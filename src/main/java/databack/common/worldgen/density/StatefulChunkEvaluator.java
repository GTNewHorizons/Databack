package databack.common.worldgen.density;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/// The exact chunk evaluator, for trees whose marker state is observable (nested interpolators or cell caches,
/// point-read `cache_once` at cell level, point-read `cache_2d` of 3D functions; see [ChunkAnalysis]).
///
/// It replays chunk evaluation step by step as spec section 5 lays it out (corner columns slice by slice, cells top
/// to bottom, then the blocks of each cell), and gives every marker the state of spec section 4. Each function is
/// bound to a [Live] node that supports the two kinds of read: a **point read** at the current point, and a **batch
/// read** over the current batch of points (a corner column or a cell), forwarding to its inputs per spec 5.4.
///
/// Subtrees without chunk markers have no state, so they are evaluated with [DensityNode#sample].
///
/// All state is reset at the start of every chunk, and instants come from a counter that never repeats, so nothing
/// carries over from one chunk to the next.
final class StatefulChunkEvaluator implements ChunkEvaluator {

    private static final int CORNERS = 0, CELL_FILL = 1, BLOCKS = 2;

    private final int cw, ch, n, cellsY, minY, cornersY, volume;

    // Phase and instants (spec 4.3).
    private int phase;
    private long counter;
    private long pointInstant, batchInstant, cornerBase;

    // The current point: block position, in-cell offset, and index within the current batch.
    private int px, py, pz, ox, oy, oz, index;

    // The current batch.
    private int batchSize;
    private final int[] bx, by, bz, box, boy, boz;

    private int chunkX0, chunkZ0;

    private final List<Interpolator> interpolators = new ArrayList<>();
    /// User cell caches in order, then the outermost one.
    private final List<CellCache> cellCaches = new ArrayList<>();
    private final CellCache outermost;
    private final List<Live> resettable = new ArrayList<>();
    private final double[] columnScratch;

    StatefulChunkEvaluator(ChunkPlan plan) {
        this.cw = plan.cellWidth;
        this.ch = plan.cellHeight;
        this.n = plan.cellsXZ;
        this.cellsY = plan.cellsY;
        this.minY = plan.minY;
        this.cornersY = cellsY + 1;
        this.volume = plan.cellVolume();

        int maxBatch = Math.max(volume, cornersY);
        bx = new int[maxBatch];
        by = new int[maxBatch];
        bz = new int[maxBatch];
        box = new int[maxBatch];
        boy = new int[maxBatch];
        boz = new int[maxBatch];
        columnScratch = new double[maxBatch];

        Binder binder = new Binder(plan, maxBatch);
        for (MarkerNode m : plan.interpolators) interpolators.add((Interpolator) binder.bind(m));
        for (MarkerNode m : plan.cellCaches) cellCaches.add((CellCache) binder.bind(m));
        // The density read for terrain: cache_all_in_cell(add(final_density, beardifier)) (spec 4.2).
        outermost = new CellCache(binder.bind(Density.add(plan.root, PlaceholderNode.BEARDIFIER)), maxBatch);
        resettable.add(outermost);
        cellCaches.add(outermost);
        for (Interpolator i : interpolators) i.argument = binder.bind(i.marker.argument);
    }

    // <editor-fold desc="Chunk order (spec 5.2)">

    @Override
    public void fill(int chunkX, int chunkZ, double[] out) {
        chunkX0 = chunkX << 4;
        chunkZ0 = chunkZ << 4;
        for (Live l : resettable) l.reset();

        fillSlice(0);
        for (int i = 0; i < n; i++) {
            fillSlice(i + 1);
            for (int cz = 0; cz < n; cz++) {
                for (int cy = cellsY - 1; cy >= 0; cy--) {
                    for (Interpolator interp : interpolators) interp.select(i, cy, cz);
                    fillCell(i, cy, cz);
                    blocks(i, cy, cz, out);
                }
            }
        }
    }

    private void fillSlice(int sx) {
        phase = CORNERS;
        int x = chunkX0 + sx * cw;
        long lastBase = 0;
        for (int sz = 0; sz <= n; sz++) {
            batchInstant = ++counter;
            cornerBase = counter + 1;
            counter += cornersY;
            lastBase = cornerBase;

            int z = chunkZ0 + sz * cw;
            batchSize = cornersY;
            for (int k = 0; k < cornersY; k++) {
                bx[k] = x;
                by[k] = minY + k * ch;
                bz[k] = z;
                box[k] = boy[k] = boz[k] = 0;
            }
            for (Interpolator interp : interpolators) {
                interp.argument.batch(columnScratch);
                interp.storeColumn(sx, sz, columnScratch);
            }
        }
        // The first cell fill after this inherits the last corner's point instant.
        pointInstant = lastBase + cornersY - 1;
    }

    private void fillCell(int i, int cy, int cz) {
        phase = CELL_FILL;
        batchInstant = ++counter;
        batchSize = volume;
        int b = 0;
        for (int dy = ch - 1; dy >= 0; dy--) {
            for (int dx = 0; dx < cw; dx++) {
                for (int dz = 0; dz < cw; dz++, b++) {
                    bx[b] = chunkX0 + i * cw + dx;
                    by[b] = minY + cy * ch + dy;
                    bz[b] = chunkZ0 + cz * cw + dz;
                    box[b] = dx;
                    boy[b] = dy;
                    boz[b] = dz;
                }
            }
        }
        for (CellCache cache : cellCaches) cache.fill();
    }

    private void blocks(int i, int cy, int cz, double[] out) {
        phase = BLOCKS;
        for (int b = 0; b < volume; b++) {
            pointInstant = ++counter;
            enterPoint(b);
            for (Interpolator interp : interpolators) interp.updateBlock();
            double v = outermost.point();
            int lx = i * cw + ox, lz = cz * cw + oz;
            out[((py - minY) * 16 + lz) * 16 + lx] = v;
        }
    }

    /// Makes point `i` of the current batch the current point. At corners, each corner is its own point instant.
    private void enterPoint(int i) {
        px = bx[i];
        py = by[i];
        pz = bz[i];
        ox = box[i];
        oy = boy[i];
        oz = boz[i];
        index = i;
        if (phase == CORNERS) pointInstant = cornerBase + i;
    }

    // </editor-fold>

    // <editor-fold desc="Live nodes">

    /// A function bound to this evaluator.
    private abstract class Live {

        /// The value at the current point.
        abstract double point();

        /// Values at every point of the current batch. By default, one point read per point.
        void batch(double[] out) {
            for (int i = 0; i < batchSize; i++) {
                enterPoint(i);
                out[i] = point();
            }
        }

        /// Back to the state at the start of a chunk.
        void reset() {}
    }

    /// A subtree without chunk markers: the same at every read.
    private final class Stateless extends Live {

        private final DensityNode node;

        Stateless(DensityNode node) {
            this.node = node;
        }

        @Override
        double point() {
            return node.sample(px, py, pz);
        }

        @Override
        void batch(double[] out) {
            for (int i = 0; i < batchSize; i++) out[i] = node.sample(bx[i], by[i], bz[i]);
        }
    }

    private final class Interpolator extends Live {

        final MarkerNode marker;
        Live argument;
        private final double[] corners;
        private double c000, c100, c010, c110, c001, c101, c011, c111;
        private double perBlock;

        Interpolator(MarkerNode marker) {
            this.marker = marker;
            this.corners = new double[(n + 1) * (n + 1) * cornersY];
        }

        void storeColumn(int sx, int sz, double[] values) {
            System.arraycopy(values, 0, corners, (sx * (n + 1) + sz) * cornersY, cornersY);
        }

        void select(int i, int cy, int cz) {
            int x0z0 = (i * (n + 1) + cz) * cornersY + cy, x1z0 = ((i + 1) * (n + 1) + cz) * cornersY + cy;
            int x0z1 = (i * (n + 1) + cz + 1) * cornersY + cy, x1z1 = ((i + 1) * (n + 1) + cz + 1) * cornersY + cy;
            c000 = corners[x0z0];
            c010 = corners[x0z0 + 1];
            c100 = corners[x1z0];
            c110 = corners[x1z0 + 1];
            c001 = corners[x0z1];
            c011 = corners[x0z1 + 1];
            c101 = corners[x1z1];
            c111 = corners[x1z1 + 1];
        }

        void updateBlock() {
            perBlock = DensityMath.trilinearYXZ(
                (double) ox / cw, (double) oy / ch, (double) oz / cw, c000, c100, c010, c110, c001, c101, c011, c111);
        }

        private double inCell(int dx, int dy, int dz) {
            return DensityMath.trilinearXYZ(
                (double) dx / cw, (double) dy / ch, (double) dz / cw, c000, c100, c010, c110, c001, c101, c011, c111);
        }

        @Override
        double point() {
            return phase == CELL_FILL ? inCell(ox, oy, oz) : perBlock;
        }

        @Override
        void batch(double[] out) {
            if (phase == CELL_FILL) {
                for (int i = 0; i < batchSize; i++) out[i] = inCell(box[i], boy[i], boz[i]);
            } else {
                argument.batch(out);
            }
        }

        @Override
        void reset() {
            perBlock = 0.0;
        }
    }

    private final class CellCache extends Live {

        private final Live argument;
        private final double[] stored;

        CellCache(Live argument, int maxBatch) {
            this.argument = argument;
            this.stored = new double[maxBatch];
        }

        /// Evaluates the argument over the current cell (the current batch).
        void fill() {
            argument.batch(stored);
        }

        @Override
        double point() {
            // Index of the in-cell offset in cell-fill order; at corners the offset is (0, 0, 0).
            int dx = phase == CORNERS ? 0 : ox, dy = phase == CORNERS ? 0 : oy, dz = phase == CORNERS ? 0 : oz;
            return stored[((ch - 1 - dy) * cw + dx) * cw + dz];
        }

        @Override
        void reset() {
            java.util.Arrays.fill(stored, 0.0);
        }
    }

    private final class FlatCache extends Live {

        private static final int SIZE = 5;
        private final DensityNode argument;
        private final double[] values = new double[SIZE * SIZE];
        private final boolean[] known = new boolean[SIZE * SIZE];

        FlatCache(DensityNode argument) {
            this.argument = argument;
        }

        @Override
        double point() {
            int i = (px >> 2) - (chunkX0 >> 2), j = (pz >> 2) - (chunkZ0 >> 2);
            if (i < 0 || i >= SIZE || j < 0 || j >= SIZE) return argument.sample(px, py, pz);
            int idx = i * SIZE + j;
            if (!known[idx]) {
                values[idx] = argument.sample(px & ~3, 0, pz & ~3);
                known[idx] = true;
            }
            return values[idx];
        }

        @Override
        void reset() {
            java.util.Arrays.fill(known, false);
        }
    }

    private final class Cache2D extends Live {

        private final Live argument;
        private boolean has;
        private int keyX, keyZ;
        private double value;

        Cache2D(Live argument) {
            this.argument = argument;
        }

        @Override
        double point() {
            if (has && keyX == px && keyZ == pz) return value;
            double v = argument.point();
            has = true;
            keyX = px;
            keyZ = pz;
            value = v;
            return v;
        }

        @Override
        void batch(double[] out) {
            argument.batch(out);
        }

        @Override
        void reset() {
            has = false;
        }
    }

    private final class CacheOnce extends Live {

        private final Live argument;
        private final double[] batchValues;
        private long batchStamp = -1, pointStamp = -1;
        private double pointValue;

        CacheOnce(Live argument, int maxBatch) {
            this.argument = argument;
            this.batchValues = new double[maxBatch];
        }

        @Override
        double point() {
            if (batchStamp == batchInstant) return batchValues[index];
            if (pointStamp == pointInstant) return pointValue;
            double v = argument.point();
            pointValue = v;
            pointStamp = pointInstant;
            return v;
        }

        @Override
        void batch(double[] out) {
            if (batchStamp != batchInstant) {
                argument.batch(batchValues);
                batchStamp = batchInstant;
            }
            System.arraycopy(batchValues, 0, out, 0, batchSize);
        }
    }

    private final class Pair extends Live {

        private final PairNode node;
        private final Live a, b;
        private final double[] tmp;

        Pair(PairNode node, Live a, Live b, int maxBatch) {
            this.node = node;
            this.a = a;
            this.b = b;
            this.tmp = node.op == PairNode.Op.ADD ? new double[maxBatch] : null;
        }

        @Override
        double point() {
            double va = a.point();
            return node.needsB(va) ? node.combine(va, b.point()) : node.skipped(va);
        }

        @Override
        void batch(double[] out) {
            a.batch(out);
            if (node.op == PairNode.Op.ADD) {
                b.batch(tmp);
                for (int i = 0; i < batchSize; i++) out[i] = out[i] + tmp[i];
                return;
            }
            for (int i = 0; i < batchSize; i++) {
                double va = out[i];
                if (node.needsB(va)) {
                    enterPoint(i);
                    out[i] = node.combine(va, b.point());
                } else {
                    out[i] = node.skipped(va);
                }
            }
        }
    }

    /// Functions of one input that read it the same way they are read: constant `add`/`mul`, the transforms, `clamp`.
    private final class Mapped extends Live {

        private final DensityNode node;
        private final Live input;

        Mapped(DensityNode node, Live input) {
            this.node = node;
            this.input = input;
        }

        private double apply(double v) {
            if (node instanceof AffineNode a) return a.apply(v);
            if (node instanceof TransformNode t) return t.kind.apply(v);
            ClampNode c = (ClampNode) node;
            return DensityMath.clamp(v, c.min, c.max);
        }

        @Override
        double point() {
            return apply(input.point());
        }

        @Override
        void batch(double[] out) {
            input.batch(out);
            for (int i = 0; i < batchSize; i++) out[i] = apply(out[i]);
        }
    }

    private final class Range extends Live {

        private final RangeChoiceNode node;
        private final Live input, whenIn, whenOut;
        private final double[] tmp;

        Range(RangeChoiceNode node, Live input, Live whenIn, Live whenOut, int maxBatch) {
            this.node = node;
            this.input = input;
            this.whenIn = whenIn;
            this.whenOut = whenOut;
            this.tmp = new double[maxBatch];
        }

        @Override
        double point() {
            return node.inRange(input.point()) ? whenIn.point() : whenOut.point();
        }

        @Override
        void batch(double[] out) {
            input.batch(tmp);
            for (int i = 0; i < batchSize; i++) {
                enterPoint(i);
                out[i] = node.inRange(tmp[i]) ? whenIn.point() : whenOut.point();
            }
        }
    }

    private final class WeirdScaled extends Live {

        private final WeirdScaledNode node;
        private final Live input;

        WeirdScaled(WeirdScaledNode node, Live input) {
            this.node = node;
            this.input = input;
        }

        @Override
        double point() {
            return node.at(px, py, pz, input.point());
        }

        @Override
        void batch(double[] out) {
            input.batch(out);
            for (int i = 0; i < batchSize; i++) out[i] = node.at(bx[i], by[i], bz[i], out[i]);
        }
    }

    private final class ShiftedNoise extends Live {

        private final ShiftedNoiseNode node;
        private final Live shiftX, shiftY, shiftZ;

        ShiftedNoise(ShiftedNoiseNode node, Live shiftX, Live shiftY, Live shiftZ) {
            this.node = node;
            this.shiftX = shiftX;
            this.shiftY = shiftY;
            this.shiftZ = shiftZ;
        }

        @Override
        double point() {
            double sx = shiftX.point();
            double sy = shiftY.point();
            double sz = shiftZ.point();
            return node.at(px, py, pz, sx, sy, sz);
        }
    }

    private final class FindTopSurface extends Live {

        private final FindTopSurfaceNode node;
        private final Live upperBound;

        FindTopSurface(FindTopSurfaceNode node, Live upperBound) {
            this.node = node;
            this.upperBound = upperBound;
        }

        @Override
        double point() {
            return node.scan(px, pz, upperBound.point());
        }
    }

    private final class SplineEval extends Live {

        private final Spline spline;
        private final Map<Spline, Live> coordinates = new IdentityHashMap<>();

        SplineEval(SplineNode node, Live[] coordinates) {
            this.spline = node.spline;
            mapCoordinates(spline, coordinates, new int[1]);
        }

        private void mapCoordinates(Spline s, Live[] source, int[] next) {
            if (!(s instanceof Spline.Curve c)) return;
            coordinates.put(c, source[next[0]++]);
            for (int i = 0; i < c.size(); i++) mapCoordinates(c.value(i), source, next);
        }

        private float eval(Spline s) {
            if (!(s instanceof Spline.Curve c)) return ((Spline.Fixed) s).value;
            float t = (float) coordinates.get(c).point();
            int i = c.segment(t);
            if (i < 0) return c.extend(0, t, eval(c.value(0)));
            if (i == c.last()) return c.extend(i, t, eval(c.value(i)));
            float y1 = eval(c.value(i));
            float y2 = eval(c.value(i + 1));
            return c.cubic(i, t, y1, y2);
        }

        @Override
        double point() {
            return eval(spline);
        }
    }

    // </editor-fold>

    // <editor-fold desc="Binding">

    private final class Binder {

        private final ChunkPlan plan;
        private final int maxBatch;
        private final Map<DensityNode, Live> bound = new IdentityHashMap<>();
        private final Map<DensityNode, Boolean> hasMarkers = new IdentityHashMap<>();

        Binder(ChunkPlan plan, int maxBatch) {
            this.plan = plan;
            this.maxBatch = maxBatch;
        }

        Live bind(DensityNode node) {
            Live live = bound.get(node);
            if (live == null) {
                live = bindNew(node);
                bound.put(node, live);
                resettable.add(live);
            }
            return live;
        }

        /// Whether `node` contains a marker that has chunk state (outside single-point contexts).
        private boolean hasMarkers(DensityNode node) {
            Boolean known = hasMarkers.get(node);
            if (known != null) return known;
            boolean result = node instanceof MarkerNode;
            if (!result) {
                DensityNode[] in = node instanceof FindTopSurfaceNode f ? new DensityNode[] { f.upperBound } : node.inputs();
                for (DensityNode child : in) result |= hasMarkers(child);
            }
            hasMarkers.put(node, result);
            return result;
        }

        private Live bindNew(DensityNode node) {
            if (!hasMarkers(node)) return new Stateless(node);

            if (node instanceof MarkerNode m) {
                switch (m.kind) {
                    case INTERPOLATED: return new Interpolator(m);
                    case CACHE_ALL_IN_CELL: return new CellCache(bind(m.argument), maxBatch);
                    case FLAT_CACHE: return new FlatCache(m.argument);
                    case CACHE_2D: return new Cache2D(bind(m.argument));
                    case CACHE_ONCE: return new CacheOnce(bind(m.argument), maxBatch);
                    default: throw new AssertionError(m.kind);
                }
            }
            if (node instanceof PairNode p) return new Pair(p, bind(p.a), bind(p.b), maxBatch);
            if (node instanceof AffineNode a) return new Mapped(a, bind(a.input));
            if (node instanceof TransformNode t) return new Mapped(t, bind(t.input));
            if (node instanceof ClampNode c) return new Mapped(c, bind(c.input));
            if (node instanceof BlendDensityNode b) return bind(b.input);
            if (node instanceof RangeChoiceNode r) {
                return new Range(r, bind(r.input), bind(r.whenIn), bind(r.whenOut), maxBatch);
            }
            if (node instanceof WeirdScaledNode w) return new WeirdScaled(w, bind(w.input));
            if (node instanceof ShiftedNoiseNode s) {
                return new ShiftedNoise(s, bind(s.shiftX), bind(s.shiftY), bind(s.shiftZ));
            }
            if (node instanceof FindTopSurfaceNode f) return new FindTopSurface(f, bind(f.upperBound));
            if (node instanceof SplineNode s) {
                DensityNode[] coords = s.inputs();
                Live[] live = new Live[coords.length];
                for (int i = 0; i < coords.length; i++) live[i] = bind(coords[i]);
                return new SplineEval(s, live);
            }
            throw new IllegalStateException("No chunk evaluation for " + node.getClass().getSimpleName());
        }
    }

    // </editor-fold>
}
