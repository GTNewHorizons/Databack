package databack.common.worldgen.feature.standin;

import org.jetbrains.annotations.ApiStatus;

/// Stand-in for the biome info noise ([FtBiome#BIOME_INFO_NOISE]). Not probed yet, so the placements that read it are
/// unsupported.
@ApiStatus.Internal
public final class FtInfoNoise {

    FtInfoNoise() {}

    public double getValue(double x, double z, boolean offset) {
        throw new UnsupportedOperationException("The biome info noise is not implemented");
    }
}
