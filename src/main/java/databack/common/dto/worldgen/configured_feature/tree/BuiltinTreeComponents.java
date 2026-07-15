package databack.common.dto.worldgen.configured_feature.tree;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.google.gson.JsonElement;

import databack.common.annotation.RangeFloat;
import databack.common.dto.worldgen.block_predicate.IBlockPredicate;
import databack.common.dto.worldgen.int_provider.IIntProvider;
import databack.common.serde.DatapackSerialization;
import databack.common.serde.TaggedUnionLoader;

public class BuiltinTreeComponents {

    public static void init() {
        TaggedUnionLoader<ITreeTrunkPlacer> trunkPlacer = DatapackSerialization
            .createTaggedUnionLoader("worldgen/trunk_placer", ITreeTrunkPlacer.class);

        trunkPlacer.addVariant("minecraft:straight_trunk_placer", EmptyTrunkPlacer.class);
        trunkPlacer.addVariant("minecraft:forking_trunk_placer", EmptyTrunkPlacer.class);
        trunkPlacer.addVariant("minecraft:giant_trunk_placer", EmptyTrunkPlacer.class);
        trunkPlacer.addVariant("minecraft:mega_jungle_trunk_placer", EmptyTrunkPlacer.class);
        trunkPlacer.addVariant("minecraft:dark_oak_trunk_placer", EmptyTrunkPlacer.class);
        trunkPlacer.addVariant("minecraft:fancy_trunk_placer", EmptyTrunkPlacer.class);
        trunkPlacer.addVariant("minecraft:bending_trunk_placer", BendingTrunkPlacer.class);
        trunkPlacer.addVariant("minecraft:cherry_trunk_placer", CherryTrunkPlacer.class);
        trunkPlacer.addVariant("minecraft:upwards_branching_trunk_placer", UpwardsBranchingTrunkPlacer.class);
        trunkPlacer.addVariant("minecraft:palm_trunk_placer", PalmTrunkPlacer.class);
        trunkPlacer.addVariant("minecraft:mangrove_trunk_placer", EmptyTrunkPlacer.class);

        TaggedUnionLoader<ITreeFoliagePlacer> foliagePlacer = DatapackSerialization
            .createTaggedUnionLoader("worldgen/foliage_placer", ITreeFoliagePlacer.class);

        foliagePlacer.addVariant("minecraft:blob_foliage_placer", HeightFoliagePlacer.class);
        foliagePlacer.addVariant("minecraft:spruce_foliage_placer", SpruceFoliagePlacer.class);
        foliagePlacer.addVariant("minecraft:pine_foliage_placer", PineFoliagePlacer.class);
        foliagePlacer.addVariant("minecraft:acacia_foliage_placer", EmptyFoliagePlacer.class);
        foliagePlacer.addVariant("minecraft:bush_foliage_placer", HeightFoliagePlacer.class);
        foliagePlacer.addVariant("minecraft:fancy_foliage_placer", EmptyFoliagePlacer.class);
        foliagePlacer.addVariant("minecraft:jungle_foliage_placer", HeightFoliagePlacer.class);
        foliagePlacer.addVariant("minecraft:mega_pine_foliage_placer", MegaPineFoliagePlacer.class);
        foliagePlacer.addVariant("minecraft:dark_oak_foliage_placer", EmptyFoliagePlacer.class);
        foliagePlacer.addVariant("minecraft:cherry_foliage_placer", CherryFoliagePlacer.class);
        foliagePlacer.addVariant("minecraft:pale_oak_foliage_placer", PaleOakFoliagePlacer.class);
        foliagePlacer.addVariant("minecraft:random_spread_foliage_placer", RandomSpreadFoliagePlacer.class);

        TaggedUnionLoader<ITreeDecorator> treeDecorator = DatapackSerialization
            .createTaggedUnionLoader("worldgen/tree_decorator", ITreeDecorator.class);

        treeDecorator.addVariant("minecraft:trunk_vine", EmptyTreeDecorator.class);
        treeDecorator.addVariant("minecraft:leave_vine", EmptyTreeDecorator.class);
        treeDecorator.addVariant("minecraft:cocoa", CocoaTreeDecorator.class);
        treeDecorator.addVariant("minecraft:beehive", BeehiveTreeDecorator.class);
        treeDecorator.addVariant("minecraft:alter_ground", AlterGroundTreeDecorator.class);
        treeDecorator.addVariant("minecraft:attached_to_leaves", AttachedToLeavesTreeDecorator.class);

        TaggedUnionLoader<IFeatureSize> featureSize = DatapackSerialization
            .createTaggedUnionLoader("worldgen/feature_size", IFeatureSize.class);

        featureSize.addVariant("minecraft:two_layers_feature_size", TwoLayersFeatureSize.class);
        featureSize.addVariant("minecraft:three_layers_feature_size", ThreeLayersFeatureSize.class);

        TaggedUnionLoader<IRootPlacer> rootPlacer = DatapackSerialization
            .createTaggedUnionLoader("worldgen/root_placer", IRootPlacer.class);

        rootPlacer.addVariant("minecraft:mangrove_root_placer", MangroveRootPlacer.class);
    }

    // -------------------------------------------------------------------------
    // Trunk placer base + variants
    // -------------------------------------------------------------------------

    public static class TrunkPlacerBase implements ITreeTrunkPlacer {
        public int base_height;
        public int height_rand_a;
        public int height_rand_b;
    }

    public static class EmptyTrunkPlacer extends TrunkPlacerBase {
    }

    public static class BendingTrunkPlacer extends TrunkPlacerBase {
        public IIntProvider bend_length;
        @Nullable public Integer min_height_for_leaves;
    }

    public static class CherryTrunkPlacer extends TrunkPlacerBase {
        public IIntProvider branch_count;
        public IIntProvider branch_horizontal_length;
        public IIntProvider branch_start_offset_from_top;
        public IIntProvider branch_end_offset_from_top;
    }

    public static class UpwardsBranchingTrunkPlacer extends TrunkPlacerBase {
        public IIntProvider extra_branch_steps;
        public float place_branch_per_log_probability;
        public IIntProvider extra_branch_length;
        public List<IBlockPredicate> can_grow_through;
    }

    public static class PalmTrunkPlacer extends TrunkPlacerBase {
        public IIntProvider leaf_overflow;
        public IIntProvider leaf_overflow_offset;
        public IIntProvider height;
        public IIntProvider trunk_height;
    }

    // -------------------------------------------------------------------------
    // Foliage placer base + variants
    // -------------------------------------------------------------------------

    public static class FoliagePlacerBase implements ITreeFoliagePlacer {
        public IIntProvider radius;
        public int offset;
    }

    public static class EmptyFoliagePlacer extends FoliagePlacerBase {
    }

    public static class HeightFoliagePlacer extends FoliagePlacerBase {
        public int height;
    }

    public static class SpruceFoliagePlacer extends FoliagePlacerBase {
        public IIntProvider trunk_height;
    }

    public static class PineFoliagePlacer extends FoliagePlacerBase {
        public IIntProvider height;
    }

    public static class MegaPineFoliagePlacer extends FoliagePlacerBase {
        public IIntProvider crown_height;
    }

    public static class CherryFoliagePlacer extends FoliagePlacerBase {
        public IIntProvider height;
        @RangeFloat(min = 0, max = 1) public float wide_bottom_layer_hole_chance;
        @RangeFloat(min = 0, max = 1) public float corner_hole_chance;
        @RangeFloat(min = 0, max = 1) public float hanging_leaves_chance;
        @RangeFloat(min = 0, max = 1) public float hanging_leaves_extension_chance;
    }

    public static class PaleOakFoliagePlacer extends FoliagePlacerBase {
        public IIntProvider height;
        public IIntProvider inner_fill_radius;
        public IIntProvider outer_fill_radius;
        @RangeFloat(min = 0, max = 1) public float fill_corner_probability;
    }

    public static class RandomSpreadFoliagePlacer extends FoliagePlacerBase {
        public IIntProvider foliage_height;
        public int leaf_placement_attempts;
    }

    // -------------------------------------------------------------------------
    // Tree decorator variants
    // -------------------------------------------------------------------------

    public static class EmptyTreeDecorator implements ITreeDecorator {
    }

    public static class CocoaTreeDecorator implements ITreeDecorator {
        @RangeFloat(min = 0, max = 1) public float probability;
    }

    public static class BeehiveTreeDecorator implements ITreeDecorator {
        @RangeFloat(min = 0, max = 1) public float probability;
    }

    public static class AlterGroundTreeDecorator implements ITreeDecorator {
        public JsonElement provider;
    }

    public static class AttachedToLeavesTreeDecorator implements ITreeDecorator {
        @RangeFloat(min = 0, max = 1) public float probability;
        public int exclusion_radius_xz;
        public int exclusion_radius_y;
        public int required_empty_blocks;
        public JsonElement block_provider;
        public JsonElement directions;
    }

    // -------------------------------------------------------------------------
    // Feature size variants
    // -------------------------------------------------------------------------

    public static class TwoLayersFeatureSize implements IFeatureSize {
        @Nullable public Integer limit;
        @Nullable public Integer lower_size;
        @Nullable public Integer upper_size;
        @Nullable public Float min_clipped_height;
    }

    public static class ThreeLayersFeatureSize implements IFeatureSize {
        @Nullable public Integer limit;
        @Nullable public Integer upper_limit;
        @Nullable public Integer lower_size;
        @Nullable public Integer middle_size;
        @Nullable public Integer upper_size;
        @Nullable public Float min_clipped_height;
    }

    // -------------------------------------------------------------------------
    // Root placer variants
    // -------------------------------------------------------------------------

    public static class MangroveRootPlacer implements IRootPlacer {
        public JsonElement root_provider;
        public IIntProvider trunk_offset_y;
        @Nullable public JsonElement above_root_placement;
        public JsonElement mangrove_root_placement;
    }
}
