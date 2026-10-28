package databack.common.worldgen.surface;

import java.util.function.Function;
import java.util.function.ToDoubleFunction;

import net.minecraft.util.ResourceLocation;

import org.jetbrains.annotations.NotNull;

import com.gtnewhorizon.gtnhlib.util.data.ImmutableBlockMeta;

import databack.common.dto.BlockStateDTO;
import databack.common.dto.worldgen.height_provider.VerticalAnchor;
import databack.common.worldgen.density.DensityBuildContext;
import databack.common.worldgen.density.SeededNoise;

/// What surface rule DTOs need to build their runtime form for one dimension.
public final class SurfaceBuildContext {

    @NotNull
    private final DensityBuildContext density;
    private final int minY;
    private final int height;
    @NotNull
    private final Function<BlockStateDTO, ImmutableBlockMeta> blocks;
    @NotNull
    private final ToDoubleFunction<String> biomeTemperature;
    @NotNull
    private final BandlandsPattern bandlands;

    /// `minY` and `height` are the dimension's; `blocks` resolves a datapack block state to a 1.7 block;
    /// `biomeTemperature` gives a biome id's temperature.
    public SurfaceBuildContext(
        @NotNull DensityBuildContext density, int minY, int height,
        @NotNull Function<BlockStateDTO, ImmutableBlockMeta> blocks, @NotNull ToDoubleFunction<String> biomeTemperature
    ) {
        this.density = density;
        this.minY = minY;
        this.height = height;
        this.blocks = blocks;
        this.biomeTemperature = biomeTemperature;
        this.bandlands = new BandlandsPattern(density.seed());
    }

    /// A datapack noise, seeded like density function noises.
    @NotNull
    public SeededNoise noise(@NotNull String id) {
        return density.noise(id);
    }

    /// `absolute v` is `v`, `above_bottom v` is `minY + v`, `below_top v` is `minY + height - 1 - v`.
    public int resolve(@NotNull VerticalAnchor anchor) {
        if (anchor.absolute != null) return anchor.absolute;
        if (anchor.above_bottom != null) return minY + anchor.above_bottom;
        if (anchor.below_top != null) return minY + height - 1 - anchor.below_top;
        throw new IllegalArgumentException("Empty vertical anchor");
    }

    @NotNull
    public ImmutableBlockMeta block(@NotNull BlockStateDTO state) {
        return blocks.apply(state);
    }

    @NotNull
    public ImmutableBlockMeta block(@NotNull String name) {
        BlockStateDTO state = new BlockStateDTO();
        state.Name = new ResourceLocation(name);
        return block(state);
    }

    @NotNull
    public ToDoubleFunction<String> biomeTemperature() {
        return biomeTemperature;
    }

    @NotNull
    public BandlandsPattern bandlands() {
        return bandlands;
    }
}
