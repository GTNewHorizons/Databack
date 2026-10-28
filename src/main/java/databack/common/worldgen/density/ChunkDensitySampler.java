package databack.common.worldgen.density;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

/// Thread-safe entry point for terrain density: evaluates a dimension's `final_density` for whole chunk columns.
///
/// The tree is analysed once ([ChunkPlan]). Pure trees, which includes every vanilla dimension, get the fast
/// [PureChunkEvaluator]; trees whose marker state is observable get the exact [StatefulChunkEvaluator]. Each thread
/// keeps its own evaluator; threads only share the [ColumnCache].
public final class ChunkDensitySampler {

    private static final Logger LOGGER = LogManager.getLogger("Databack/ChunkDensity");

    /// The requested range. For pure trees this is the only plan.
    private final ChunkPlan plan;
    /// Stateful trees only: the dimension's range, where values are defined by evaluation order; null if the tree is
    /// pure or the requested range already is the dimension's.
    @Nullable
    private final ChunkPlan dimensionPlan;
    private final int minY, height;
    @Nullable
    private final ColumnCache columnCache;
    private final ThreadLocal<ChunkEvaluator> evaluators;
    @Nullable
    private final ThreadLocal<ChunkEvaluator> dimensionEvaluators;
    @Nullable
    private final ThreadLocal<double[]> dimensionBuffers;

    /// @param cellWidth       `size_horizontal * 4`
    /// @param cellHeight      `size_vertical * 4`
    /// @param minY            lowest block to generate; rounded down to a cell boundary
    /// @param maxY            one above the highest block to generate; rounded up to a cell boundary
    /// @param columnCacheSize corner columns to keep for neighbouring chunks, or 0 to disable sharing
    public ChunkDensitySampler(
        DensityNode finalDensity, int cellWidth, int cellHeight, int minY, int maxY, int columnCacheSize
    ) {
        this(finalDensity, cellWidth, cellHeight, minY, maxY, minY, maxY, columnCacheSize, false);
    }

    /// The generated range may be any part of the world, inside the dimension's noise range or not (Cubic Chunks).
    ///
    /// For pure trees ([ChunkAnalysis#isPure]) a block's value doesn't depend on which other blocks are evaluated, so
    /// only the requested range is. In the stateful regime a block's value can depend on cells elsewhere in the
    /// column, and is only defined (by 26.1) for the dimension's own range: blocks inside it come from evaluating
    /// the whole dimension range, blocks outside it from evaluating the requested range on its own.
    ///
    /// @param dimensionMinY the dimension's `min_y`
    /// @param dimensionMaxY `min_y + height`
    /// @param forceStateful use the exact evaluator even for a pure tree (for testing and benchmarking)
    public ChunkDensitySampler(
        DensityNode finalDensity, int cellWidth, int cellHeight, int minY, int maxY, int dimensionMinY,
        int dimensionMaxY, int columnCacheSize, boolean forceStateful
    ) {
        ChunkPlan requested = new ChunkPlan(finalDensity, cellWidth, cellHeight, minY, maxY, forceStateful);
        this.plan = requested;
        this.minY = requested.minY;
        this.height = requested.height();

        ChunkPlan dimension = null;
        if (!requested.pure) {
            LOGGER.debug("Using exact chunk evaluation: {}", requested.impurity);
            ChunkPlan d = new ChunkPlan(finalDensity, cellWidth, cellHeight, dimensionMinY, dimensionMaxY, forceStateful);
            boolean same = d.minY == requested.minY && d.height() == requested.height();
            boolean overlaps = d.minY < this.minY + height && this.minY < d.minY + d.height();
            if (!same && overlaps) dimension = d;
        }
        this.dimensionPlan = dimension;

        this.columnCache = plan.pure && columnCacheSize > 0 ? new ColumnCache(columnCacheSize) : null;
        this.evaluators = ThreadLocal.withInitial(
            () -> plan.pure ? new PureChunkEvaluator(plan, columnCache) : new StatefulChunkEvaluator(plan));
        if (dimension != null) {
            ChunkPlan d = dimension;
            this.dimensionEvaluators = ThreadLocal.withInitial(() -> new StatefulChunkEvaluator(d));
            this.dimensionBuffers = ThreadLocal.withInitial(() -> new double[d.height() * 256]);
        } else {
            this.dimensionEvaluators = null;
            this.dimensionBuffers = null;
        }
    }

    /// Whether this sampler uses the fast evaluator.
    public boolean isPure() {
        return plan.pure;
    }

    /// The lowest Y in the arrays produced by [#sample].
    public int minY() {
        return minY;
    }

    /// The number of Y levels in the arrays produced by [#sample].
    public int height() {
        return height;
    }

    /// The shared corner-column cache, or null if disabled.
    public ColumnCache columnCache() {
        return columnCache;
    }

    public double[] newBuffer() {
        return new double[height * 256];
    }

    /// @param out at least `height() * 256` values; receives density indexed `((y - minY()) * 16 + z) * 16 + x`
    public void sample(int chunkX, int chunkZ, double[] out) {
        ChunkPlan d = dimensionPlan;
        if (d == null) {
            evaluators.get().fill(chunkX, chunkZ, out);
            return;
        }
        double[] full = dimensionBuffers.get();
        dimensionEvaluators.get().fill(chunkX, chunkZ, full);
        int lo = Math.max(minY, d.minY), hi = Math.min(minY + height, d.minY + d.height());
        if (lo > minY || hi < minY + height) {
            // Part of the requested range lies outside the dimension's.
            evaluators.get().fill(chunkX, chunkZ, out);
        }
        System.arraycopy(full, (lo - d.minY) * 256, out, (lo - minY) * 256, (hi - lo) * 256);
    }
}
