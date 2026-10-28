package databack.common.worldgen.density;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

/// The fast chunk evaluator, for trees whose marker state is unobservable ([ChunkAnalysis#isPure]).
///
/// In that regime every function inside a chunk is a pure function of the block position, given two chunk-specific
/// replacements: an `interpolated` is the trilinear interpolation of its argument at the cell corners (cell-fill
/// order, spec 5.3), and a `flat_cache` is its argument at the 4x4-aligned grid point with y = 0 (spec 4.1). Every
/// other marker is transparent. Purity lets this evaluator choose its own order, skip work and reuse results:
///
/// - **Cells.** The tree above the interpolators is compiled into [CellOps], which evaluate a whole cell
///   array-at-a-time and skip branches no point of the cell needs. Results are copied straight into the output.
/// - **Corners, on demand.** An interpolator's corner value is computed the first time a cell reads that interpolator,
///   so an interpolator that only matters in some cells (the overworld's noodle caves only matter underground) is
///   only evaluated at the corners of those cells. Shared subtrees get per-position memos (see
///   [SharedSubexpressions]).
/// - **Shared columns.** A corner column whose values for an interpolator are complete at the end of a chunk is
///   published to the [ColumnCache], and later chunks take it from there instead of computing it.
///
/// The evaluated Y range is the plan's; since nothing depends on evaluation order, a clipped range gives the same
/// values as the full one.
final class PureChunkEvaluator implements ChunkEvaluator {

    private static final int CORNER = 0, CELL = 1, FLAT = 2;

    final ChunkPlan plan;
    final int cw, ch, n, cellsY, minY, lanes;
    private final int columnsPerSide, cornersY;
    @Nullable
    private final ColumnCache columnCache;

    private final DensityNode[] cornerFunctions;
    private final CellOps.Op root;

    /// Interpolation weights per in-cell offset (spec 5.3).
    final double[] fx, fy, fz;
    /// In-cell offsets of each lane. Lanes are ordered y, then z, then x (x fastest), like the output.
    final int[] laneX, laneY, laneZ;
    /// Per lane: index of its (y, x) row, and its z weight.
    private final int[] laneRow;
    private final double[] laneFz;

    // Chunk state.
    int chunkX0, chunkZ0;
    /// Bumped per chunk; stamps per-chunk cached values.
    long generation;

    // Corner columns, indexed [column][interpolator]; column = sx * columnsPerSide + sz.
    private final int[] columnX, columnZ;
    /// The values in use this chunk: either this evaluator's buffer or a complete array from the cache.
    private final double[][][] corners;
    private final double[][][] ownCorners;
    /// Per corner: the generation it was computed in (only for this evaluator's buffers).
    private final long[][][] computedIn;
    private final int[][] computedCount;
    private final boolean[][] complete;
    @Nullable
    private final double[][][] cacheEntries;

    // Cell state.
    private int cellI, cellJ, cellK;
    /// Bumped per cell; per interpolator, the cell whose corners were last made sure of.
    private long cellCount;
    private final long[] cornersReadyIn;
    int cellX0, cellY0, cellZ0;
    private final double[] cellValues;

    PureChunkEvaluator(ChunkPlan plan, @Nullable ColumnCache columnCache) {
        if (!plan.pure) throw new IllegalArgumentException("not a pure tree: " + plan.impurity);
        this.plan = plan;
        this.cw = plan.cellWidth;
        this.ch = plan.cellHeight;
        this.n = plan.cellsXZ;
        this.cellsY = plan.cellsY;
        this.minY = plan.minY;
        this.lanes = plan.cellVolume();
        this.columnsPerSide = n + 1;
        this.cornersY = cellsY + 1;
        this.columnCache = columnCache;

        fx = new double[cw];
        fz = new double[cw];
        fy = new double[ch];
        for (int i = 0; i < cw; i++) fx[i] = fz[i] = (double) i / (double) cw;
        for (int i = 0; i < ch; i++) fy[i] = (double) i / (double) ch;
        laneX = new int[lanes];
        laneY = new int[lanes];
        laneZ = new int[lanes];
        laneRow = new int[lanes];
        laneFz = new double[lanes];
        for (int dy = 0, l = 0; dy < ch; dy++) {
            for (int dz = 0; dz < cw; dz++) {
                for (int dx = 0; dx < cw; dx++, l++) {
                    laneX[l] = dx;
                    laneY[l] = dy;
                    laneZ[l] = dz;
                    laneRow[l] = dy * cw + dx;
                    laneFz[l] = fz[dz];
                }
            }
        }

        Binder binder = new Binder();
        List<MarkerNode> interps = plan.interpolators;
        int count = interps.size();
        cornerFunctions = new DensityNode[count];
        for (int j = 0; j < count; j++) cornerFunctions[j] = binder.bind(interps.get(j).argument, CORNER);
        root = binder.compile(plan.root);

        int columns = columnsPerSide * columnsPerSide;
        columnX = new int[columns];
        columnZ = new int[columns];
        corners = new double[columns][count][];
        ownCorners = new double[columns][count][cornersY];
        computedIn = new long[columns][count][cornersY];
        computedCount = new int[columns][count];
        complete = new boolean[columns][count];
        cacheEntries = columnCache != null ? new double[columns][][] : null;
        cornersReadyIn = new long[count];
        cellValues = new double[lanes];
    }

    @Override
    public void fill(int chunkX, int chunkZ, double[] out) {
        chunkX0 = chunkX << 4;
        chunkZ0 = chunkZ << 4;
        generation++;

        if (cornerFunctions.length > 0) prepareColumns();

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                for (int k = 0; k < cellsY; k++) {
                    cellI = i;
                    cellJ = j;
                    cellK = k;
                    cellX0 = chunkX0 + i * cw;
                    cellY0 = minY + k * ch;
                    cellZ0 = chunkZ0 + j * cw;
                    cellCount++;
                    root.run(cellValues, null, lanes);
                    copyCell(out, i, j, k);
                }
            }
        }

        if (columnCache != null && cornerFunctions.length > 0) publishColumns();
    }

    private void copyCell(double[] out, int i, int j, int k) {
        double[] values = cellValues;
        int w = cw;
        if (w == 4) {
            for (int dy = 0, src = 0; dy < ch; dy++) {
                int dst = ((k * ch + dy) * 16 + j * 4) * 16 + i * 4;
                for (int dz = 0; dz < 4; dz++, src += 4, dst += 16) {
                    out[dst] = values[src];
                    out[dst + 1] = values[src + 1];
                    out[dst + 2] = values[src + 2];
                    out[dst + 3] = values[src + 3];
                }
            }
            return;
        }
        for (int dy = 0, src = 0; dy < ch; dy++) {
            for (int dz = 0; dz < w; dz++, src += w) {
                int dst = ((k * ch + dy) * 16 + j * w + dz) * 16 + i * w;
                for (int dx = 0; dx < w; dx++) out[dst + dx] = values[src + dx];
            }
        }
    }

    // <editor-fold desc="Corner columns">

    private void prepareColumns() {
        for (int sx = 0, c = 0; sx < columnsPerSide; sx++) {
            for (int sz = 0; sz < columnsPerSide; sz++, c++) {
                int x = chunkX0 + sx * cw, z = chunkZ0 + sz * cw;
                columnX[c] = x;
                columnZ[c] = z;
                double[][] entry = null;
                if (columnCache != null) {
                    entry = columnCache.get(Math.floorDiv(x, cw), Math.floorDiv(z, cw));
                    cacheEntries[c] = entry;
                }
                for (int j = 0; j < cornerFunctions.length; j++) {
                    boolean cached = entry != null && entry[j] != null;
                    corners[c][j] = cached ? entry[j] : ownCorners[c][j];
                    complete[c][j] = cached;
                    computedCount[c][j] = 0;
                }
            }
        }
    }

    /// Publishes every column array that this chunk completed, merged into the cache's entry for that column.
    private void publishColumns() {
        for (int c = 0; c < corners.length; c++) {
            double[][] merged = null;
            for (int j = 0; j < cornerFunctions.length; j++) {
                if (complete[c][j] || computedCount[c][j] != cornersY) continue;
                if (merged == null) {
                    double[][] old = cacheEntries[c];
                    merged = old != null ? old.clone() : new double[cornerFunctions.length][];
                }
                merged[j] = ownCorners[c][j].clone();
            }
            if (merged != null) columnCache.put(Math.floorDiv(columnX[c], cw), Math.floorDiv(columnZ[c], cw), merged);
        }
    }

    /// Makes sure interpolator `j`'s eight corners of the current cell are computed.
    private void ensureCorners(int j) {
        if (cornersReadyIn[j] == cellCount) return;
        cornersReadyIn[j] = cellCount;
        int c = cellI * columnsPerSide + cellJ, k = cellK;
        ensureCorner(c, j, k);
        ensureCorner(c, j, k + 1);
        ensureCorner(c + columnsPerSide, j, k);
        ensureCorner(c + columnsPerSide, j, k + 1);
        ensureCorner(c + 1, j, k);
        ensureCorner(c + 1, j, k + 1);
        ensureCorner(c + columnsPerSide + 1, j, k);
        ensureCorner(c + columnsPerSide + 1, j, k + 1);
    }

    private void ensureCorner(int c, int j, int k) {
        if (complete[c][j]) return;
        long[] stamps = computedIn[c][j];
        if (stamps[k] == generation) return;
        corners[c][j][k] = cornerFunctions[j].sample(columnX[c], minY + k * ch, columnZ[c]);
        stamps[k] = generation;
        computedCount[c][j]++;
    }

    // </editor-fold>

    // <editor-fold desc="Interpolation">

    /// Scratch space [#interpolate] needs.
    int interpolationScratch() {
        return 4 * cw + 2 * ch * cw;
    }

    /// Interpolator `j` over every lane of the current cell, in cell-fill order (x, then y, then z). The partial lerps
    /// are shared between lanes, but each lane's value goes through exactly the operations of
    /// [DensityMath#trilinearXYZ]: `lerp(t, a, b) = a + t * (b - a)`, with `b - a` computed once per pair.
    void interpolate(int j, double[] out, double[] scratch) {
        ensureCorners(j);
        int base = cellI * columnsPerSide + cellJ;
        double[] a = corners[base][j], b = corners[base + columnsPerSide][j];
        double[] c = corners[base + 1][j], d = corners[base + columnsPerSide + 1][j];
        int k = cellK;
        double c000 = a[k], c010 = a[k + 1], c100 = b[k], c110 = b[k + 1];
        double c001 = c[k], c011 = c[k + 1], c101 = d[k], c111 = d[k + 1];
        double d00 = c100 - c000, d10 = c110 - c010, d01 = c101 - c001, d11 = c111 - c011;

        // scratch: per x, the bottom (y0) value and the top - bottom difference, for z0 and z1; then per (y, x) row,
        // the z0 value and the z1 - z0 difference.
        int w = cw, e = 4 * w, rows = ch * w;
        for (int dx = 0; dx < w; dx++) {
            double t = fx[dx];
            double e00 = c000 + t * d00, e10 = c010 + t * d10, e01 = c001 + t * d01, e11 = c011 + t * d11;
            scratch[dx] = e00;
            scratch[w + dx] = e10 - e00;
            scratch[2 * w + dx] = e01;
            scratch[3 * w + dx] = e11 - e01;
        }
        for (int dy = 0, r = 0; dy < ch; dy++) {
            double t = fy[dy];
            for (int dx = 0; dx < w; dx++, r++) {
                double a0 = scratch[dx] + t * scratch[w + dx];
                double a1 = scratch[2 * w + dx] + t * scratch[3 * w + dx];
                scratch[e + r] = a0;
                scratch[e + rows + r] = a1 - a0;
            }
        }
        if (w == 4) {
            // Common case, unrolled: one row of four x values per (y, z).
            for (int dy = 0, l = 0; dy < ch; dy++) {
                int r = e + dy * 4;
                double a0 = scratch[r], a1 = scratch[r + 1], a2 = scratch[r + 2], a3 = scratch[r + 3];
                double d0 = scratch[r + rows], d1 = scratch[r + rows + 1], d2 = scratch[r + rows + 2], d3 = scratch[r + rows + 3];
                for (int dz = 0; dz < 4; dz++, l += 4) {
                    double t = fz[dz];
                    out[l] = a0 + t * d0;
                    out[l + 1] = a1 + t * d1;
                    out[l + 2] = a2 + t * d2;
                    out[l + 3] = a3 + t * d3;
                }
            }
            return;
        }
        int[] row = laneRow;
        double[] tz = laneFz;
        for (int l = 0; l < lanes; l++) {
            int r = row[l];
            out[l] = scratch[e + r] + tz[l] * scratch[e + rows + r];
        }
    }

    /// Bounds on interpolator `j`'s values in the current cell, into `out[0..1]`; false if there are none.
    ///
    /// Each lerp with a weight in `[0, 1)` lands between its endpoints up to a few units of rounding, so every lane
    /// lies within the range of the cell's eight corners, widened by a margin far larger than that rounding. Infinite,
    /// NaN or huge corners give no bounds (an interpolation between infinities is NaN).
    boolean cornerBounds(int j, double[] out) {
        ensureCorners(j);
        int base = cellI * columnsPerSide + cellJ;
        double[] a = corners[base][j], b = corners[base + columnsPerSide][j];
        double[] c = corners[base + 1][j], d = corners[base + columnsPerSide + 1][j];
        int k = cellK;
        double lo = Math.min(Math.min(Math.min(a[k], a[k + 1]), Math.min(b[k], b[k + 1])),
            Math.min(Math.min(c[k], c[k + 1]), Math.min(d[k], d[k + 1])));
        double hi = Math.max(Math.max(Math.max(a[k], a[k + 1]), Math.max(b[k], b[k + 1])),
            Math.max(Math.max(c[k], c[k + 1]), Math.max(d[k], d[k + 1])));
        double magnitude = Math.max(Math.abs(lo), Math.abs(hi));
        // NaN fails this too. Near the top of the range, differences between corners can overflow.
        if (!(magnitude < 1e300)) return false;
        // Relative to the corners, plus an absolute floor for subnormal ones (whose rounding is absolute).
        double margin = 1e-12 * magnitude + Double.MIN_NORMAL;
        out[0] = lo - margin;
        out[1] = hi + margin;
        return true;
    }

    /// Interpolator `j` at one position inside the current cell; same result as the matching lane of
    /// [#interpolate].
    double interpolateAt(int j, int x, int y, int z) {
        ensureCorners(j);
        int base = cellI * columnsPerSide + cellJ;
        double[] a = corners[base][j], b = corners[base + columnsPerSide][j];
        double[] c = corners[base + 1][j], d = corners[base + columnsPerSide + 1][j];
        int k = cellK;
        return DensityMath.trilinearXYZ(
            (double) (x - cellX0) / (double) cw, (double) (y - cellY0) / (double) ch, (double) (z - cellZ0) / (double) cw,
            a[k], b[k], a[k + 1], b[k + 1], c[k], d[k], c[k + 1], d[k + 1]);
    }

    // </editor-fold>

    // <editor-fold desc="Binding">

    /// Turns the immutable tree into this evaluator's structures. Nodes are bound per context, since the same node
    /// means different things at cell level (where `interpolated` reads the current cell), at corners (where shared
    /// subtrees get a memo) and inside `flat_cache` arguments (only ever evaluated at grid points with y = 0, where
    /// markers are transparent).
    private final class Binder {

        private final List<Map<DensityNode, DensityNode>> bound = new ArrayList<>();
        private final Map<DensityNode, FlatGrid> flatGrids = new IdentityHashMap<>();
        private final Map<DensityNode, CellOps.Op> ops = new IdentityHashMap<>();
        private final Map<DensityNode, Integer> interpIndex = new IdentityHashMap<>();
        private final Map<DensityNode, Boolean> yIndependent = new HashMap<>();
        private final Set<DensityNode> memoTargets;

        Binder() {
            for (int i = 0; i < 3; i++) bound.add(new IdentityHashMap<>());
            List<DensityNode> args = new ArrayList<>();
            for (int j = 0; j < plan.interpolators.size(); j++) {
                interpIndex.put(plan.interpolators.get(j), j);
                args.add(plan.interpolators.get(j).argument);
            }
            memoTargets = SharedSubexpressions.memoTargets(args);
        }

        DensityNode bind(DensityNode node, int ctx) {
            Map<DensityNode, DensityNode> map = bound.get(ctx);
            DensityNode result = map.get(node);
            if (result == null) {
                result = bindNew(node, ctx);
                // Parents read their inputs' estimates (min/max skips), so a replacement keeps the original's.
                if (!DensityMath.same(result.lo, node.lo) || !DensityMath.same(result.hi, node.hi)) {
                    result = result.withBounds(node.lo, node.hi);
                }
                if (ctx == CORNER && memoTargets.contains(node)) result = new PointMemo(result, node);
                map.put(node, result);
            }
            return result;
        }

        private DensityNode bindNew(DensityNode node, int ctx) {
            if (node instanceof MarkerNode m) {
                switch (m.kind) {
                    case INTERPOLATED:
                        if (ctx == CELL) return new CellInterp(PureChunkEvaluator.this, interpIndex.get(m), m);
                        if (ctx == FLAT) return bind(m.argument, ctx);
                        throw new IllegalStateException("nested interpolated in a pure tree");
                    case CACHE_ALL_IN_CELL:
                        if (ctx == CORNER) throw new IllegalStateException("cache_all_in_cell at corners in a pure tree");
                        return bind(m.argument, ctx);
                    case FLAT_CACHE:
                        return flatGrid(m);
                    case CACHE_2D:
                        if (ctx != FLAT && ChunkAnalysis.isYIndependent(m.argument, yIndependent)) {
                            return new ColumnMemo(bind(m.argument, ctx), m);
                        }
                        return bind(m.argument, ctx);
                    case CACHE_ONCE:
                        return bind(m.argument, ctx);
                    default:
                        throw new AssertionError(m.kind);
                }
            }

            DensityNode[] in = node.inputs();
            if (in.length == 0) return node;
            DensityNode[] next = new DensityNode[in.length];
            boolean changed = false;
            for (int i = 0; i < in.length; i++) {
                // find_top_surface's density is always evaluated at single points (spec 3.8).
                next[i] = node instanceof FindTopSurfaceNode && i == 0 ? in[i] : bind(in[i], ctx);
                changed |= next[i] != in[i];
            }
            return changed ? node.withInputs(next) : node;
        }

        private FlatGrid flatGrid(MarkerNode m) {
            FlatGrid grid = flatGrids.get(m);
            if (grid == null) {
                grid = new FlatGrid(PureChunkEvaluator.this, bind(m.argument, FLAT), m);
                flatGrids.put(m, grid);
            }
            return grid;
        }

        CellOps.Op compile(DensityNode node) {
            CellOps.Op op = ops.get(node);
            if (op == null) {
                op = compileNew(node);
                ops.put(node, op);
            }
            return op;
        }

        private CellOps.Op compileNew(DensityNode node) {
            PureChunkEvaluator ev = PureChunkEvaluator.this;
            if (node instanceof ConstantNode c) return new CellOps.Fixed(ev, c.value);
            if (node instanceof PlaceholderNode p) return new CellOps.Fixed(ev, p.value);
            if (node instanceof GradientNode g) return new CellOps.Gradient(ev, g);
            if (node instanceof BlendDensityNode b) return compile(b.input);
            if (node instanceof MarkerNode m) {
                switch (m.kind) {
                    case INTERPOLATED: return new CellOps.Interpolated(ev, interpIndex.get(m));
                    case FLAT_CACHE: return new CellOps.PerPoint(ev, flatGrid(m));
                    default: return compile(m.argument);
                }
            }
            if (node instanceof TransformNode t) return new CellOps.Transform(ev, t.kind, compile(t.input));
            if (node instanceof ClampNode c) return new CellOps.Clamp(ev, c.min, c.max, compile(c.input));
            if (node instanceof AffineNode a) return new CellOps.Affine(ev, a.multiply, a.c, compile(a.input));
            if (node instanceof PairNode p) {
                CellOps.Op a = compile(p.a), b = compile(p.b);
                switch (p.op) {
                    case ADD: return new CellOps.Add(ev, a, b);
                    case MUL: return new CellOps.Gated(ev, CellOps.Gated.MUL, a, b, 0.0);
                    case MIN: return new CellOps.Gated(ev, CellOps.Gated.MIN, a, b, p.b.lo);
                    default: return new CellOps.Gated(ev, CellOps.Gated.MAX, a, b, p.b.hi);
                }
            }
            if (node instanceof RangeChoiceNode r) {
                return new CellOps.Range(
                    ev, compile(r.input), r.minInclusive, r.maxExclusive, compile(r.whenIn), compile(r.whenOut));
            }
            return new CellOps.PerPoint(ev, bind(node, CELL));
        }
    }

    // </editor-fold>

    // <editor-fold desc="Bound nodes">

    /// Base for the evaluator's own nodes: opaque (no inputs), identity equality, the estimate of the node they stand
    /// for.
    private abstract static class BoundNode extends DensityNode {

        BoundNode(DensityNode original) {
            super(original.lo, original.hi, System.identityHashCode(original));
        }

        BoundNode(double lo, double hi) {
            super(lo, hi, 0);
        }

        @Override
        protected boolean sameParams(DensityNode other) {
            return other == this;
        }

        @Override
        public DensityNode withBounds(double lo, double hi) {
            return new Rebound(this, lo, hi);
        }
    }

    /// Another node's value under a different estimate.
    private static final class Rebound extends BoundNode {

        private final DensityNode inner;

        Rebound(DensityNode inner, double lo, double hi) {
            super(lo, hi);
            this.inner = inner;
        }

        @Override
        public double sample(int x, int y, int z) {
            return inner.sample(x, y, z);
        }
    }

    /// `flat_cache`: the argument at the grid point `(4 * floor(x / 4), 0, 4 * floor(z / 4))`, computed once per
    /// chunk for the chunk's 5x5 grid (spec 4.1). Reads outside the grid evaluate the argument at the position.
    private static final class FlatGrid extends BoundNode {

        private static final int SIZE = 5;

        private final PureChunkEvaluator ev;
        private final DensityNode argument;
        private final MarkerNode original;
        private final double[] values = new double[SIZE * SIZE];
        private final long[] stamps = new long[SIZE * SIZE];

        FlatGrid(PureChunkEvaluator ev, DensityNode argument, MarkerNode original) {
            super(original);
            this.ev = ev;
            this.argument = argument;
            this.original = original;
        }

        @Override
        public double sample(int x, int y, int z) {
            int i = (x >> 2) - (ev.chunkX0 >> 2), j = (z >> 2) - (ev.chunkZ0 >> 2);
            if (i < 0 || i >= SIZE || j < 0 || j >= SIZE) return original.argument.sample(x, y, z);
            int idx = i * SIZE + j;
            if (stamps[idx] != ev.generation) {
                values[idx] = argument.sample(x & ~3, 0, z & ~3);
                stamps[idx] = ev.generation;
            }
            return values[idx];
        }
    }

    /// `cache_2d` of a function that doesn't depend on y: a one-entry memo keyed on `(x, z)`. Corners of one column
    /// share it.
    private static final class ColumnMemo extends BoundNode {

        private final DensityNode argument;
        private boolean valid;
        private int lastX, lastZ;
        private double value;

        ColumnMemo(DensityNode argument, DensityNode original) {
            super(original);
            this.argument = argument;
        }

        @Override
        public double sample(int x, int y, int z) {
            if (!valid || x != lastX || z != lastZ) {
                value = argument.sample(x, y, z);
                lastX = x;
                lastZ = z;
                valid = true;
            }
            return value;
        }
    }

    /// One-entry memo keyed on the block position, for subtrees reached more than once per corner.
    private static final class PointMemo extends BoundNode {

        private final DensityNode argument;
        private boolean valid;
        private int lastX, lastY, lastZ;
        private double value;

        PointMemo(DensityNode argument, DensityNode original) {
            super(original);
            this.argument = argument;
        }

        @Override
        public double sample(int x, int y, int z) {
            if (!valid || x != lastX || y != lastY || z != lastZ) {
                value = argument.sample(x, y, z);
                lastX = x;
                lastY = y;
                lastZ = z;
                valid = true;
            }
            return value;
        }
    }

    /// An `interpolated` read at one position of the current cell (for cell-level functions evaluated per point).
    private static final class CellInterp extends BoundNode {

        private final PureChunkEvaluator ev;
        private final int index;

        CellInterp(PureChunkEvaluator ev, int index, DensityNode original) {
            super(original);
            this.ev = ev;
            this.index = index;
        }

        @Override
        public double sample(int x, int y, int z) {
            return ev.interpolateAt(index, x, y, z);
        }
    }

    // </editor-fold>
}
