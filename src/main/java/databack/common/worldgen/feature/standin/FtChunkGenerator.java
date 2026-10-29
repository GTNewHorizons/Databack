package databack.common.worldgen.feature.standin;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/// Stand-in for the chunk generator, as features use it: the generated height range, and which features each biome
/// has.
@ApiStatus.Internal
public interface FtChunkGenerator {

    /// The lowest generated Y.
    int getMinY();

    /// The number of generated layers.
    int getGenDepth();

    /// The features of `biome` (a holder of the biome's id).
    @NotNull
    FtBiomeFeatures getBiomeGenerationSettings(@NotNull FtHolder biome);
}
