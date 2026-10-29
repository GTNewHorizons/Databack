package databack.common.worldgen.feature.standin;

import org.jetbrains.annotations.ApiStatus;

/// Stand-in for the biome constants features use.
@ApiStatus.Internal
public final class FtBiome {

    /// The noise flower and grass counts follow: seeded 2345 (`feature/probes/info_noise.tsv`).
    public static final FtInfoNoise BIOME_INFO_NOISE = new FtInfoNoise(2345L);

    private FtBiome() {}
}
