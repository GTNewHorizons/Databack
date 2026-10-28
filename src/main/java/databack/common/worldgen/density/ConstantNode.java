package databack.common.worldgen.density;

/// A bare number in the datapack JSON (or `minecraft:constant`). Only these count as constants for the constant
/// forms of `add` and `mul` (spec 3.4); see [Density#add].
public final class ConstantNode extends DensityNode {

    public final double value;

    ConstantNode(double value) {
        this(value, value, value);
    }

    private ConstantNode(double value, double lo, double hi) {
        super(lo, hi, Double.hashCode(value));
        this.value = value;
    }

    @Override
    public double sample(int x, int y, int z) {
        return value;
    }

    @Override
    public DensityNode withBounds(double lo, double hi) {
        return new ConstantNode(value, lo, hi);
    }

    @Override
    protected boolean sameParams(DensityNode other) {
        return DensityMath.same(value, ((ConstantNode) other).value);
    }

    @Override
    public String toString() {
        return Double.toString(value);
    }
}
