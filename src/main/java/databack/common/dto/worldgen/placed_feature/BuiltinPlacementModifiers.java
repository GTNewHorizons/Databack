package databack.common.dto.worldgen.placed_feature;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.google.gson.JsonDeserializer;

import databack.common.dto.worldgen.block_predicate.IBlockPredicate;
import databack.common.dto.worldgen.height_provider.IHeightProvider;
import databack.common.dto.worldgen.int_provider.IIntProvider;
import databack.common.interop.heightmap.HeightmapType;
import databack.common.serde.DatapackSerialization;
import databack.common.serde.TaggedUnionLoader;
import databack.common.worldgen.feature.FeatureBuildContext;
import databack.common.worldgen.vanilla.standin.VnBlockPos;
import databack.common.worldgen.vanilla.standin.VnDirection;

/// The placement modifiers, built as vanilla's own ([IPlacementModifier#instantiate]).
public class BuiltinPlacementModifiers {

    private static final String PLACEMENT = "placement/";

    public static void init() {
        DatapackSerialization.getBuilder()
            .registerTypeAdapter(IPlacedFeatureRef.class, (JsonDeserializer<IPlacedFeatureRef>) (json, type, ctx) -> {
                if (json.isJsonPrimitive()) {
                    String s = json.getAsString();
                    return s.startsWith("#") ? new PlacedFeatureTagRef(s.substring(1)) : new PlacedFeatureIdRef(s);
                }
                return ctx.deserialize(json, PlacedFeature.class);
            });

        TaggedUnionLoader<IPlacementModifier> placementModifiers = DatapackSerialization
            .createTaggedUnionLoader("builtin/placement_modifiers", IPlacementModifier.class);

        placementModifiers.addVariant("minecraft:block_predicate_filter", BlockPredicateFilterMod.class);
        placementModifiers.addVariant("minecraft:rarity_filter", RarityFilterMod.class);
        placementModifiers.addVariant("minecraft:count", CountMod.class);
        placementModifiers.addVariant("minecraft:count_on_every_layer", CountOnEveryLayerMod.class);
        placementModifiers.addVariant("minecraft:noise_threshold_count", NoiseThresholdCountMod.class);
        placementModifiers.addVariant("minecraft:noise_based_count", NoiseBasedCountMod.class);
        placementModifiers.addVariant("minecraft:environment_scan", EnvironmentScanMod.class);
        placementModifiers.addVariant("minecraft:fixed_placement", FixedPlacementMod.class);
        placementModifiers.addVariant("minecraft:heightmap", HeightmapMod.class);
        placementModifiers.addVariant("minecraft:height_range", HeightRangeMod.class);
        placementModifiers.addVariant("minecraft:in_square", InSquareMod.class);
        placementModifiers.addVariant("minecraft:random_offset", RandomOffsetMod.class);
        placementModifiers.addVariant("minecraft:surface_relative_threshold_filter",
            SurfaceRelativeThresholdFilterMod.class);
        placementModifiers.addVariant("minecraft:surface_water_depth_filter", SurfaceWaterDepthFilterMod.class);
        placementModifiers.addVariant("minecraft:biome", BiomeMod.class);
    }

    private static class BlockPredicateFilterMod implements IPlacementModifier {

        public IBlockPredicate predicate;

        @Nullable
        @Override
        public Object instantiate(@NotNull FeatureBuildContext ctx) {
            Object built = predicate.instantiate(ctx);
            return built == null ? null : ctx.vanilla.call(PLACEMENT + "BlockPredicateFilter", "forPredicate", built);
        }
    }

    private static class RarityFilterMod implements IPlacementModifier {

        public int chance;

        @Override
        public Object instantiate(@NotNull FeatureBuildContext ctx) {
            return ctx.vanilla.call(PLACEMENT + "RarityFilter", "onAverageOnceEvery", chance);
        }
    }

    private static class CountMod implements IPlacementModifier {

        public IIntProvider count;

        @Override
        public Object instantiate(@NotNull FeatureBuildContext ctx) {
            return ctx.vanilla.call(PLACEMENT + "CountPlacement", "of", ctx.intProvider(count));
        }
    }

    private static class CountOnEveryLayerMod implements IPlacementModifier {

        public IIntProvider count;

        @Override
        public Object instantiate(@NotNull FeatureBuildContext ctx) {
            return ctx.vanilla.call(PLACEMENT + "CountOnEveryLayerPlacement", "of", ctx.intProvider(count));
        }
    }

    private static class NoiseThresholdCountMod implements IPlacementModifier {

        public double noise_level;
        public int below_noise;
        public int above_noise;

        @Override
        public Object instantiate(@NotNull FeatureBuildContext ctx) {
            return ctx.vanilla.call(PLACEMENT + "NoiseThresholdCountPlacement", "of", noise_level, below_noise,
                above_noise);
        }
    }

    private static class NoiseBasedCountMod implements IPlacementModifier {

        public int noise_to_count_ratio;
        public double noise_factor;
        public double noise_offset;

        @Override
        public Object instantiate(@NotNull FeatureBuildContext ctx) {
            return ctx.vanilla.call(PLACEMENT + "NoiseBasedCountPlacement", "of", noise_to_count_ratio, noise_factor,
                noise_offset);
        }
    }

    private static class EnvironmentScanMod implements IPlacementModifier {

        public String direction_of_search;
        public int max_steps;
        public IBlockPredicate target_condition;
        @Nullable
        public IBlockPredicate allowed_search_condition;

        @Nullable
        @Override
        public Object instantiate(@NotNull FeatureBuildContext ctx) {
            VnDirection direction = ctx.direction(direction_of_search);
            if (direction == null) return ctx.unsupported("Environment scan direction " + direction_of_search);

            Object target = target_condition.instantiate(ctx);
            if (target == null) return null;
            if (allowed_search_condition == null) {
                return ctx.vanilla.call(PLACEMENT + "EnvironmentScanPlacement", "scanningFor", direction, target,
                    max_steps);
            }

            Object allowed = allowed_search_condition.instantiate(ctx);
            return allowed == null ? null : ctx.vanilla.call(PLACEMENT + "EnvironmentScanPlacement", "scanningFor",
                direction, target, allowed, max_steps);
        }
    }

    private static class FixedPlacementMod implements IPlacementModifier {

        public int[][] positions;

        @Override
        public Object instantiate(@NotNull FeatureBuildContext ctx) {
            VnBlockPos[] list = new VnBlockPos[positions.length];
            for (int i = 0; i < list.length; i++) list[i] = ctx.offset(positions[i]);
            return ctx.vanilla.call(PLACEMENT + "FixedPlacement", "of", (Object) list);
        }
    }

    private static class HeightmapMod implements IPlacementModifier {

        public HeightmapType heightmap;

        @Override
        public Object instantiate(@NotNull FeatureBuildContext ctx) {
            return ctx.vanilla.call(PLACEMENT + "HeightmapPlacement", "onHeightmap", heightmap);
        }
    }

    private static class HeightRangeMod implements IPlacementModifier {

        public IHeightProvider height;

        @Override
        public Object instantiate(@NotNull FeatureBuildContext ctx) {
            return ctx.vanilla.call(PLACEMENT + "HeightRangePlacement", "of", ctx.heightProvider(height));
        }
    }

    private static class InSquareMod implements IPlacementModifier {

        @Override
        public Object instantiate(@NotNull FeatureBuildContext ctx) {
            return ctx.vanilla.call(PLACEMENT + "InSquarePlacement", "spread");
        }
    }

    private static class RandomOffsetMod implements IPlacementModifier {

        public IIntProvider xz_spread;
        public IIntProvider y_spread;

        @Override
        public Object instantiate(@NotNull FeatureBuildContext ctx) {
            return ctx.vanilla.call(PLACEMENT + "RandomOffsetPlacement", "of", ctx.intProvider(xz_spread),
                ctx.intProvider(y_spread));
        }
    }

    private static class SurfaceRelativeThresholdFilterMod implements IPlacementModifier {

        public HeightmapType heightmap;
        public int min_inclusive = Integer.MIN_VALUE;
        public int max_inclusive = Integer.MAX_VALUE;

        @Override
        public Object instantiate(@NotNull FeatureBuildContext ctx) {
            return ctx.vanilla.call(PLACEMENT + "SurfaceRelativeThresholdFilter", "of", heightmap, min_inclusive,
                max_inclusive);
        }
    }

    private static class SurfaceWaterDepthFilterMod implements IPlacementModifier {

        public int max_water_depth;

        @Override
        public Object instantiate(@NotNull FeatureBuildContext ctx) {
            return ctx.vanilla.call(PLACEMENT + "SurfaceWaterDepthFilter", "forMaxDepth", max_water_depth);
        }
    }

    private static class BiomeMod implements IPlacementModifier {

        @Override
        public Object instantiate(@NotNull FeatureBuildContext ctx) {
            return ctx.vanilla.call(PLACEMENT + "BiomeFilter", "biome");
        }
    }
}
