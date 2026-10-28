package databack.common.worldgen.density;

/// `weird_scaled_sampler` (spec 3.7): maps the input to a rarity `r`, then `r * |noise(x / r, y / r, z / r)|`.
public final class WeirdScaledNode extends DensityNode {

    public enum Rarity {
        TYPE_1(2.0), TYPE_2(3.0);

        public final double maxRarity;

        Rarity(double maxRarity) {
            this.maxRarity = maxRarity;
        }

        public double of(double v) {
            if (this == TYPE_1) {
                if (v < -0.5) return 0.75;
                if (v < 0.0) return 1.0;
                if (v < 0.5) return 1.5;
                return 2.0;
            }
            if (v < -0.75) return 0.5;
            if (v < -0.5) return 0.75;
            if (v < 0.5) return 1.0;
            if (v < 0.75) return 2.0;
            return 3.0;
        }
    }

    public final DensityNode input;
    public final SeededNoise noise;
    public final Rarity rarity;

    WeirdScaledNode(DensityNode input, SeededNoise noise, Rarity rarity) {
        this(input, noise, rarity, 0.0, rarity.maxRarity * noise.maxValue());
    }

    private WeirdScaledNode(DensityNode input, SeededNoise noise, Rarity rarity, double lo, double hi) {
        super(lo, hi, System.identityHashCode(noise) * 31 + rarity.ordinal(), input);
        this.input = input;
        this.noise = noise;
        this.rarity = rarity;
    }

    /// The result at a position, given the input's value there.
    public double at(int x, int y, int z, double v) {
        double r = rarity.of(v);
        return r * Math.abs(noise.noise.getValue(x / r, y / r, z / r));
    }

    @Override
    public double sample(int x, int y, int z) {
        return at(x, y, z, input.sample(x, y, z));
    }

    @Override
    public DensityNode[] inputs() {
        return new DensityNode[] { input };
    }

    @Override
    public DensityNode withInputs(DensityNode[] inputs) {
        return new WeirdScaledNode(inputs[0], noise, rarity, lo, hi);
    }

    @Override
    public DensityNode withBounds(double lo, double hi) {
        return new WeirdScaledNode(input, noise, rarity, lo, hi);
    }

    @Override
    protected boolean sameParams(DensityNode o) {
        WeirdScaledNode other = (WeirdScaledNode) o;
        return noise == other.noise && rarity == other.rarity;
    }
}
