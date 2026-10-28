package databack.common.worldgen.density;

/// `y_clamped_gradient` (spec 3.1): maps the block's y from `[fromY, toY]` onto `[fromValue, toValue]`, clamped.
public final class GradientNode extends DensityNode {

    public final int fromY, toY;
    public final double fromValue, toValue;

    GradientNode(int fromY, int toY, double fromValue, double toValue) {
        this(fromY, toY, fromValue, toValue, Math.min(fromValue, toValue), Math.max(fromValue, toValue));
    }

    private GradientNode(int fromY, int toY, double fromValue, double toValue, double lo, double hi) {
        super(lo, hi, ((fromY * 31 + toY) * 31 + Double.hashCode(fromValue)) * 31 + Double.hashCode(toValue));
        this.fromY = fromY;
        this.toY = toY;
        this.fromValue = fromValue;
        this.toValue = toValue;
    }

    /// The value at a given y (the gradient ignores x and z).
    public double at(int y) {
        return DensityMath.clampedMap(y, fromY, toY, fromValue, toValue);
    }

    @Override
    public double sample(int x, int y, int z) {
        return at(y);
    }

    @Override
    public DensityNode withBounds(double lo, double hi) {
        return new GradientNode(fromY, toY, fromValue, toValue, lo, hi);
    }

    @Override
    protected boolean sameParams(DensityNode o) {
        GradientNode other = (GradientNode) o;
        return fromY == other.fromY && toY == other.toY && DensityMath.same(fromValue, other.fromValue)
            && DensityMath.same(toValue, other.toValue);
    }
}
