package databack.common.dto.worldgen.configured_feature.tree;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import databack.common.annotation.RangeFloat;
import databack.common.dto.worldgen.BlockWhitelist;
import databack.common.dto.worldgen.block_state_provider.IBlockStateProvider;
import databack.common.dto.worldgen.int_provider.IIntProvider;
import databack.common.serde.DatapackSerialization;
import databack.common.serde.TaggedUnionLoader;
import databack.common.worldgen.feature.FeatureBuildContext;
import databack.common.worldgen.feature.standin.FtIntProvider;
import databack.common.worldgen.vanilla.standin.VnDirection;

public class BuiltinTreeComponents {

    public static void init() {
        TaggedUnionLoader<ITreeTrunkPlacer> trunkPlacer = DatapackSerialization
            .createTaggedUnionLoader("worldgen/trunk_placer", ITreeTrunkPlacer.class);
        trunkPlacer.setUnknown(type -> new ITreeTrunkPlacer() {});
        trunkPlacer.addVariant("minecraft:straight_trunk_placer", StraightTrunkPlacer.class);
        trunkPlacer.addVariant("minecraft:forking_trunk_placer", ForkingTrunkPlacer.class);
        trunkPlacer.addVariant("minecraft:giant_trunk_placer", GiantTrunkPlacer.class);
        trunkPlacer.addVariant("minecraft:mega_jungle_trunk_placer", MegaJungleTrunkPlacer.class);
        trunkPlacer.addVariant("minecraft:dark_oak_trunk_placer", DarkOakTrunkPlacer.class);
        trunkPlacer.addVariant("minecraft:fancy_trunk_placer", FancyTrunkPlacer.class);
        trunkPlacer.addVariant("minecraft:bending_trunk_placer", BendingTrunkPlacer.class);
        trunkPlacer.addVariant("minecraft:cherry_trunk_placer", CherryTrunkPlacer.class);
        trunkPlacer.addVariant("minecraft:upwards_branching_trunk_placer", UpwardsBranchingTrunkPlacer.class);
        TaggedUnionLoader<ITreeFoliagePlacer> foliagePlacer = DatapackSerialization
            .createTaggedUnionLoader("worldgen/foliage_placer", ITreeFoliagePlacer.class);
        foliagePlacer.setUnknown(type -> new ITreeFoliagePlacer() {});
        foliagePlacer.addVariant("minecraft:blob_foliage_placer", BlobFoliagePlacer.class);
        foliagePlacer.addVariant("minecraft:bush_foliage_placer", BushFoliagePlacer.class);
        foliagePlacer.addVariant("minecraft:fancy_foliage_placer", FancyFoliagePlacer.class);
        foliagePlacer.addVariant("minecraft:jungle_foliage_placer", JungleFoliagePlacer.class);
        foliagePlacer.addVariant("minecraft:acacia_foliage_placer", AcaciaFoliagePlacer.class);
        foliagePlacer.addVariant("minecraft:dark_oak_foliage_placer", DarkOakFoliagePlacer.class);
        foliagePlacer.addVariant("minecraft:spruce_foliage_placer", SpruceFoliagePlacer.class);
        foliagePlacer.addVariant("minecraft:pine_foliage_placer", PineFoliagePlacer.class);
        foliagePlacer.addVariant("minecraft:mega_pine_foliage_placer", MegaPineFoliagePlacer.class);
        foliagePlacer.addVariant("minecraft:cherry_foliage_placer", CherryFoliagePlacer.class);
        foliagePlacer.addVariant("minecraft:random_spread_foliage_placer", RandomSpreadFoliagePlacer.class);
        TaggedUnionLoader<ITreeDecorator> treeDecorator = DatapackSerialization
            .createTaggedUnionLoader("worldgen/tree_decorator", ITreeDecorator.class);
        treeDecorator.setUnknown(type -> new ITreeDecorator() {});
        treeDecorator.addVariant("minecraft:trunk_vine", TrunkVineDecorator.class);
        treeDecorator.addVariant("minecraft:leave_vine", LeaveVineDecorator.class);
        treeDecorator.addVariant("minecraft:cocoa", CocoaDecorator.class);
        treeDecorator.addVariant("minecraft:beehive", BeehiveDecorator.class);
        treeDecorator.addVariant("minecraft:alter_ground", AlterGroundDecorator.class);
        treeDecorator.addVariant("minecraft:attached_to_leaves", AttachedToLeavesDecorator.class);
        treeDecorator.addVariant("minecraft:place_on_ground", PlaceOnGroundDecorator.class);
        treeDecorator.addVariant("minecraft:creaking_heart", CreakingHeartDecorator.class);
        treeDecorator.addVariant("minecraft:pale_moss", PaleMossDecorator.class);
        treeDecorator.addVariant("minecraft:attached_to_logs", AttachedToLogsDecorator.class);
        TaggedUnionLoader<IFeatureSize> featureSize = DatapackSerialization
            .createTaggedUnionLoader("worldgen/feature_size", IFeatureSize.class);
        featureSize.setUnknown(type -> new IFeatureSize() {});
        featureSize.addVariant("minecraft:two_layers_feature_size", TwoLayersFeatureSize.class);
        featureSize.addVariant("minecraft:three_layers_feature_size", ThreeLayersFeatureSize.class);
        TaggedUnionLoader<IRootPlacer> rootPlacer = DatapackSerialization
            .createTaggedUnionLoader("worldgen/root_placer", IRootPlacer.class);
        rootPlacer.setUnknown(type -> new IRootPlacer() {});

        rootPlacer.addVariant("minecraft:mangrove_root_placer", MangroveRootPlacer.class);
    }

    private static final String TRUNKS = "feature/trunkplacers/", FOLIAGE = "feature/foliageplacers/",
        DECORATORS = "feature/treedecorators/", SIZES = "feature/featuresize/", ROOTS = "feature/rootplacers/";

    /// The directions named, or null if one is not a direction.
    @Nullable
    private static List<VnDirection> directions(FeatureBuildContext ctx, String[] names) {
        List<VnDirection> directions = new ArrayList<>(names.length);
        for (String name : names) {
            VnDirection direction = ctx.direction(name);
            if (direction == null) return ctx.unsupported("Direction " + name);
            directions.add(direction);
        }
        return directions;
    }

    // ---- Trunk placers ---------------------------------------------------------------------------------------

    /// The trunk placers with only the shared heights; argument orders checked by `TreeConstructionTest`.
    public abstract static class TrunkPlacerBase implements ITreeTrunkPlacer {
        public int base_height;
        public int height_rand_a;
        public int height_rand_b;

        /// The vanilla class, in the trunk placer package.
        abstract String vanillaClass();

        @Override
        public Object instantiate(@NotNull FeatureBuildContext ctx) {
            return ctx.vanilla.construct(TRUNKS + vanillaClass(), base_height, height_rand_a, height_rand_b);
        }
    }
    public static class StraightTrunkPlacer extends TrunkPlacerBase {
        @Override
        String vanillaClass() {
            return "StraightTrunkPlacer";
        }
    }
    public static class ForkingTrunkPlacer extends TrunkPlacerBase {
        @Override
        String vanillaClass() {
            return "ForkingTrunkPlacer";
        }
    }
    public static class GiantTrunkPlacer extends TrunkPlacerBase {
        @Override
        String vanillaClass() {
            return "GiantTrunkPlacer";
        }
    }
    public static class MegaJungleTrunkPlacer extends TrunkPlacerBase {
        @Override
        String vanillaClass() {
            return "MegaJungleTrunkPlacer";
        }
    }
    public static class DarkOakTrunkPlacer extends TrunkPlacerBase {
        @Override
        String vanillaClass() {
            return "DarkOakTrunkPlacer";
        }
    }
    public static class FancyTrunkPlacer extends TrunkPlacerBase {
        @Override
        String vanillaClass() {
            return "FancyTrunkPlacer";
        }
    }
    public static class BendingTrunkPlacer extends TrunkPlacerBase {
        public IIntProvider bend_length;
        public int min_height_for_leaves = 1;

        @Override
        String vanillaClass() {
            return "BendingTrunkPlacer";
        }

        @Override
        public Object instantiate(@NotNull FeatureBuildContext ctx) {
            return ctx.vanilla.construct(TRUNKS + vanillaClass(), base_height, height_rand_a, height_rand_b,
                min_height_for_leaves, ctx.intProvider(bend_length));
        }
    }
    public static class CherryTrunkPlacer extends TrunkPlacerBase {
        public IIntProvider branch_count;
        public IIntProvider branch_horizontal_length;
        public UniformInt branch_start_offset_from_top;
        public IIntProvider branch_end_offset_from_top;

        @Override
        String vanillaClass() {
            return "CherryTrunkPlacer";
        }

        @Override
        public Object instantiate(@NotNull FeatureBuildContext ctx) {
            return ctx.vanilla.construct(TRUNKS + vanillaClass(), base_height, height_rand_a, height_rand_b,
                ctx.intProvider(branch_count), ctx.intProvider(branch_horizontal_length),
                FtIntProvider.of(branch_start_offset_from_top.min_inclusive,
                    branch_start_offset_from_top.max_inclusive),
                ctx.intProvider(branch_end_offset_from_top));
        }
    }
    /** Plain uniform int range — no {@code type} discriminator field. */
    public static class UniformInt {
        public int min_inclusive;
        public int max_inclusive;
    }
    public static class UpwardsBranchingTrunkPlacer extends TrunkPlacerBase {
        public IIntProvider extra_branch_steps;
        public float place_branch_per_log_probability;
        public IIntProvider extra_branch_length;
        public BlockWhitelist can_grow_through;

        @Override
        String vanillaClass() {
            return "UpwardsBranchingTrunkPlacer";
        }

        @Override
        public Object instantiate(@NotNull FeatureBuildContext ctx) {
            return ctx.vanilla.construct(TRUNKS + vanillaClass(), base_height, height_rand_a, height_rand_b,
                ctx.intProvider(extra_branch_steps), place_branch_per_log_probability,
                ctx.intProvider(extra_branch_length), ctx.blocks(can_grow_through.entries));
        }
    }

    // ---- Foliage placers -------------------------------------------------------------------------------------

    /// The foliage placers with only a radius and offset; argument orders checked by `TreeConstructionTest`.
    public abstract static class FoliagePlacerBase implements ITreeFoliagePlacer {
        public IIntProvider radius;
        public IIntProvider offset;

        /// The vanilla class, in the foliage placer package.
        abstract String vanillaClass();

        @Override
        public Object instantiate(@NotNull FeatureBuildContext ctx) {
            return ctx.vanilla.construct(FOLIAGE + vanillaClass(), ctx.intProvider(radius), ctx.intProvider(offset));
        }
    }
    public static class AcaciaFoliagePlacer extends FoliagePlacerBase {
        @Override
        String vanillaClass() {
            return "AcaciaFoliagePlacer";
        }
    }
    public static class DarkOakFoliagePlacer extends FoliagePlacerBase {
        @Override
        String vanillaClass() {
            return "DarkOakFoliagePlacer";
        }
    }
    /// The foliage placers with a fixed height.
    public abstract static class HeightFoliagePlacer extends FoliagePlacerBase {
        public int height;

        @Override
        public Object instantiate(@NotNull FeatureBuildContext ctx) {
            return ctx.vanilla.construct(FOLIAGE + vanillaClass(), ctx.intProvider(radius), ctx.intProvider(offset),
                height);
        }
    }
    public static class BlobFoliagePlacer extends HeightFoliagePlacer {
        @Override
        String vanillaClass() {
            return "BlobFoliagePlacer";
        }
    }
    public static class BushFoliagePlacer extends HeightFoliagePlacer {
        @Override
        String vanillaClass() {
            return "BushFoliagePlacer";
        }
    }
    public static class FancyFoliagePlacer extends HeightFoliagePlacer {
        @Override
        String vanillaClass() {
            return "FancyFoliagePlacer";
        }
    }
    public static class JungleFoliagePlacer extends HeightFoliagePlacer {
        @Override
        String vanillaClass() {
            return "MegaJungleFoliagePlacer";
        }
    }
    /// The foliage placers with one more int provider, named differently by each.
    public abstract static class ProviderFoliagePlacer extends FoliagePlacerBase {
        /// The extra provider.
        abstract IIntProvider extra();

        @Override
        public Object instantiate(@NotNull FeatureBuildContext ctx) {
            return ctx.vanilla.construct(FOLIAGE + vanillaClass(), ctx.intProvider(radius), ctx.intProvider(offset),
                ctx.intProvider(extra()));
        }
    }
    public static class SpruceFoliagePlacer extends ProviderFoliagePlacer {
        public IIntProvider trunk_height;

        @Override
        String vanillaClass() {
            return "SpruceFoliagePlacer";
        }

        @Override
        IIntProvider extra() {
            return trunk_height;
        }
    }
    public static class PineFoliagePlacer extends ProviderFoliagePlacer {
        public IIntProvider height;

        @Override
        String vanillaClass() {
            return "PineFoliagePlacer";
        }

        @Override
        IIntProvider extra() {
            return height;
        }
    }
    public static class MegaPineFoliagePlacer extends ProviderFoliagePlacer {
        public IIntProvider crown_height;

        @Override
        String vanillaClass() {
            return "MegaPineFoliagePlacer";
        }

        @Override
        IIntProvider extra() {
            return crown_height;
        }
    }
    public static class CherryFoliagePlacer extends FoliagePlacerBase {
        public IIntProvider height;
        @RangeFloat(min = 0, max = 1) public float wide_bottom_layer_hole_chance;
        @RangeFloat(min = 0, max = 1) public float corner_hole_chance;
        @RangeFloat(min = 0, max = 1) public float hanging_leaves_chance;
        @RangeFloat(min = 0, max = 1) public float hanging_leaves_extension_chance;

        @Override
        String vanillaClass() {
            return "CherryFoliagePlacer";
        }

        @Override
        public Object instantiate(@NotNull FeatureBuildContext ctx) {
            return ctx.vanilla.construct(FOLIAGE + vanillaClass(), ctx.intProvider(radius), ctx.intProvider(offset),
                ctx.intProvider(height), wide_bottom_layer_hole_chance, corner_hole_chance, hanging_leaves_chance,
                hanging_leaves_extension_chance);
        }
    }
    public static class RandomSpreadFoliagePlacer extends FoliagePlacerBase {
        public IIntProvider foliage_height;
        public int leaf_placement_attempts;

        @Override
        String vanillaClass() {
            return "RandomSpreadFoliagePlacer";
        }

        @Override
        public Object instantiate(@NotNull FeatureBuildContext ctx) {
            return ctx.vanilla.construct(FOLIAGE + vanillaClass(), ctx.intProvider(radius), ctx.intProvider(offset),
                ctx.intProvider(foliage_height), leaf_placement_attempts);
        }
    }

    // ---- Tree decorators -------------------------------------------------------------------------------------

    public static class TrunkVineDecorator implements ITreeDecorator {
        @Override
        public Object instantiate(@NotNull FeatureBuildContext ctx) {
            return ctx.vanilla.construct(DECORATORS + "TrunkVineDecorator");
        }
    }
    /// The decorators with only a probability.
    public abstract static class ProbabilityDecorator implements ITreeDecorator {
        @RangeFloat(min = 0, max = 1) public float probability;

        /// The vanilla class, in the tree decorator package.
        abstract String vanillaClass();

        @Override
        public Object instantiate(@NotNull FeatureBuildContext ctx) {
            return ctx.vanilla.construct(DECORATORS + vanillaClass(), probability);
        }
    }
    public static class LeaveVineDecorator extends ProbabilityDecorator {
        @Override
        String vanillaClass() {
            return "LeaveVineDecorator";
        }
    }
    public static class CocoaDecorator extends ProbabilityDecorator {
        @Override
        String vanillaClass() {
            return "CocoaDecorator";
        }
    }
    /// Bee nests are placed without bees ([databack.common.worldgen.feature.standin.FtLevel#getBlockEntity]).
    public static class BeehiveDecorator extends ProbabilityDecorator {
        @Override
        String vanillaClass() {
            return "BeehiveDecorator";
        }
    }
    /// Unsupported when placed: the creaking heart has no 1.7 block.
    public static class CreakingHeartDecorator extends ProbabilityDecorator {
        @Override
        String vanillaClass() {
            return "CreakingHeartDecorator";
        }
    }
    /// Unsupported when placed: pale moss has no 1.7 block.
    public static class PaleMossDecorator implements ITreeDecorator {
        @RangeFloat(min = 0, max = 1) public float leaves_probability;
        @RangeFloat(min = 0, max = 1) public float trunk_probability;
        @RangeFloat(min = 0, max = 1) public float ground_probability;

        @Override
        public Object instantiate(@NotNull FeatureBuildContext ctx) {
            return ctx.vanilla.construct(DECORATORS + "PaleMossDecorator", leaves_probability, trunk_probability,
                ground_probability);
        }
    }
    public static class AlterGroundDecorator implements ITreeDecorator {
        public IBlockStateProvider provider;

        @Nullable
        @Override
        public Object instantiate(@NotNull FeatureBuildContext ctx) {
            Object built = provider.instantiate(ctx);
            return built == null ? null : ctx.vanilla.construct(DECORATORS + "AlterGroundDecorator", built);
        }
    }
    public static class PlaceOnGroundDecorator implements ITreeDecorator {
        public IBlockStateProvider block_state_provider;
        public int tries = 128;
        public int radius = 2;
        public int height = 1;

        @Nullable
        @Override
        public Object instantiate(@NotNull FeatureBuildContext ctx) {
            Object built = block_state_provider.instantiate(ctx);
            return built == null ? null : ctx.vanilla.construct(DECORATORS + "PlaceOnGroundDecorator", tries, radius,
                height, built);
        }
    }
    public static class AttachedToLeavesDecorator implements ITreeDecorator {
        @RangeFloat(min = 0, max = 1) public float probability;
        public int exclusion_radius_xz;
        public int exclusion_radius_y;
        public int required_empty_blocks;
        public IBlockStateProvider block_provider;
        public String[] directions;

        @Nullable
        @Override
        public Object instantiate(@NotNull FeatureBuildContext ctx) {
            Object built = block_provider.instantiate(ctx);
            List<VnDirection> sides = directions(ctx, directions);
            if (built == null || sides == null) return null;
            return ctx.vanilla.construct(DECORATORS + "AttachedToLeavesDecorator", probability, exclusion_radius_xz,
                exclusion_radius_y, built, required_empty_blocks, sides);
        }
    }

    /// Decorates fallen trees' logs.
    public static class AttachedToLogsDecorator implements ITreeDecorator {
        @RangeFloat(min = 0, max = 1) public float probability;
        public IBlockStateProvider block_provider;
        public String[] directions;

        @Nullable
        @Override
        public Object instantiate(@NotNull FeatureBuildContext ctx) {
            Object built = block_provider.instantiate(ctx);
            List<VnDirection> sides = directions(ctx, directions);
            if (built == null || sides == null) return null;
            return ctx.vanilla.construct(DECORATORS + "AttachedToLogsDecorator", probability, built, sides);
        }
    }

    /// Each of `decorators` built, or null if one cannot be.
    @Nullable
    public static List<Object> decorators(@NotNull FeatureBuildContext ctx, @NotNull List<ITreeDecorator> decorators) {
        List<Object> built = new ArrayList<>(decorators.size());
        for (ITreeDecorator decorator : decorators) {
            Object one = decorator.instantiate(ctx);
            if (one == null) return null;
            built.add(one);
        }
        return built;
    }

    // ---- Feature sizes ---------------------------------------------------------------------------------------

    public static class TwoLayersFeatureSize implements IFeatureSize {
        public int limit = 1;
        public int lower_size = 0;
        public int upper_size = 1;
        @Nullable public Integer min_clipped_height;

        @Override
        public Object instantiate(@NotNull FeatureBuildContext ctx) {
            return ctx.vanilla.construct(SIZES + "TwoLayersFeatureSize", limit, lower_size, upper_size,
                optional(min_clipped_height));
        }
    }
    public static class ThreeLayersFeatureSize implements IFeatureSize {
        public int limit = 1;
        public int upper_limit = 1;
        public int lower_size = 0;
        public int middle_size = 1;
        public int upper_size = 1;
        @Nullable public Integer min_clipped_height;

        @Override
        public Object instantiate(@NotNull FeatureBuildContext ctx) {
            return ctx.vanilla.construct(SIZES + "ThreeLayersFeatureSize", limit, upper_limit, lower_size,
                middle_size, upper_size, optional(min_clipped_height));
        }
    }

    private static OptionalInt optional(@Nullable Integer value) {
        return value == null ? OptionalInt.empty() : OptionalInt.of(value);
    }

    // ---- Root placers ----------------------------------------------------------------------------------------

    public static class MangroveRootPlacer implements IRootPlacer {
        public IIntProvider trunk_offset_y;
        public IBlockStateProvider root_provider;
        @Nullable public AboveRootPlacement above_root_placement;
        public MangroveRootPlacement mangrove_root_placement;

        @Nullable
        @Override
        public Object instantiate(@NotNull FeatureBuildContext ctx) {
            Object provider = root_provider.instantiate(ctx);
            Object placement = mangrove_root_placement.instantiate(ctx);
            Object above = above_root_placement == null ? null : above_root_placement.instantiate(ctx);
            if (provider == null || placement == null || above_root_placement != null && above == null) return null;
            return ctx.vanilla.construct(ROOTS + "MangroveRootPlacer", ctx.intProvider(trunk_offset_y), provider,
                Optional.ofNullable(above), placement);
        }
    }
    public static class AboveRootPlacement {
        public IBlockStateProvider above_root_provider;
        public float above_root_placement_chance;

        @Nullable
        Object instantiate(FeatureBuildContext ctx) {
            Object provider = above_root_provider.instantiate(ctx);
            return provider == null ? null : ctx.vanilla.construct(ROOTS + "AboveRootPlacement", provider,
                above_root_placement_chance);
        }
    }
    public static class MangroveRootPlacement {
        public BlockWhitelist can_grow_through;
        public BlockWhitelist muddy_roots_in;
        public IBlockStateProvider muddy_roots_provider;
        public int max_root_width;
        public int max_root_length;
        public float random_skew_chance;

        @Nullable
        Object instantiate(FeatureBuildContext ctx) {
            Object provider = muddy_roots_provider.instantiate(ctx);
            return provider == null ? null : ctx.vanilla.construct(ROOTS + "MangroveRootPlacement",
                ctx.blocks(can_grow_through.entries), ctx.blocks(muddy_roots_in.entries), provider, max_root_width,
                max_root_length, random_skew_chance);
        }
    }
}
