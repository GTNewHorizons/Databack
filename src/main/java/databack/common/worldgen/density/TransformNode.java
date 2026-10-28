package databack.common.worldgen.density;

/// The single-input transforms of spec 3.3 (`abs`, `square`, `cube`, `half_negative`, `quarter_negative`, `invert`,
/// `squeeze`).
public final class TransformNode extends DensityNode {

    public enum Kind {
        ABS, SQUARE, CUBE, HALF_NEGATIVE, QUARTER_NEGATIVE, INVERT, SQUEEZE;

        public double apply(double v) {
            switch (this) {
                case ABS: return Math.abs(v);
                case SQUARE: return v * v;
                case CUBE: return v * v * v;
                case HALF_NEGATIVE: return v > 0.0 ? v : v * 0.5;
                case QUARTER_NEGATIVE: return v > 0.0 ? v : v * 0.25;
                case INVERT: return 1.0 / v;
                case SQUEEZE: {
                    double c = DensityMath.clamp(v, -1.0, 1.0);
                    return c / 2.0 - c * c * c / 24.0;
                }
                default: throw new AssertionError(this);
            }
        }
    }

    public final Kind kind;
    public final DensityNode input;

    TransformNode(Kind kind, DensityNode input) {
        this(kind, input, estimateLo(kind, input), estimateHi(kind, input));
    }

    private TransformNode(Kind kind, DensityNode input, double lo, double hi) {
        super(lo, hi, kind.ordinal(), input);
        this.kind = kind;
        this.input = input;
    }

    private static double estimateLo(Kind kind, DensityNode in) {
        switch (kind) {
            case INVERT: return in.lo < 0.0 && in.hi > 0.0 ? Double.NEGATIVE_INFINITY : kind.apply(in.hi);
            case ABS:
            case SQUARE: return Math.max(0.0, in.lo);
            default: return kind.apply(in.lo);
        }
    }

    private static double estimateHi(Kind kind, DensityNode in) {
        switch (kind) {
            case INVERT: return in.lo < 0.0 && in.hi > 0.0 ? Double.POSITIVE_INFINITY : kind.apply(in.lo);
            case ABS:
            case SQUARE: return Math.max(kind.apply(in.lo), kind.apply(in.hi));
            default: return kind.apply(in.hi);
        }
    }

    @Override
    public double sample(int x, int y, int z) {
        return kind.apply(input.sample(x, y, z));
    }

    @Override
    public DensityNode[] inputs() {
        return new DensityNode[] { input };
    }

    @Override
    public DensityNode withInputs(DensityNode[] inputs) {
        return new TransformNode(kind, inputs[0], lo, hi);
    }

    @Override
    public DensityNode withBounds(double lo, double hi) {
        return new TransformNode(kind, input, lo, hi);
    }

    @Override
    protected boolean sameParams(DensityNode other) {
        return kind == ((TransformNode) other).kind;
    }

    @Override
    public String toString() {
        return kind.name().toLowerCase() + "(" + input + ")";
    }
}
