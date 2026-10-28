package databack.common.worldgen.density;

/// Constructors for every density function type. The DTO factories build trees through these.
public final class Density {

    private Density() {}

    public static final ConstantNode ZERO = new ConstantNode(0.0);

    public static ConstantNode constant(double value) {
        return value == 0.0 && Double.doubleToLongBits(value) == 0L ? ZERO : new ConstantNode(value);
    }

    public static DensityNode yGradient(int fromY, int toY, double fromValue, double toValue) {
        return new GradientNode(fromY, toY, fromValue, toValue);
    }

    public static DensityNode transform(TransformNode.Kind kind, DensityNode input) {
        return new TransformNode(kind, input);
    }

    public static DensityNode clamp(DensityNode input, double min, double max) {
        return new ClampNode(input, min, max);
    }

    public static DensityNode blendDensity(DensityNode input) {
        return new BlendDensityNode(input);
    }

    /// `add`: the constant form if either argument is a [ConstantNode] (spec 3.4).
    public static DensityNode add(DensityNode a, DensityNode b) {
        if (a instanceof ConstantNode c) return new AffineNode(b, false, c.value);
        if (b instanceof ConstantNode c) return new AffineNode(a, false, c.value);
        return new PairNode(PairNode.Op.ADD, a, b);
    }

    /// `mul`: the constant form if either argument is a [ConstantNode] (spec 3.4). A first argument of 0 is special,
    /// see [AffineNode#zeroFirst].
    public static DensityNode mul(DensityNode a, DensityNode b) {
        if (a instanceof ConstantNode c) return new AffineNode(b, true, c.value, true);
        if (b instanceof ConstantNode c) return new AffineNode(a, true, c.value);
        return new PairNode(PairNode.Op.MUL, a, b);
    }

    public static DensityNode min(DensityNode a, DensityNode b) {
        return new PairNode(PairNode.Op.MIN, a, b);
    }

    public static DensityNode max(DensityNode a, DensityNode b) {
        return new PairNode(PairNode.Op.MAX, a, b);
    }

    public static DensityNode rangeChoice(
        DensityNode input, double minInclusive, double maxExclusive, DensityNode whenInRange, DensityNode whenOutOfRange
    ) {
        return new RangeChoiceNode(input, minInclusive, maxExclusive, whenInRange, whenOutOfRange);
    }

    public static DensityNode noise(SeededNoise noise, double xzScale, double yScale) {
        return NoiseNode.noise(noise, xzScale, yScale);
    }

    public static DensityNode shiftA(SeededNoise noise) {
        return NoiseNode.shift(NoiseNode.Kind.SHIFT_A, noise);
    }

    public static DensityNode shiftB(SeededNoise noise) {
        return NoiseNode.shift(NoiseNode.Kind.SHIFT_B, noise);
    }

    public static DensityNode shift(SeededNoise noise) {
        return NoiseNode.shift(NoiseNode.Kind.SHIFT, noise);
    }

    public static DensityNode shiftedNoise(
        DensityNode shiftX, DensityNode shiftY, DensityNode shiftZ, double xzScale, double yScale, SeededNoise noise
    ) {
        return new ShiftedNoiseNode(shiftX, shiftY, shiftZ, xzScale, yScale, noise);
    }

    public static DensityNode weirdScaled(DensityNode input, SeededNoise noise, WeirdScaledNode.Rarity rarity) {
        return new WeirdScaledNode(input, noise, rarity);
    }

    public static DensityNode findTopSurface(DensityNode density, DensityNode upperBound, int lowerBound, int cellHeight) {
        return new FindTopSurfaceNode(density, upperBound, lowerBound, cellHeight);
    }

    public static DensityNode spline(Spline spline) {
        return new SplineNode(spline);
    }

    public static DensityNode marker(MarkerNode.Kind kind, DensityNode argument) {
        return new MarkerNode(kind, argument);
    }

    public static DensityNode interpolated(DensityNode argument) {
        return marker(MarkerNode.Kind.INTERPOLATED, argument);
    }

    public static DensityNode flatCache(DensityNode argument) {
        return marker(MarkerNode.Kind.FLAT_CACHE, argument);
    }

    public static DensityNode cache2d(DensityNode argument) {
        return marker(MarkerNode.Kind.CACHE_2D, argument);
    }

    public static DensityNode cacheOnce(DensityNode argument) {
        return marker(MarkerNode.Kind.CACHE_ONCE, argument);
    }

    public static DensityNode cacheAllInCell(DensityNode argument) {
        return marker(MarkerNode.Kind.CACHE_ALL_IN_CELL, argument);
    }
}
