package databack.common.worldgen.density;

import databack.common.worldgen.noise.NormalNoise;

/// Direct noise sampling at the block position (spec 3.7): `noise`, and the three shift functions.
public final class NoiseNode extends DensityNode {

    public enum Kind {
        /// `noise(x * xzScale, y * yScale, z * xzScale)`
        NOISE,
        /// `4 * noise(x / 4, 0, z / 4)`
        SHIFT_A,
        /// `4 * noise(z / 4, x / 4, 0)`
        SHIFT_B,
        /// `4 * noise(x / 4, y / 4, z / 4)`
        SHIFT
    }

    public final Kind kind;
    public final SeededNoise noise;
    /// Only used by [Kind#NOISE].
    public final double xzScale, yScale;
    private final NormalNoise normal;

    static NoiseNode noise(SeededNoise noise, double xzScale, double yScale) {
        double n = noise.maxValue();
        return new NoiseNode(Kind.NOISE, noise, xzScale, yScale, -n, n);
    }

    static NoiseNode shift(Kind kind, SeededNoise noise) {
        double n = 4.0 * noise.maxValue();
        return new NoiseNode(kind, noise, 0.25, 0.25, -n, n);
    }

    private NoiseNode(Kind kind, SeededNoise noise, double xzScale, double yScale, double lo, double hi) {
        super(lo, hi, ((kind.ordinal() * 31 + System.identityHashCode(noise)) * 31 + Double.hashCode(xzScale)) * 31
            + Double.hashCode(yScale));
        this.kind = kind;
        this.noise = noise;
        this.xzScale = xzScale;
        this.yScale = yScale;
        this.normal = noise.noise;
    }

    @Override
    public double sample(int x, int y, int z) {
        switch (kind) {
            case NOISE: return normal.getValue(x * xzScale, y * yScale, z * xzScale);
            case SHIFT_A: return normal.getValue(x * 0.25, 0.0, z * 0.25) * 4.0;
            case SHIFT_B: return normal.getValue(z * 0.25, x * 0.25, 0.0) * 4.0;
            default: return normal.getValue(x * 0.25, y * 0.25, z * 0.25) * 4.0;
        }
    }

    @Override
    public DensityNode withBounds(double lo, double hi) {
        return new NoiseNode(kind, noise, xzScale, yScale, lo, hi);
    }

    @Override
    protected boolean sameParams(DensityNode o) {
        NoiseNode other = (NoiseNode) o;
        return kind == other.kind && noise == other.noise && DensityMath.same(xzScale, other.xzScale)
            && DensityMath.same(yScale, other.yScale);
    }

    @Override
    public String toString() {
        return kind.name().toLowerCase() + "(" + noise + ")";
    }
}
