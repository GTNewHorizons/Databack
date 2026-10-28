package databack.common.worldgen.density;

/// `blend_density` without blending data (spec 3.2): its input's value, with an unbounded estimate.
public final class BlendDensityNode extends DensityNode {

    public final DensityNode input;

    BlendDensityNode(DensityNode input) {
        this(input, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);
    }

    private BlendDensityNode(DensityNode input, double lo, double hi) {
        super(lo, hi, 0, input);
        this.input = input;
    }

    @Override
    public double sample(int x, int y, int z) {
        return input.sample(x, y, z);
    }

    @Override
    public DensityNode[] inputs() {
        return new DensityNode[] { input };
    }

    @Override
    public DensityNode withInputs(DensityNode[] inputs) {
        return new BlendDensityNode(inputs[0], lo, hi);
    }

    @Override
    public DensityNode withBounds(double lo, double hi) {
        return new BlendDensityNode(input, lo, hi);
    }

    @Override
    protected boolean sameParams(DensityNode other) {
        return true;
    }
}
