package databack.common.worldgen.density;

/// `clamp` (spec 3.3). The estimate is the clamp range itself, whatever the input's estimate.
public final class ClampNode extends DensityNode {

    public final DensityNode input;
    public final double min, max;

    ClampNode(DensityNode input, double min, double max) {
        this(input, min, max, min, max);
    }

    private ClampNode(DensityNode input, double min, double max, double lo, double hi) {
        super(lo, hi, Double.hashCode(min) * 31 + Double.hashCode(max), input);
        this.input = input;
        this.min = min;
        this.max = max;
    }

    @Override
    public double sample(int x, int y, int z) {
        return DensityMath.clamp(input.sample(x, y, z), min, max);
    }

    @Override
    public DensityNode[] inputs() {
        return new DensityNode[] { input };
    }

    @Override
    public DensityNode withInputs(DensityNode[] inputs) {
        return new ClampNode(inputs[0], min, max, lo, hi);
    }

    @Override
    public DensityNode withBounds(double lo, double hi) {
        return new ClampNode(input, min, max, lo, hi);
    }

    @Override
    protected boolean sameParams(DensityNode o) {
        ClampNode other = (ClampNode) o;
        return DensityMath.same(min, other.min) && DensityMath.same(max, other.max);
    }
}
