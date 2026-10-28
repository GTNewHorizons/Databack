package databack.common.dto.worldgen.surface_rule;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import org.jetbrains.annotations.NotNull;

import com.gtnewhorizon.gtnhlib.util.data.ImmutableBlockMeta;

import databack.common.dto.BlockStateDTO;
import databack.common.dto.worldgen.height_provider.VerticalAnchor;
import databack.common.serde.DatapackSerialization;
import databack.common.serde.TaggedUnionLoader;
import databack.common.worldgen.surface.BandlandsPattern;
import databack.common.worldgen.surface.SurfaceBuildContext;
import databack.common.worldgen.surface.SurfaceCondition;
import databack.common.worldgen.surface.SurfaceConditions;
import databack.common.worldgen.surface.SurfaceRule;
import databack.common.worldgen.surface.SurfaceRules;

/// The 26.1 surface rule and condition types (docs/worldgen/surface-rules.md).
@SuppressWarnings({ "unused", "NotNullFieldNotInitialized" })
public class BuiltinSurfaceRules {

    public static void init() {
        TaggedUnionLoader<ISurfaceRuleFactory> rules = DatapackSerialization.createTaggedUnionLoader(
            "worldgen/surface_rule", ISurfaceRuleFactory.class);
        rules.addVariant("minecraft:block", BlockRule.class);
        rules.addVariant("minecraft:sequence", SequenceRule.class);
        rules.addVariant("minecraft:condition", ConditionRule.class);
        rules.addVariant("minecraft:bandlands", BandlandsRule.class);

        TaggedUnionLoader<ISurfaceConditionFactory> conditions = DatapackSerialization.createTaggedUnionLoader(
            "worldgen/surface_condition", ISurfaceConditionFactory.class);
        conditions.addVariant("minecraft:biome", BiomeCondition.class);
        conditions.addVariant("minecraft:noise_threshold", NoiseThresholdCondition.class);
        conditions.addVariant("minecraft:vertical_gradient", VerticalGradientCondition.class);
        conditions.addVariant("minecraft:y_above", YAboveCondition.class);
        conditions.addVariant("minecraft:water", WaterCondition.class);
        conditions.addVariant("minecraft:stone_depth", StoneDepthCondition.class);
        conditions.addVariant("minecraft:temperature", TemperatureCondition.class);
        conditions.addVariant("minecraft:steep", SteepCondition.class);
        conditions.addVariant("minecraft:hole", HoleCondition.class);
        conditions.addVariant("minecraft:above_preliminary_surface", AbovePreliminarySurfaceCondition.class);
        conditions.addVariant("minecraft:not", NotCondition.class);
    }

    // -- Rules --

    public static class BlockRule implements ISurfaceRuleFactory {

        public BlockStateDTO result_state;

        @Override
        public @NotNull SurfaceRule instantiate(@NotNull SurfaceBuildContext ctx) {
            return SurfaceRules.block(ctx.block(result_state));
        }
    }

    public static class SequenceRule implements ISurfaceRuleFactory {

        public List<ISurfaceRuleFactory> sequence;

        @Override
        public @NotNull SurfaceRule instantiate(@NotNull SurfaceBuildContext ctx) {
            List<SurfaceRule> built = new ArrayList<>(sequence.size());
            for (ISurfaceRuleFactory rule : sequence) built.add(rule.instantiate(ctx));
            return SurfaceRules.sequence(built);
        }
    }

    public static class ConditionRule implements ISurfaceRuleFactory {

        public ISurfaceConditionFactory if_true;
        public ISurfaceRuleFactory then_run;

        @Override
        public @NotNull SurfaceRule instantiate(@NotNull SurfaceBuildContext ctx) {
            return SurfaceRules.condition(if_true.instantiate(ctx), then_run.instantiate(ctx));
        }
    }

    public static class BandlandsRule implements ISurfaceRuleFactory {

        /// Indexed by [BandlandsPattern]'s colours.
        private static final String[] COLOURS = { "minecraft:terracotta", "minecraft:orange_terracotta",
            "minecraft:brown_terracotta", "minecraft:yellow_terracotta", "minecraft:light_gray_terracotta",
            "minecraft:white_terracotta", "minecraft:red_terracotta" };

        @Override
        public @NotNull SurfaceRule instantiate(@NotNull SurfaceBuildContext ctx) {
            ImmutableBlockMeta[] blocks = new ImmutableBlockMeta[COLOURS.length];
            for (int i = 0; i < COLOURS.length; i++) blocks[i] = ctx.block(COLOURS[i]);
            return SurfaceRules.bandlands(ctx.bandlands(), ctx.noise("minecraft:clay_bands_offset"), blocks);
        }
    }

    // -- Conditions --

    public static class BiomeCondition implements ISurfaceConditionFactory {

        public List<String> biome_is;

        @Override
        public @NotNull SurfaceCondition instantiate(@NotNull SurfaceBuildContext ctx) {
            return SurfaceConditions.biome(new HashSet<>(biome_is));
        }
    }

    public static class NoiseThresholdCondition implements ISurfaceConditionFactory {

        public String noise;
        public double min_threshold;
        public double max_threshold;

        @Override
        public @NotNull SurfaceCondition instantiate(@NotNull SurfaceBuildContext ctx) {
            return SurfaceConditions.noiseThreshold(ctx.noise(noise), min_threshold, max_threshold);
        }
    }

    public static class VerticalGradientCondition implements ISurfaceConditionFactory {

        public String random_name;
        public VerticalAnchor true_at_and_below;
        public VerticalAnchor false_at_and_above;

        @Override
        public @NotNull SurfaceCondition instantiate(@NotNull SurfaceBuildContext ctx) {
            return SurfaceConditions.verticalGradient(random_name, ctx.resolve(true_at_and_below),
                ctx.resolve(false_at_and_above));
        }
    }

    public static class YAboveCondition implements ISurfaceConditionFactory {

        public VerticalAnchor anchor;
        public int surface_depth_multiplier;
        public boolean add_stone_depth;

        @Override
        public @NotNull SurfaceCondition instantiate(@NotNull SurfaceBuildContext ctx) {
            return SurfaceConditions.yAbove(ctx.resolve(anchor), surface_depth_multiplier, add_stone_depth);
        }
    }

    public static class WaterCondition implements ISurfaceConditionFactory {

        public int offset;
        public int surface_depth_multiplier;
        public boolean add_stone_depth;

        @Override
        public @NotNull SurfaceCondition instantiate(@NotNull SurfaceBuildContext ctx) {
            return SurfaceConditions.water(offset, surface_depth_multiplier, add_stone_depth);
        }
    }

    public static class StoneDepthCondition implements ISurfaceConditionFactory {

        public enum SurfaceType {
            floor,
            ceiling
        }

        public int offset;
        public boolean add_surface_depth;
        public int secondary_depth_range;
        public SurfaceType surface_type;

        @Override
        public @NotNull SurfaceCondition instantiate(@NotNull SurfaceBuildContext ctx) {
            return SurfaceConditions.stoneDepth(offset, add_surface_depth, secondary_depth_range,
                surface_type == SurfaceType.ceiling);
        }
    }

    public static class TemperatureCondition implements ISurfaceConditionFactory {

        @Override
        public @NotNull SurfaceCondition instantiate(@NotNull SurfaceBuildContext ctx) {
            return SurfaceConditions.temperature(ctx.biomeTemperature());
        }
    }

    public static class SteepCondition implements ISurfaceConditionFactory {

        @Override
        public @NotNull SurfaceCondition instantiate(@NotNull SurfaceBuildContext ctx) {
            return SurfaceConditions.steep();
        }
    }

    public static class HoleCondition implements ISurfaceConditionFactory {

        @Override
        public @NotNull SurfaceCondition instantiate(@NotNull SurfaceBuildContext ctx) {
            return SurfaceConditions.hole();
        }
    }

    public static class AbovePreliminarySurfaceCondition implements ISurfaceConditionFactory {

        @Override
        public @NotNull SurfaceCondition instantiate(@NotNull SurfaceBuildContext ctx) {
            return SurfaceConditions.abovePreliminarySurface();
        }
    }

    public static class NotCondition implements ISurfaceConditionFactory {

        public ISurfaceConditionFactory invert;

        @Override
        public @NotNull SurfaceCondition instantiate(@NotNull SurfaceBuildContext ctx) {
            return SurfaceConditions.not(invert.instantiate(ctx));
        }
    }
}
