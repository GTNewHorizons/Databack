package databack.common.worldgen.density;

/// The constant forms of `add` and `mul` (spec 3.4): `input + c` or `input * c`, where `c` was a constant argument.
/// In a batch the input is read as a batch.
///
/// One exception, `mul` whose *first* argument is the constant 0 ([#zeroFirst]): evaluated on its own at a single
/// point it is 0 without evaluating the input, but as part of a chunk it is `input * 0` (NaN for an infinite input).
/// See [ChunkPlan#forChunk] and cases `constant_bare_zero*`.
public final class AffineNode extends DensityNode {

    public final DensityNode input;
    /// true for `input * c`, false for `input + c`
    public final boolean multiply;
    public final double c;
    /// `mul(0, input)` at a single point: 0, without evaluating the input.
    public final boolean zeroFirst;

    AffineNode(DensityNode input, boolean multiply, double c) {
        this(input, multiply, c, false);
    }

    AffineNode(DensityNode input, boolean multiply, double c, boolean constantFirst) {
        this(input, multiply, c, constantFirst && multiply && c == 0.0, estimateLo(input, multiply, c),
            estimateHi(input, multiply, c));
    }

    private AffineNode(DensityNode input, boolean multiply, double c, boolean zeroFirst, double lo, double hi) {
        super(lo, hi, Double.hashCode(c) * 4 + (multiply ? 1 : 0) + (zeroFirst ? 2 : 0), input);
        this.input = input;
        this.multiply = multiply;
        this.c = c;
        this.zeroFirst = zeroFirst;
    }

    /// The form used inside a chunk: `input * c` with no short circuit.
    public AffineNode inChunk() {
        return zeroFirst ? new AffineNode(input, multiply, c, false, lo, hi) : this;
    }

    private static double estimateLo(DensityNode in, boolean multiply, double c) {
        if (!multiply) return in.lo + c;
        return c >= 0.0 ? in.lo * c : in.hi * c;
    }

    private static double estimateHi(DensityNode in, boolean multiply, double c) {
        if (!multiply) return in.hi + c;
        return c >= 0.0 ? in.hi * c : in.lo * c;
    }

    public double apply(double v) {
        return multiply ? v * c : v + c;
    }

    @Override
    public double sample(int x, int y, int z) {
        if (zeroFirst) return 0.0;
        double v = input.sample(x, y, z);
        return multiply ? v * c : v + c;
    }

    @Override
    public DensityNode[] inputs() {
        return new DensityNode[] { input };
    }

    @Override
    public DensityNode withInputs(DensityNode[] inputs) {
        return new AffineNode(inputs[0], multiply, c, zeroFirst, lo, hi);
    }

    @Override
    public DensityNode withBounds(double lo, double hi) {
        return new AffineNode(input, multiply, c, zeroFirst, lo, hi);
    }

    @Override
    protected boolean sameParams(DensityNode o) {
        AffineNode other = (AffineNode) o;
        return multiply == other.multiply && zeroFirst == other.zeroFirst && DensityMath.same(c, other.c);
    }

    @Override
    public String toString() {
        return (multiply ? "mul(" : "add(") + input + ", " + c + ")";
    }
}
