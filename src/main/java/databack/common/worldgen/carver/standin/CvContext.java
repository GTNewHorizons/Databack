package databack.common.worldgen.carver.standin;

import java.util.Optional;
import java.util.function.Function;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/// Stand-in for the carving context: the generated height range, and the surface rule's top material.
@ApiStatus.Internal
public final class CvContext {

    /// The surface material for an exposed block, as the surface rule places it.
    @FunctionalInterface
    public interface TopMaterial {

        /// The block the surface rule puts at `pos` of `chunk`, or null for none. `underFluid` is whether fluid lies
        /// above it.
        @Nullable
        CvBlockState at(@NotNull CvChunk chunk, @NotNull CvBlockPos pos, boolean underFluid);
    }

    private final int minGenY, genDepth;
    @NotNull
    private final TopMaterial topMaterial;

    public CvContext(int minGenY, int genDepth, @NotNull TopMaterial topMaterial) {
        this.minGenY = minGenY;
        this.genDepth = genDepth;
        this.topMaterial = topMaterial;
    }

    public int getMinGenY() {
        return minGenY;
    }

    public int getGenDepth() {
        return genDepth;
    }

    /// `biomes` is vanilla's biome lookup, which [TopMaterial] does without.
    @NotNull
    public Optional<CvBlockState> topMaterial(
        @Nullable Function<?, ?> biomes, @NotNull CvChunk chunk, @NotNull CvBlockPos pos, boolean underFluid
    ) {
        return Optional.ofNullable(topMaterial.at(chunk, pos, underFluid));
    }
}
