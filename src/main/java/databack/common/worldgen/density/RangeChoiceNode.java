package databack.common.worldgen.density;

/// `range_choice` (spec 3.6): `whenIn` if `minInclusive <= input < maxExclusive` (NaN is out of range), else
/// `whenOut`. Only the chosen branch is evaluated.
public final class RangeChoiceNode extends DensityNode {

    public final DensityNode input, whenIn, whenOut;
    public final double minInclusive, maxExclusive;

    RangeChoiceNode(DensityNode input, double minInclusive, double maxExclusive, DensityNode whenIn, DensityNode whenOut) {
        this(input, minInclusive, maxExclusive, whenIn, whenOut,
            Math.min(whenIn.lo, whenOut.lo), Math.max(whenIn.hi, whenOut.hi));
    }

    private RangeChoiceNode(
        DensityNode input, double minInclusive, double maxExclusive, DensityNode whenIn, DensityNode whenOut, double lo,
        double hi
    ) {
        super(lo, hi, Double.hashCode(minInclusive) * 31 + Double.hashCode(maxExclusive), input, whenIn, whenOut);
        this.input = input;
        this.whenIn = whenIn;
        this.whenOut = whenOut;
        this.minInclusive = minInclusive;
        this.maxExclusive = maxExclusive;
    }

    public boolean inRange(double v) {
        return v >= minInclusive && v < maxExclusive;
    }

    @Override
    public double sample(int x, int y, int z) {
        return inRange(input.sample(x, y, z)) ? whenIn.sample(x, y, z) : whenOut.sample(x, y, z);
    }

    @Override
    public DensityNode[] inputs() {
        return new DensityNode[] { input, whenIn, whenOut };
    }

    @Override
    public DensityNode withInputs(DensityNode[] inputs) {
        return new RangeChoiceNode(inputs[0], minInclusive, maxExclusive, inputs[1], inputs[2], lo, hi);
    }

    @Override
    public DensityNode withBounds(double lo, double hi) {
        return new RangeChoiceNode(input, minInclusive, maxExclusive, whenIn, whenOut, lo, hi);
    }

    @Override
    protected boolean sameParams(DensityNode o) {
        RangeChoiceNode other = (RangeChoiceNode) o;
        return DensityMath.same(minInclusive, other.minInclusive) && DensityMath.same(maxExclusive, other.maxExclusive);
    }
}
