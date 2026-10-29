package databack.common.worldgen.feature.standin;

import org.jetbrains.annotations.ApiStatus;

import databack.common.worldgen.noise.SimplexNoise;
import databack.common.worldgen.rng.StandardRandom;

/// Stand-in for the biome info noise ([FtBiome#BIOME_INFO_NOISE]) and noises like it: one simplex noise from a legacy
/// random, read in two dimensions (`feature/probes/info_noise.tsv`).
@ApiStatus.Internal
public final class FtInfoNoise {

    private final SimplexNoise noise;

    public FtInfoNoise(long seed) {
        this.noise = new SimplexNoise(new StandardRandom(seed));
    }

    /// With `offset`, the noise's own random offsets shift `x` and `z`.
    public double getValue(double x, double z, boolean offset) {
        return offset ? noise.getValue(x + noise.xo, z + noise.yo) : noise.getValue(x, z);
    }
}
