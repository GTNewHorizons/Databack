package databack.common.worldgen.feature.standin;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import databack.common.worldgen.biome.BiomeTemperature;
import databack.common.worldgen.vanilla.standin.VnBlockPos;
import databack.common.worldgen.vanilla.standin.VnDirection;
import databack.common.worldgen.vanilla.standin.VnPlane;

/// Stand-in for a biome, as features see it: its temperature, and where it freezes water and lays snow.
///
/// **Approximation:** block light is taken as 0, so lava and glowstone don't keep snow and ice away. 1.7 lights
/// blocks after population.
@ApiStatus.Internal
public final class FtBiome {

    /// The noise flower and grass counts follow: seeded 2345 (`feature/probes/info_noise.tsv`).
    public static final FtInfoNoise BIOME_INFO_NOISE = new FtInfoNoise(2345L);

    @NotNull
    private final BiomeTemperature temperature;

    public FtBiome(@NotNull BiomeTemperature temperature) {
        this.temperature = temperature;
    }

    public float getBaseTemperature() {
        return temperature.base();
    }

    /// Sea level doesn't affect it.
    public boolean coldEnoughToSnow(@NotNull VnBlockPos pos, int seaLevel) {
        return temperature.coldEnoughToSnow(pos.getX(), pos.getY(), pos.getZ());
    }

    public boolean warmEnoughToRain(@NotNull VnBlockPos pos, int seaLevel) {
        return !coldEnoughToSnow(pos, seaLevel);
    }

    public boolean shouldFreeze(@NotNull FtLevel level, @NotNull VnBlockPos pos) {
        return shouldFreeze(level, pos, true);
    }

    /// Whether still water at `pos` freezes; with `mustBeAtEdge`, only where a side isn't water.
    public boolean shouldFreeze(@NotNull FtLevel level, @NotNull VnBlockPos pos, boolean mustBeAtEdge) {
        if (!canFormAt(level, pos)) return false;

        FtBlockState state = level.getBlockState(pos);
        if (!state.is(FtBlocks.WATER) || !state.getFluidState().isSourceOfType(FtFluids.WATER)) return false;
        if (!mustBeAtEdge) return true;

        for (VnDirection side : VnPlane.HORIZONTAL) {
            if (!level.isFluidAtPosition(pos.relative(side), fluid -> fluid.is(FtFluidTags.WATER))) return true;
        }
        return false;
    }

    /// Whether a snow layer forms at `pos`.
    public boolean shouldSnow(@NotNull FtLevel level, @NotNull VnBlockPos pos) {
        return canFormAt(level, pos) && level.getBlockState(pos).isAir()
            && FtBlocks.SNOW.defaultBlockState().canSurvive(level, pos);
    }

    private boolean canFormAt(FtLevel level, VnBlockPos pos) {
        return coldEnoughToSnow(pos, 0) && level.isInsideBuildHeight(pos);
    }
}
