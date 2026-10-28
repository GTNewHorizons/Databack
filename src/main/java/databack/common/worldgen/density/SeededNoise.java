package databack.common.worldgen.density;

import databack.common.worldgen.noise.NormalNoise;

/// A `worldgen/noise` entry instantiated for one world seed. [DensityBuildContext] creates one per id, so noise
/// nodes can compare their noises by identity.
public final class SeededNoise {

    public final String id;
    public final NormalNoise noise;

    public SeededNoise(String id, NormalNoise noise) {
        this.id = id;
        this.noise = noise;
    }

    public double getValue(double x, double y, double z) {
        return noise.getValue(x, y, z);
    }

    /// The bound `N` of `|noise|` (spec 3.7).
    public double maxValue() {
        return noise.maxValue();
    }

    @Override
    public String toString() {
        return id;
    }
}
