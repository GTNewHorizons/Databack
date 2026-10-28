package databack.common.worldgen.density;

/// `shifted_noise` (spec 3.7): noise at `(x * xzScale + sx, y * yScale + sy, z * xzScale + sz)`, with the three
/// shift inputs evaluated in x, y, z order.
public final class ShiftedNoiseNode extends DensityNode {

    public final DensityNode shiftX, shiftY, shiftZ;
    public final double xzScale, yScale;
    public final SeededNoise noise;

    ShiftedNoiseNode(
        DensityNode shiftX, DensityNode shiftY, DensityNode shiftZ, double xzScale, double yScale, SeededNoise noise
    ) {
        this(shiftX, shiftY, shiftZ, xzScale, yScale, noise, -noise.maxValue(), noise.maxValue());
    }

    private ShiftedNoiseNode(
        DensityNode shiftX, DensityNode shiftY, DensityNode shiftZ, double xzScale, double yScale, SeededNoise noise,
        double lo, double hi
    ) {
        super(lo, hi, (System.identityHashCode(noise) * 31 + Double.hashCode(xzScale)) * 31 + Double.hashCode(yScale),
            shiftX, shiftY, shiftZ);
        this.shiftX = shiftX;
        this.shiftY = shiftY;
        this.shiftZ = shiftZ;
        this.xzScale = xzScale;
        this.yScale = yScale;
        this.noise = noise;
    }

    /// The noise at a position, given the three shift values.
    public double at(int x, int y, int z, double sx, double sy, double sz) {
        return noise.noise.getValue(x * xzScale + sx, y * yScale + sy, z * xzScale + sz);
    }

    @Override
    public double sample(int x, int y, int z) {
        double sx = shiftX.sample(x, y, z);
        double sy = shiftY.sample(x, y, z);
        double sz = shiftZ.sample(x, y, z);
        return at(x, y, z, sx, sy, sz);
    }

    @Override
    public DensityNode[] inputs() {
        return new DensityNode[] { shiftX, shiftY, shiftZ };
    }

    @Override
    public DensityNode withInputs(DensityNode[] inputs) {
        return new ShiftedNoiseNode(inputs[0], inputs[1], inputs[2], xzScale, yScale, noise, lo, hi);
    }

    @Override
    public DensityNode withBounds(double lo, double hi) {
        return new ShiftedNoiseNode(shiftX, shiftY, shiftZ, xzScale, yScale, noise, lo, hi);
    }

    @Override
    protected boolean sameParams(DensityNode o) {
        ShiftedNoiseNode other = (ShiftedNoiseNode) o;
        return noise == other.noise && DensityMath.same(xzScale, other.xzScale) && DensityMath.same(yScale, other.yScale);
    }
}
