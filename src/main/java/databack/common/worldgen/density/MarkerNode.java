package databack.common.worldgen.density;

/// One of the five caching markers (spec 4). At a single point every marker is its argument's value; inside a chunk
/// each has state, which [ChunkDensitySampler] implements. A marker's estimate is its argument's (verified by the
/// `marker_bounds_*` cases).
public final class MarkerNode extends DensityNode {

    public enum Kind {
        INTERPOLATED, FLAT_CACHE, CACHE_2D, CACHE_ONCE, CACHE_ALL_IN_CELL
    }

    public final Kind kind;
    public final DensityNode argument;

    MarkerNode(Kind kind, DensityNode argument) {
        this(kind, argument, argument.lo, argument.hi);
    }

    private MarkerNode(Kind kind, DensityNode argument, double lo, double hi) {
        super(lo, hi, kind.ordinal(), argument);
        this.kind = kind;
        this.argument = argument;
    }

    @Override
    public double sample(int x, int y, int z) {
        return argument.sample(x, y, z);
    }

    @Override
    public DensityNode[] inputs() {
        return new DensityNode[] { argument };
    }

    @Override
    public DensityNode withInputs(DensityNode[] inputs) {
        return new MarkerNode(kind, inputs[0], lo, hi);
    }

    @Override
    public DensityNode withBounds(double lo, double hi) {
        return new MarkerNode(kind, argument, lo, hi);
    }

    @Override
    protected boolean sameParams(DensityNode other) {
        return kind == ((MarkerNode) other).kind;
    }

    @Override
    public String toString() {
        return kind.name().toLowerCase() + "(" + argument + ")";
    }
}
