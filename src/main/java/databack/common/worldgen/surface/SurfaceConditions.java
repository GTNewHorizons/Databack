package databack.common.worldgen.surface;

import java.util.Set;
import java.util.function.ToDoubleFunction;

import org.jetbrains.annotations.NotNull;

import databack.common.worldgen.density.SeededNoise;

/// The surface condition types (docs/worldgen/surface-rules.md section 3).
public final class SurfaceConditions {

    /// Below this biome temperature, `temperature` holds.
    static final float COLD = 0.15F;

    private SurfaceConditions() {}

    @NotNull
    public static SurfaceCondition biome(@NotNull Set<String> biomes) {
        return ctx -> biomes.contains(ctx.biome());
    }

    /// The noise is 2D, so its value is computed once per column.
    @NotNull
    public static SurfaceCondition noiseThreshold(@NotNull SeededNoise noise, double min, double max) {
        return new SurfaceCondition() {

            @Override
            public boolean test(@NotNull SurfaceContext ctx) {
                Object cached = ctx.columnValue(this);
                if (cached == null) {
                    cached = noise.getValue(ctx.x(), 0, ctx.z());
                    ctx.setColumnValue(this, cached);
                }
                double value = (Double) cached;
                return value >= min && value <= max;
            }
        };
    }

    /// **Approximation:** the random draw is Databack's own.
    @NotNull
    public static SurfaceCondition verticalGradient(@NotNull String randomName, int trueAtAndBelow, int falseAtAndAbove) {
        return ctx -> {
            int y = ctx.y();
            if (y <= trueAtAndBelow) return true;
            if (y >= falseAtAndAbove) return false;
            double chance = (double) (falseAtAndAbove - y) / (falseAtAndAbove - trueAtAndBelow);
            return SurfaceRandom.at(ctx.seed, randomName, ctx.x(), y, ctx.z()) < chance;
        };
    }

    @NotNull
    public static SurfaceCondition yAbove(int anchor, int surfaceDepthMultiplier, boolean addStoneDepth) {
        return ctx -> ctx.y() + (addStoneDepth ? ctx.floorDepth() : 0)
            >= anchor + surfaceDepthMultiplier * ctx.surfaceDepth();
    }

    @NotNull
    public static SurfaceCondition water(int offset, int surfaceDepthMultiplier, boolean addStoneDepth) {
        return ctx -> {
            if (ctx.fluidLevel() == SurfaceContext.NO_FLUID) return true;
            return ctx.y() + (addStoneDepth ? ctx.floorDepth() : 0)
                >= ctx.fluidLevel() + offset + surfaceDepthMultiplier * ctx.surfaceDepth();
        };
    }

    @NotNull
    public static SurfaceCondition stoneDepth(
        int offset, boolean addSurfaceDepth, int secondaryDepthRange, boolean ceiling
    ) {
        return ctx -> {
            int depth = ceiling ? ctx.ceilingDepth() : ctx.floorDepth();
            int limit = 1 + offset + (addSurfaceDepth ? ctx.surfaceDepth() : 0);
            if (secondaryDepthRange != 0) limit += ctx.secondaryDepth(secondaryDepthRange);
            return depth <= limit;
        };
    }

    /// **Approximation:** the biome's temperature, without height cooling or the frozen modifier.
    @NotNull
    public static SurfaceCondition temperature(@NotNull ToDoubleFunction<String> biomeTemperature) {
        return ctx -> {
            String biome = ctx.biome();
            return biome != null && biomeTemperature.applyAsDouble(biome) < COLD;
        };
    }

    @NotNull
    public static SurfaceCondition steep() {
        return SurfaceContext::steep;
    }

    @NotNull
    public static SurfaceCondition hole() {
        return ctx -> ctx.surfaceDepth() <= 0;
    }

    /// **Approximation:** the threshold is not established exactly (docs/worldgen/surface-rules.md section 3).
    @NotNull
    public static SurfaceCondition abovePreliminarySurface() {
        return ctx -> ctx.y() >= ctx.preliminarySurface() + ctx.surfaceDepth() - 8;
    }

    @NotNull
    public static SurfaceCondition not(@NotNull SurfaceCondition inner) {
        return ctx -> !inner.test(ctx);
    }
}
