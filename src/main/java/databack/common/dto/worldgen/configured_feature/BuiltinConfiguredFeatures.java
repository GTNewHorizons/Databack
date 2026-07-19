package databack.common.dto.worldgen.configured_feature;

import java.util.List;

import javax.annotation.Nonnegative;

import org.jetbrains.annotations.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.annotations.SerializedName;
import com.gtnewhorizon.gtnhlib.blockstate.core.BlockState;
import databack.common.annotation.RangeFloat;
import databack.common.dto.worldgen.BlockWhitelist;
import databack.common.dto.worldgen.FluidState;
import databack.common.dto.worldgen.block_predicate.IBlockPredicate;
import databack.common.dto.worldgen.block_state_provider.IBlockStateProvider;
import databack.common.dto.worldgen.configured_feature.tree.BuiltinTreeComponents;
import databack.common.dto.worldgen.configured_feature.tree.IFallenLogDecorator;
import databack.common.dto.worldgen.configured_feature.tree.IFeatureSize;
import databack.common.dto.worldgen.configured_feature.tree.IRootPlacer;
import databack.common.dto.worldgen.configured_feature.tree.ITreeDecorator;
import databack.common.dto.worldgen.configured_feature.tree.ITreeFoliagePlacer;
import databack.common.dto.worldgen.configured_feature.tree.ITreeTrunkPlacer;
import databack.common.dto.worldgen.float_provider.IFloatProvider;
import databack.common.dto.worldgen.int_provider.IIntProvider;
import databack.common.dto.worldgen.placed_feature.IPlacedFeatureRef;
import databack.common.dto.worldgen.processor_list.IProcessorListRef;
import databack.common.serde.DatapackSerialization;
import databack.common.serde.TaggedUnionLoader;

@SuppressWarnings("unused")
public class BuiltinConfiguredFeatures {

    public static void init() {
        BuiltinTreeComponents.init();

        TaggedUnionLoader<IConfiguredFeature> loader = DatapackSerialization
            .createTaggedUnionLoader("worldgen/configured_feature", IConfiguredFeature.class);

        loader.addVariant("minecraft:bamboo", ProbabilityFeature.class);
        loader.addVariant("minecraft:seagrass", ProbabilityFeature.class);
        loader.addVariant("minecraft:basalt_columns", BasaltColumnsFeature.class);
        loader.addVariant("minecraft:block_column", BlockColumnFeature.class);
        loader.addVariant("minecraft:block_pile", BlockPileFeature.class);
        loader.addVariant("minecraft:decorated", DecoratedFeature.class);
        loader.addVariant("minecraft:desert_well", DesertWellFeature.class);
        loader.addVariant("minecraft:delta_feature", DeltaFeatureFeature.class);
        loader.addVariant("minecraft:disk", DiskFeature.class);
        loader.addVariant("minecraft:ice_patch", DiskFeature.class);
        loader.addVariant("minecraft:emerald_ore", EmeraldOreFeature.class);
        loader.addVariant("minecraft:end_gateway", EndGatewayFeature.class);
        loader.addVariant("minecraft:end_podium", EndPodiumFeature.class);
        loader.addVariant("minecraft:end_spike", EndSpikeFeature.class);
        loader.addVariant("minecraft:dripstone_cluster", SpeleothemClusterFeature.class);
        loader.addVariant("minecraft:speleothem_cluster", SpeleothemClusterFeature.class);
        loader.addVariant("minecraft:fallen_tree", FallenTreeFeature.class);
        loader.addVariant("minecraft:fill_layer", FillLayerFeature.class);
        loader.addVariant("minecraft:flower", RandomPatchFeature.class);
        loader.addVariant("minecraft:no_bonemeal_flower", RandomPatchFeature.class);
        loader.addVariant("minecraft:random_patch", RandomPatchFeature.class);
        loader.addVariant("minecraft:block_blob", BlockBlobFeature.class);
        loader.addVariant("minecraft:forest_rock", ForestRockFeature.class);
        loader.addVariant("minecraft:fossil", FossilFeature.class);
        loader.addVariant("minecraft:geode", GeodeFeature.class);
        loader.addVariant("minecraft:glow_lichen", MultifaceGrowthFeature.class);
        loader.addVariant("minecraft:multiface_growth", MultifaceGrowthFeature.class);
        loader.addVariant("minecraft:growing_plant", GrowingPlantFeature.class);
        loader.addVariant("minecraft:huge_brown_mushroom", HugeMushroomFeature.class);
        loader.addVariant("minecraft:huge_red_mushroom", HugeMushroomFeature.class);
        loader.addVariant("minecraft:huge_fungus", HugeFungusFeature.class);
        loader.addVariant("minecraft:iceberg", IcebergFeature.class);
        loader.addVariant("minecraft:lake", LakeFeature.class);
        loader.addVariant("minecraft:large_dripstone", LargeDripstoneFeature.class);
        loader.addVariant("minecraft:nether_forest_vegetation", NetherForestVegetationFeature.class);
        loader.addVariant("minecraft:netherrack_replace_blobs", NetherrackReplaceBlobsFeature.class);
        loader.addVariant("minecraft:no_surface_ore", OreFeature.class);
        loader.addVariant("minecraft:ore", OreFeature.class);
        loader.addVariant("minecraft:scattered_ore", OreFeature.class);
        loader.addVariant("minecraft:pointed_dripstone", SpeleothemFeature.class);
        loader.addVariant("minecraft:speleothem", SpeleothemFeature.class);
        loader.addVariant("minecraft:random_boolean_selector", RandomBooleanSelectorFeature.class);
        loader.addVariant("minecraft:random_selector", RandomSelectorFeature.class);
        loader.addVariant("minecraft:replace_single_block", ReplaceSingleBlockFeature.class);
        loader.addVariant("minecraft:root_system", RootSystemFeature.class);
        loader.addVariant("minecraft:sculk_patch", SculkPatchFeature.class);
        loader.addVariant("minecraft:sea_pickle", SeaPickleFeature.class);
        loader.addVariant("minecraft:sequence", SequenceFeature.class);
        loader.addVariant("minecraft:simple_block", SimpleBlockFeature.class);
        loader.addVariant("minecraft:simple_random_selector", SimpleRandomSelectorFeature.class);
        loader.addVariant("minecraft:small_dripstone", SmallDripstoneFeature.class);
        loader.addVariant("minecraft:spike", SpikeFeature.class);
        loader.addVariant("minecraft:spring_feature", SpringFeature.class);
        loader.addVariant("minecraft:template", TemplateFeature.class);
        loader.addVariant("minecraft:twisting_vines", TwistingVinesFeature.class);
        loader.addVariant("minecraft:underwater_magma", UnderwaterMagmaFeature.class);
        loader.addVariant("minecraft:vegetation_patch", VegetationPatchFeature.class);
        loader.addVariant("minecraft:waterlogged_vegetation_patch", VegetationPatchFeature.class);
        loader.addVariant("minecraft:weighted_random_selector", WeightedRandomSelectorFeature.class);
        loader.addVariant("minecraft:tree", TreeFeature.class);
        loader.addVariant("minecraft:basalt_pillar", BasaltPillarFeature.class);
        loader.addVariant("minecraft:blue_ice", BlueIceFeature.class);
        loader.addVariant("minecraft:bonus_chest", BonusChestFeature.class);
        loader.addVariant("minecraft:chorus_plant", ChorusPlantFeature.class);
        loader.addVariant("minecraft:end_island", EndIslandFeature.class);
        loader.addVariant("minecraft:end_platform", EndPlatformFeature.class);
        loader.addVariant("minecraft:freeze_top_layer", FreezeTopLayerFeature.class);
        loader.addVariant("minecraft:glowstone_blob", GlowstoneBlobFeature.class);
        loader.addVariant("minecraft:kelp", KelpFeature.class);
        loader.addVariant("minecraft:monster_room", MonsterRoomFeature.class);
        loader.addVariant("minecraft:vines", VinesFeature.class);
        loader.addVariant("minecraft:void_start_platform", VoidStartPlatformFeature.class);
        loader.addVariant("minecraft:weeping_vines", WeepingVinesFeature.class);
        loader.addVariant("minecraft:coral_claw", CoralClawFeature.class);
        loader.addVariant("minecraft:coral_mushroom", CoralMushroomFeature.class);
        loader.addVariant("minecraft:coral_tree", CoralTreeFeature.class);

        loader.setFallback((json, typeOfT, context) -> {
            if (!json.isJsonPrimitive()) throw new JsonParseException("Expected typed object or resource location for configured feature: " + json);
            return new ConfiguredFeatureRef(json.getAsString());
        });
    }

    private static class ConfiguredFeatureRef implements IConfiguredFeature {
        public String id;
        ConfiguredFeatureRef(String id) { this.id = id; }
    }

    // bamboo, seagrass
    private static class ProbabilityFeature implements IConfiguredFeature {
        public ProbabilityConfig config;
    }
    private static class ProbabilityConfig {
        @RangeFloat(min = 0, max = 1) public float probability;
    }

    // basalt_columns
    private static class BasaltColumnsFeature implements IConfiguredFeature {
        public BasaltColumnsConfig config;
    }
    private static class BasaltColumnsConfig {
        public IIntProvider reach;
        public IIntProvider height;
    }

    // block_column (since 1.18)
    private static class BlockColumnFeature implements IConfiguredFeature {
        public BlockColumnConfig config;
    }
    private static class BlockColumnConfig {
        public String direction;
        public IBlockPredicate allowed_placement;
        public boolean prioritize_tip;
        /** Each layer has height (IntProvider) and provider (BlockStateProvider). */
        public JsonElement layers;
    }

    // block_pile
    private static class BlockPileFeature implements IConfiguredFeature {
        public BlockPileConfig config;
    }
    private static class BlockPileConfig {
        public IBlockStateProvider state_provider;
    }

    // decorated (until 1.18)
    private static class DecoratedFeature implements IConfiguredFeature {
        public DecoratedConfig config;
    }
    private static class DecoratedConfig {
        public JsonElement decorator;
        public JsonElement feature;
    }

    // basalt_pillar
    private static class BasaltPillarFeature implements IConfiguredFeature {}

    // blue_ice
    private static class BlueIceFeature implements IConfiguredFeature {}

    // bonus_chest
    private static class BonusChestFeature implements IConfiguredFeature {}

    // chorus_plant
    private static class ChorusPlantFeature implements IConfiguredFeature {}

    // end_island
    private static class EndIslandFeature implements IConfiguredFeature {}

    // end_platform
    private static class EndPlatformFeature implements IConfiguredFeature {}

    // freeze_top_layer
    private static class FreezeTopLayerFeature implements IConfiguredFeature {}

    // glowstone_blob
    private static class GlowstoneBlobFeature implements IConfiguredFeature {}

    // kelp
    private static class KelpFeature implements IConfiguredFeature {}

    // monster_room
    private static class MonsterRoomFeature implements IConfiguredFeature {}

    // vines
    private static class VinesFeature implements IConfiguredFeature {}

    // void_start_platform
    private static class VoidStartPlatformFeature implements IConfiguredFeature {}

    // weeping_vines
    private static class WeepingVinesFeature implements IConfiguredFeature {}

    // coral_claw, coral_mushroom, coral_tree
    private static class CoralClawFeature implements IConfiguredFeature {}
    private static class CoralMushroomFeature implements IConfiguredFeature {}
    private static class CoralTreeFeature implements IConfiguredFeature {}

    // desert_well
    private static class DesertWellFeature implements IConfiguredFeature {}

    // delta_feature
    private static class DeltaFeatureFeature implements IConfiguredFeature {
        public DeltaFeatureConfig config;
    }
    private static class DeltaFeatureConfig {
        public BlockState contents;
        public BlockState rim;
        public IIntProvider size;
        public IIntProvider rim_size;
    }

    // disk, ice_patch
    private static class DiskFeature implements IConfiguredFeature {
        public DiskConfig config;
    }
    private static class DiskConfig {
        @Nullable public BlockState state;
        @Nullable public IBlockStateProvider state_provider;
        public IIntProvider radius;
        public int half_height;
        @Nullable public JsonElement targets;
        @Nullable public IBlockPredicate target;
    }

    // emerald_ore
    private static class EmeraldOreFeature implements IConfiguredFeature {
        public EmeraldOreConfig config;
    }
    private static class EmeraldOreConfig {
        public BlockState state;
        public BlockState target;
    }

    // end_gateway
    private static class EndGatewayFeature implements IConfiguredFeature {
        public EndGatewayConfig config;
    }
    private static class EndGatewayConfig {
        public boolean exact;
        @Nullable public int[] exit;
    }

    // end_podium (since 26.3)
    private static class EndPodiumFeature implements IConfiguredFeature {
        public EndPodiumConfig config;
    }
    private static class EndPodiumConfig {
        @Nullable public Boolean active;
    }

    // end_spike
    private static class EndSpikeFeature implements IConfiguredFeature {
        public EndSpikeConfig config;
    }
    private static class EndSpikeConfig {
        public EndSpike[] spikes;
        @Nullable public Boolean crystal_invulnerable;
        @Nullable public int[] crystal_beam_target;
    }

    private static class EndSpike {
        public int centerX;
        public int centerZ;
        public int radius;
        public int height;
        @Nullable public Boolean guarded;
    }

    // dripstone_cluster (until 26.2), speleothem_cluster (since 26.2)
    private static class SpeleothemClusterFeature implements IConfiguredFeature {
        public SpeleothemClusterConfig config;
    }
    private static class SpeleothemClusterConfig {
        @Nullable public BlockState base_block;
        @Nullable public BlockState pointed_block;
        @Nullable public BlockWhitelist replaceable_blocks;
        @Nonnegative public int floor_to_ceiling_search_range;
        public IIntProvider height;
        public IIntProvider radius;
        @Nonnegative public int max_stalagmite_stalactite_height_diff;
        @Nonnegative public int height_deviation;
        @Nullable public IIntProvider dripstone_block_layer_thickness;
        @Nullable public IIntProvider speleothem_block_layer_thickness;
        public IFloatProvider density;
        public IFloatProvider wetness;
        @Nullable @RangeFloat(min = 0, max = 1) public Float chance_of_dripstone_column_at_max_distance_from_center;
        @Nullable @RangeFloat(min = 0, max = 1) public Float chance_of_speleothem_at_max_distance_from_center;
        @Nullable public Integer max_distance_from_edge_affecting_chance_of_dripstone_column;
        @Nullable public Integer max_distance_from_edge_affecting_chance_of_speleothem;
        @Nonnegative public int max_distance_from_center_affecting_height_bias;
    }

    // fallen_tree (since 26.1)
    private static class FallenTreeFeature implements IConfiguredFeature {
        public FallenTreeConfig config;
    }
    private static class FallenTreeConfig {
        public IBlockStateProvider trunk_provider;
        public IIntProvider log_length;
        public IFallenLogDecorator[] log_decorators;
        public IFallenLogDecorator[] stump_decorators;
    }

    // fill_layer
    private static class FillLayerFeature implements IConfiguredFeature {
        public FillLayerConfig config;
    }
    private static class FillLayerConfig {
        public BlockState state;
        @Nonnegative public int height;
    }

    // flower, no_bonemeal_flower, random_patch
    private static class RandomPatchFeature implements IConfiguredFeature {
        public RandomPatchConfig config;
    }
    private static class RandomPatchConfig {
        @Nullable public Integer tries;
        // Until 1.18
        @Nullable public Boolean can_replace;
        @Nullable public Boolean project;
        @Nullable public Boolean need_water;
        @Nullable public Integer xspread;
        @Nullable public Integer yspread;
        @Nullable public Integer zspread;
        @Nullable public JsonElement state_provider;
        @Nullable public JsonElement block_placer;
        @Nullable public JsonElement whitelist;
        @Nullable public JsonElement blacklist;
        // Since 1.18
        @Nullable public Integer xz_spread;
        @Nullable public Integer y_spread;
        @Nullable public IPlacedFeatureRef feature;
    }

    // block_blob (since 26.1)
    private static class BlockBlobFeature implements IConfiguredFeature {
        public BlockBlobConfig config;
    }
    private static class BlockBlobConfig {
        public BlockState state;
        public IBlockPredicate can_place_on;
    }

    // forest_rock (until 26.1)
    private static class ForestRockFeature implements IConfiguredFeature {
        public ForestRockConfig config;
    }
    private static class ForestRockConfig {
        public BlockState state;
    }

    // fossil (since 1.17)
    private static class FossilFeature implements IConfiguredFeature {
        public FossilConfig config;
    }
    private static class FossilConfig {
        public int max_empty_corners_allowed;
        public String[] fossil_structures;
        public String[] overlay_structures;
        public IProcessorListRef fossil_processors;
        public IProcessorListRef overlay_processors;
    }

    // geode (since 1.17)
    private static class GeodeFeature implements IConfiguredFeature {
        public GeodeConfig config;
    }
    private static class GeodeConfig {
        public JsonElement blocks;
        public JsonElement layers;
        public JsonElement crack;
        @Nullable @RangeFloat(min = 0, max = 1) public Float noise_multiplier;
        @Nullable @RangeFloat(min = 0, max = 1) public Float use_potential_placements_chance;
        @Nullable @RangeFloat(min = 0, max = 1) public Float use_alternate_layer0_chance;
        @Nullable public Boolean placements_require_layer0_alternate;
        @Nullable public IIntProvider outer_wall_distance;
        @Nullable public IIntProvider distribution_points;
        @Nullable public IIntProvider point_offset;
        @Nullable public Integer min_gen_offset;
        @Nullable public Integer max_gen_offset;
        public int invalid_blocks_threshold;
    }

    // glow_lichen, multiface_growth (since 1.17)
    private static class MultifaceGrowthFeature implements IConfiguredFeature {
        public MultifaceGrowthConfig config;
    }
    private static class MultifaceGrowthConfig {
        @Nullable public String block;
        @Nullable public Integer search_range;
        @Nullable @RangeFloat(min = 0, max = 1) public Float chance_of_spreading;
        @Nullable public Boolean can_place_on_floor;
        @Nullable public Boolean can_place_on_ceiling;
        @Nullable public Boolean can_place_on_wall;
        @Nullable public BlockWhitelist can_be_placed_on;
    }

    // growing_plant (1.17–1.18)
    private static class GrowingPlantFeature implements IConfiguredFeature {
        public GrowingPlantConfig config;
    }
    private static class GrowingPlantConfig {
        public String direction;
        public boolean allow_water;
        public JsonElement height_distribution;
        public JsonElement body_provider;
        public JsonElement head_provider;
    }

    // huge_brown_mushroom, huge_red_mushroom
    private static class HugeMushroomFeature implements IConfiguredFeature {
        public HugeMushroomConfig config;
    }
    private static class HugeMushroomConfig {
        public IBlockStateProvider cap_provider;
        public IBlockStateProvider stem_provider;
        public int foliage_radius;
        @Nullable public IBlockPredicate can_place_on;
    }

    // huge_fungus
    private static class HugeFungusFeature implements IConfiguredFeature {
        public HugeFungusConfig config;
    }
    private static class HugeFungusConfig {
        public BlockState hat_state;
        public BlockState decor_state;
        public BlockState stem_state;
        public BlockState valid_base_block;
        @Nullable public Boolean planted;
        @Nullable public IBlockPredicate replaceable_blocks;
    }

    // iceberg
    private static class IcebergFeature implements IConfiguredFeature {
        public IcebergConfig config;
    }
    private static class IcebergConfig {
        public BlockState state;
    }

    // lake
    private static class LakeFeature implements IConfiguredFeature {
        public LakeConfig config;
    }
    private static class LakeConfig {
        @Nullable public JsonElement state;
        @Nullable public IBlockStateProvider fluid;
        @Nullable public IBlockStateProvider barrier;
        @Nullable public IBlockPredicate can_place_feature;
        @Nullable public IBlockPredicate can_replace_with_air_or_fluid;
        @Nullable public IBlockPredicate can_replace_with_barrier;
    }

    // large_dripstone (since 1.17)
    private static class LargeDripstoneFeature implements IConfiguredFeature {
        public LargeDripstoneConfig config;
    }
    private static class LargeDripstoneConfig {
        @Nullable public IBlockPredicate replaceable_blocks;
        @Nullable public Integer floor_to_ceiling_search_range;
        public IIntProvider column_radius;
        public IFloatProvider height_scale;
        @RangeFloat(min = 0, max = 1) public float max_column_radius_to_cave_height_ratio;
        public IFloatProvider stalactite_bluntness;
        public IFloatProvider stalagmite_bluntness;
        public IFloatProvider wind_speed;
        public int min_radius_for_wind;
        @RangeFloat(min = 0, max = 1) public float min_bluntness_for_wind;
    }

    // nether_forest_vegetation
    private static class NetherForestVegetationFeature implements IConfiguredFeature {
        public NetherForestVegetationConfig config;
    }
    private static class NetherForestVegetationConfig {
        public IBlockStateProvider state_provider;
        @Nullable @Nonnegative public Integer spread_width;
        @Nullable @Nonnegative public Integer spread_height;
    }

    // netherrack_replace_blobs
    private static class NetherrackReplaceBlobsFeature implements IConfiguredFeature {
        public NetherrackReplaceBlobsConfig config;
    }
    private static class NetherrackReplaceBlobsConfig {
        public BlockState state;
        public BlockState target;
        public IIntProvider radius;
    }

    // no_surface_ore, ore, scattered_ore
    private static class OreFeature implements IConfiguredFeature {
        public OreConfig config;
    }
    private static class OreConfig {
        @Nullable public JsonElement target;
        @Nullable public JsonElement state;
        @Nullable public JsonElement targets;
        @Nonnegative public int size;
        @Nullable @RangeFloat(min = 0, max = 1) public Float discard_chance_on_air_exposure;
    }

    // pointed_dripstone (until 26.2), speleothem (since 26.2)
    private static class SpeleothemFeature implements IConfiguredFeature {
        public SpeleothemConfig config;
    }
    private static class SpeleothemConfig {
        @Nullable public BlockState base_block;
        @Nullable public BlockState pointed_block;
        @Nullable public BlockWhitelist replaceable_blocks;
        @Nullable @RangeFloat(min = 0, max = 1) public Float chance_of_taller_dripstone;
        @Nullable @RangeFloat(min = 0, max = 1) public Float chance_of_taller_generation;
        @Nullable @RangeFloat(min = 0, max = 1) public Float chance_of_directional_spread;
        @Nullable @RangeFloat(min = 0, max = 1) public Float chance_of_spread_radius2;
        @Nullable @RangeFloat(min = 0, max = 1) public Float chance_of_spread_radius3;
    }

    // random_boolean_selector
    private static class RandomBooleanSelectorFeature implements IConfiguredFeature {
        public RandomBooleanSelectorConfig config;
    }
    private static class RandomBooleanSelectorConfig {
        public IPlacedFeatureRef feature_false;
        public IPlacedFeatureRef feature_true;
    }

    // random_selector
    private static class RandomSelectorFeature implements IConfiguredFeature {
        public RandomSelectorConfig config;
    }
    private static class RandomSelectorConfig {
        public WeightedPlacedFeature[] features;
        @SerializedName("default") public IPlacedFeatureRef defaultFeature;
    }

    /** An entry in a {@code random_selector} features list: a placed feature ref with a selection probability. */
    private static class WeightedPlacedFeature {
        @RangeFloat(min = 0, max = 1) public float chance;
        public IPlacedFeatureRef feature;
    }

    // replace_single_block (since 1.17)
    private static class ReplaceSingleBlockFeature implements IConfiguredFeature {
        public ReplaceSingleBlockConfig config;
    }
    private static class ReplaceSingleBlockConfig {
        public JsonElement targets;
    }

    // root_system (since 1.17)
    private static class RootSystemFeature implements IConfiguredFeature {
        public RootSystemConfig config;
    }
    private static class RootSystemConfig {
        public int required_vertical_space_for_tree;
        @Nullable public Integer level_test_distance;
        @Nullable public Integer max_level_deviation;
        public int root_radius;
        public int root_placement_attempts;
        public int root_column_max_height;
        public int hanging_root_radius;
        public int hanging_roots_vertical_span;
        public int hanging_root_placement_attempts;
        public int allowed_vertical_water_for_tree;
        public BlockWhitelist root_replaceable;
        public IBlockStateProvider root_state_provider;
        public IBlockStateProvider hanging_root_state_provider;
        @Nullable public IBlockPredicate allowed_tree_position;
        public IPlacedFeatureRef feature;
    }

    // sculk_patch (since 1.19)
    private static class SculkPatchFeature implements IConfiguredFeature {
        public SculkPatchConfig config;
    }
    private static class SculkPatchConfig {
        public int charge_count;
        public int amount_per_charge;
        public int spread_attempts;
        public int growth_rounds;
        public int spread_rounds;
        public IIntProvider extra_rare_growths;
        @RangeFloat(min = 0, max = 1) public float catalyst_chance;
    }

    // sea_pickle
    private static class SeaPickleFeature implements IConfiguredFeature {
        public SeaPickleConfig config;
    }
    private static class SeaPickleConfig {
        public IIntProvider count;
    }

    // sequence (since 26.2)
    private static class SequenceFeature implements IConfiguredFeature {
        public SequenceConfig config;
    }
    private static class SequenceConfig {
        public IPlacedFeatureRef[] features;
    }

    // simple_block
    private static class SimpleBlockFeature implements IConfiguredFeature {
        public SimpleBlockConfig config;
    }
    private static class SimpleBlockConfig {
        public IBlockStateProvider to_place;
        @Nullable public Boolean schedule_tick;
        @Nullable public JsonElement place_on;
        @Nullable public JsonElement place_in;
        @Nullable public JsonElement place_under;
    }

    // simple_random_selector
    private static class SimpleRandomSelectorFeature implements IConfiguredFeature {
        public SimpleRandomSelectorConfig config;
    }
    private static class SimpleRandomSelectorConfig {
        public IPlacedFeatureRef[] features;
    }

    // small_dripstone (1.17–1.18)
    private static class SmallDripstoneFeature implements IConfiguredFeature {
        public SmallDripstoneConfig config;
    }
    private static class SmallDripstoneConfig {
        @Nullable public Integer max_placements;
        @Nullable public Integer empty_space_search_radius;
        @Nullable public Integer max_offset_from_origin;
        @Nullable @RangeFloat(min = 0, max = 1) public Float chance_of_taller_dripstone;
    }

    // spike (since 26.1)
    private static class SpikeFeature implements IConfiguredFeature {
        public SpikeConfig config;
    }
    private static class SpikeConfig {
        public BlockState state;
        public IBlockPredicate can_place_on;
        public IBlockPredicate can_replace;
    }

    // spring_feature
    private static class SpringFeature implements IConfiguredFeature {
        public SpringConfig config;
    }
    private static class SpringConfig {
        public FluidState state;
        public int rock_count;
        public int hole_count;
        public boolean requires_block_below;
        public BlockWhitelist valid_blocks;
    }

    // template (since 26.2)
    private static class TemplateFeature implements IConfiguredFeature {
        public TemplateConfig config;
    }
    private static class TemplateConfig {
        public JsonElement templates;
    }

    // twisting_vines (since 1.18)
    private static class TwistingVinesFeature implements IConfiguredFeature {
        public TwistingVinesConfig config;
    }
    private static class TwistingVinesConfig {
        @Nonnegative public int spread_width;
        @Nonnegative public int spread_height;
        @Nonnegative public int max_height;
    }

    // underwater_magma (since 1.17)
    private static class UnderwaterMagmaFeature implements IConfiguredFeature {
        public UnderwaterMagmaConfig config;
    }
    private static class UnderwaterMagmaConfig {
        public int floor_search_range;
        public int placement_radius_around_floor;
        @RangeFloat(min = 0, max = 1) public float placement_probability_per_valid_position;
    }

    // vegetation_patch, waterlogged_vegetation_patch (since 1.17)
    private static class VegetationPatchFeature implements IConfiguredFeature {
        public VegetationPatchConfig config;
    }
    private static class VegetationPatchConfig {
        public String surface;
        public IIntProvider depth;
        public int vertical_range;
        @RangeFloat(min = 0, max = 1) public float extra_bottom_block_chance;
        @RangeFloat(min = 0, max = 1) public float extra_edge_column_chance;
        @RangeFloat(min = 0, max = 1) public float vegetation_chance;
        public IIntProvider xz_radius;
        public JsonElement replaceable;
        public IBlockStateProvider ground_state;
        public IPlacedFeatureRef vegetation_feature;
    }

    // weighted_random_selector (since 26.2)
    private static class WeightedRandomSelectorFeature implements IConfiguredFeature {
        public WeightedRandomSelectorConfig config;
    }
    private static class WeightedRandomSelectorConfig {
        public WeightedListEntry[] features;
    }

    /** A {@code WeightedList} entry wrapping a placed feature reference. */
    private static class WeightedListEntry {
        public int weight;
        public IPlacedFeatureRef data;
    }

    private static class TreeFeature implements IConfiguredFeature {
        public TreeConfig config;
    }

    private static class TreeConfig {
        public IBlockStateProvider trunk_provider;
        public IBlockStateProvider foliage_provider;
        @Nullable public IBlockStateProvider dirt_provider;
        @Nullable public IBlockStateProvider below_trunk_provider;
        public ITreeTrunkPlacer trunk_placer;
        public ITreeFoliagePlacer foliage_placer;
        @Nullable public IRootPlacer root_placer;
        public List<ITreeDecorator> decorators;
        public IFeatureSize minimum_size;
        @Nullable public Boolean ignore_vines;
        @Nullable public Boolean force_dirt;
    }
}
