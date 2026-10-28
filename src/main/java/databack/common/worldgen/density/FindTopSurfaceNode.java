package databack.common.worldgen.density;

/// `find_top_surface` (spec 3.8): scans down from `upperBound` (rounded down to a multiple of `cellHeight`) in steps of
/// `cellHeight` and returns the first y whose density is positive, or `lowerBound`.
///
/// The density is always evaluated at single points, with markers transparent, even inside a chunk.
public final class FindTopSurfaceNode extends DensityNode {

    public final DensityNode density, upperBound;
    public final int lowerBound, cellHeight;

    FindTopSurfaceNode(DensityNode density, DensityNode upperBound, int lowerBound, int cellHeight) {
        this(density, upperBound, lowerBound, cellHeight, lowerBound, Math.max(lowerBound, upperBound.hi));
    }

    private FindTopSurfaceNode(
        DensityNode density, DensityNode upperBound, int lowerBound, int cellHeight, double lo, double hi
    ) {
        super(lo, hi, lowerBound * 31 + cellHeight, density, upperBound);
        this.density = density;
        this.upperBound = upperBound;
        this.lowerBound = lowerBound;
        this.cellHeight = cellHeight;
    }

    /// The scan, given the upper bound's value at the column.
    public double scan(int x, int z, double upper) {
        int top = DensityMath.floor(upper / cellHeight) * cellHeight;
        if (top <= lowerBound) return lowerBound;
        for (int y = top; y >= lowerBound; y -= cellHeight) {
            if (density.sample(x, y, z) > 0.0) return y;
        }
        return lowerBound;
    }

    @Override
    public double sample(int x, int y, int z) {
        return scan(x, z, upperBound.sample(x, y, z));
    }

    @Override
    public DensityNode[] inputs() {
        return new DensityNode[] { density, upperBound };
    }

    @Override
    public DensityNode withInputs(DensityNode[] inputs) {
        return new FindTopSurfaceNode(inputs[0], inputs[1], lowerBound, cellHeight, lo, hi);
    }

    @Override
    public DensityNode withBounds(double lo, double hi) {
        return new FindTopSurfaceNode(density, upperBound, lowerBound, cellHeight, lo, hi);
    }

    @Override
    protected boolean sameParams(DensityNode o) {
        FindTopSurfaceNode other = (FindTopSurfaceNode) o;
        return lowerBound == other.lowerBound && cellHeight == other.cellHeight;
    }
}
