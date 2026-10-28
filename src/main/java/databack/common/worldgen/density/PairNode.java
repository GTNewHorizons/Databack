package databack.common.worldgen.density;

/// The general (non-constant) forms of `add`, `mul`, `min` and `max` (spec 3.4, 3.5).
///
/// - `add`: `a + b`.
/// - `mul`: `0.0` when `a == 0` (then `b` is skipped), else `a * b`.
/// - `min`: `a` when `a < b.lo` (then `b` is skipped), else `min(a, b)`.
/// - `max`: `a` when `a > b.hi` (then `b` is skipped), else `max(a, b)`.
public final class PairNode extends DensityNode {

    public enum Op {
        ADD, MUL, MIN, MAX
    }

    public final Op op;
    public final DensityNode a, b;

    PairNode(Op op, DensityNode a, DensityNode b) {
        this(op, a, b, estimateLo(op, a, b), estimateHi(op, a, b));
    }

    private PairNode(Op op, DensityNode a, DensityNode b, double lo, double hi) {
        super(lo, hi, op.ordinal(), a, b);
        this.op = op;
        this.a = a;
        this.b = b;
    }

    private static double estimateLo(Op op, DensityNode a, DensityNode b) {
        switch (op) {
            case ADD: return a.lo + b.lo;
            case MIN: return Math.min(a.lo, b.lo);
            case MAX: return Math.max(a.lo, b.lo);
            default: {
                if (a.lo > 0.0 && b.lo > 0.0) return a.lo * b.lo;
                if (a.hi < 0.0 && b.hi < 0.0) return a.hi * b.hi;
                return Math.min(a.lo * b.hi, a.hi * b.lo);
            }
        }
    }

    private static double estimateHi(Op op, DensityNode a, DensityNode b) {
        switch (op) {
            case ADD: return a.hi + b.hi;
            case MIN: return Math.min(a.hi, b.hi);
            case MAX: return Math.max(a.hi, b.hi);
            default: {
                // Not a sound bound when one side is positive and the other negative; required anyway (spec 3.4).
                if (a.lo > 0.0 && b.lo > 0.0) return a.hi * b.hi;
                if (a.hi < 0.0 && b.hi < 0.0) return a.lo * b.lo;
                return Math.max(a.lo * b.lo, a.hi * b.hi);
            }
        }
    }

    /// Whether `b` has to be evaluated once `a`'s value is known.
    public boolean needsB(double va) {
        switch (op) {
            case ADD: return true;
            case MUL: return va != 0.0;
            case MIN: return !(va < b.lo);
            default: return !(va > b.hi);
        }
    }

    /// The result from `a`'s value and `b`'s value, for points where [#needsB] holds.
    public double combine(double va, double vb) {
        switch (op) {
            case ADD: return va + vb;
            case MUL: return va * vb;
            case MIN: return Math.min(va, vb);
            default: return Math.max(va, vb);
        }
    }

    /// The result for points where [#needsB] does not hold.
    public double skipped(double va) {
        return op == Op.MUL ? 0.0 : va;
    }

    @Override
    public double sample(int x, int y, int z) {
        double va = a.sample(x, y, z);
        switch (op) {
            case ADD: return va + b.sample(x, y, z);
            case MUL: return va == 0.0 ? 0.0 : va * b.sample(x, y, z);
            case MIN: return va < b.lo ? va : Math.min(va, b.sample(x, y, z));
            default: return va > b.hi ? va : Math.max(va, b.sample(x, y, z));
        }
    }

    @Override
    public DensityNode[] inputs() {
        return new DensityNode[] { a, b };
    }

    @Override
    public DensityNode withInputs(DensityNode[] inputs) {
        return new PairNode(op, inputs[0], inputs[1], lo, hi);
    }

    @Override
    public DensityNode withBounds(double lo, double hi) {
        return new PairNode(op, a, b, lo, hi);
    }

    @Override
    protected boolean sameParams(DensityNode other) {
        return op == ((PairNode) other).op;
    }

    @Override
    public String toString() {
        return op.name().toLowerCase() + "(" + a + ", " + b + ")";
    }
}
