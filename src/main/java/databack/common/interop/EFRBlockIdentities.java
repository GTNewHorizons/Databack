package databack.common.interop;

import static databack.common.interop.VanillaBlockIdentities.bits;
import static databack.common.interop.VanillaBlockIdentities.colored;
import static databack.common.interop.VanillaBlockIdentities.lookup;
import static databack.common.interop.VanillaBlockIdentities.torchPlacement;

import com.gtnewhorizon.gtnhlib.blockstate.properties.IntegerBlockProperty.IntegerMetaBlockProperty;
import databack.common.interop.registry.BlockIdentityRegistry;
import ganymedes01.etfuturum.ModBlocks;

/// Modern names for Et Futurum Requiem's blocks. Must run after [VanillaBlockIdentities#init()]: EFR blocks that
/// duplicate a vanilla block (tile replacements, old textures) are aliases, so vanilla keeps owning those ids, while
/// EFR's dedicated blocks (smooth stone, coarse dirt, etc) take ids over from vanilla's meta variants.
public class EFRBlockIdentities {

    private static final BlockIdentityRegistry BLOCKS = BlockIdentityRegistry.INSTANCE;

    private static final String[] COPPER_AGES = { "", "exposed_", "weathered_", "oxidized_" };

    private static final String[] MODERN_WOODS = { "crimson", "warped", "mangrove", "cherry", "bamboo" };

    public static void init() {
        renames();
        variants();
        woodVariants();
        aliases();
    }

    private static void renames() {
        rename(ModBlocks.SEA_LANTERN, "minecraft:sea_lantern");
        rename(ModBlocks.COARSE_DIRT, "minecraft:coarse_dirt");
        // Colour and standing/wall live in the tile entity
        rename(ModBlocks.BANNER, "minecraft:white_banner");
        rename(ModBlocks.SLIME, "minecraft:slime_block");
        rename(ModBlocks.BEETROOTS, "minecraft:beetroots");
        rename(ModBlocks.PURPUR_BLOCK, "minecraft:purpur_block");
        rename(ModBlocks.PURPUR_PILLAR, "minecraft:purpur_pillar");
        rename(ModBlocks.END_BRICKS, "minecraft:end_stone_bricks");
        rename(ModBlocks.GRASS_PATH, "minecraft:dirt_path");
        rename(ModBlocks.END_ROD, "minecraft:end_rod");
        rename(ModBlocks.CHORUS_PLANT, "minecraft:chorus_plant");
        rename(ModBlocks.CHORUS_FLOWER, "minecraft:chorus_flower");
        rename(ModBlocks.BONE, "minecraft:bone_block");
        rename(ModBlocks.ANCIENT_DEBRIS, "minecraft:ancient_debris");
        rename(ModBlocks.NETHERITE_BLOCK, "minecraft:netherite_block");
        rename(ModBlocks.NETHER_GOLD_ORE, "minecraft:nether_gold_ore");
        rename(ModBlocks.BLUE_ICE, "minecraft:blue_ice");
        rename(ModBlocks.SMOOTH_STONE, "minecraft:smooth_stone");
        rename(ModBlocks.SMOOTH_SANDSTONE, "minecraft:smooth_sandstone");
        rename(ModBlocks.SMOOTH_RED_SANDSTONE, "minecraft:smooth_red_sandstone");
        rename(ModBlocks.SMOOTH_QUARTZ, "minecraft:smooth_quartz");
        rename(ModBlocks.QUARTZ_BRICKS, "minecraft:quartz_bricks");
        rename(ModBlocks.COPPER_ORE, "minecraft:copper_ore");
        rename(ModBlocks.DEEPSLATE_COPPER_ORE, "minecraft:deepslate_copper_ore");
        rename(ModBlocks.CORNFLOWER, "minecraft:cornflower");
        rename(ModBlocks.LILY_OF_THE_VALLEY, "minecraft:lily_of_the_valley");
        rename(ModBlocks.WITHER_ROSE, "minecraft:wither_rose");
        rename(ModBlocks.SWEET_BERRY_BUSH, "minecraft:sweet_berry_bush");
        rename(ModBlocks.LIGHTNING_ROD, "minecraft:lightning_rod");
        rename(ModBlocks.DEEPSLATE, "minecraft:deepslate");
        rename(ModBlocks.COBBLED_DEEPSLATE, "minecraft:cobbled_deepslate");
        rename(ModBlocks.POLISHED_DEEPSLATE, "minecraft:polished_deepslate");
        rename(ModBlocks.SMOOTH_BASALT, "minecraft:smooth_basalt");
        rename(ModBlocks.CALCITE, "minecraft:calcite");
        rename(ModBlocks.AMETHYST_BLOCK, "minecraft:amethyst_block");
        rename(ModBlocks.BUDDING_AMETHYST, "minecraft:budding_amethyst");
        rename(ModBlocks.TINTED_GLASS, "minecraft:tinted_glass");
        rename(ModBlocks.MUD, "minecraft:mud");
        rename(ModBlocks.MANGROVE_ROOTS, "minecraft:mangrove_roots");
        rename(ModBlocks.MUDDY_MANGROVE_ROOTS, "minecraft:muddy_mangrove_roots");
        rename(ModBlocks.MOSS_BLOCK, "minecraft:moss_block");
        rename(ModBlocks.MOSS_CARPET, "minecraft:moss_carpet");
        rename(ModBlocks.DEEPSLATE_COAL_ORE, "minecraft:deepslate_coal_ore");
        rename(ModBlocks.DEEPSLATE_IRON_ORE, "minecraft:deepslate_iron_ore");
        rename(ModBlocks.DEEPSLATE_GOLD_ORE, "minecraft:deepslate_gold_ore");
        rename(ModBlocks.DEEPSLATE_REDSTONE_ORE, "minecraft:deepslate_redstone_ore");
        rename(ModBlocks.DEEPSLATE_LAPIS_ORE, "minecraft:deepslate_lapis_ore");
        rename(ModBlocks.DEEPSLATE_DIAMOND_ORE, "minecraft:deepslate_diamond_ore");
        rename(ModBlocks.DEEPSLATE_EMERALD_ORE, "minecraft:deepslate_emerald_ore");
        rename(ModBlocks.OBSERVER, "minecraft:observer");
        rename(ModBlocks.TARGET, "minecraft:target");
        rename(ModBlocks.IRON_TRAPDOOR, "minecraft:iron_trapdoor");
        rename(ModBlocks.MAGMA, "minecraft:magma_block");
        rename(ModBlocks.LANTERN, "minecraft:lantern");
        rename(ModBlocks.SOUL_LANTERN, "minecraft:soul_lantern");
        rename(ModBlocks.SMOKER, "minecraft:smoker");
        rename(ModBlocks.BLAST_FURNACE, "minecraft:blast_furnace");
        // Colour lives in the tile entity
        rename(ModBlocks.SHULKER_BOX, "minecraft:shulker_box");
        rename(ModBlocks.SMITHING_TABLE, "minecraft:smithing_table");
        rename(ModBlocks.COMPOSTER, "minecraft:composter");
        rename(ModBlocks.STONECUTTER, "minecraft:stonecutter");
        rename(ModBlocks.FLETCHING_TABLE, "minecraft:fletching_table");
        rename(ModBlocks.CARTOGRAPHY_TABLE, "minecraft:cartography_table");
        rename(ModBlocks.LOOM, "minecraft:loom");
        rename(ModBlocks.DRIPSTONE_BLOCK, "minecraft:dripstone_block");
        rename(ModBlocks.POINTED_DRIPSTONE, "minecraft:pointed_dripstone");
        rename(ModBlocks.HONEY_BLOCK, "minecraft:honey_block");
        rename(ModBlocks.HONEYCOMB_BLOCK, "minecraft:honeycomb_block");
        rename(ModBlocks.BEEHIVE, "minecraft:beehive");
        rename(ModBlocks.BEE_NEST, "minecraft:bee_nest");
        rename(ModBlocks.CHAIN, "minecraft:iron_chain");
        rename(ModBlocks.FROSTED_ICE, "minecraft:frosted_ice");
        rename(ModBlocks.LAVA_CAULDRON, "minecraft:lava_cauldron");
        rename(ModBlocks.BUBBLE_COLUMN, "minecraft:bubble_column");
        rename(ModBlocks.BARREL, "minecraft:barrel");
        rename(ModBlocks.BLACKSTONE_STAIRS, "minecraft:blackstone_stairs");
        rename(ModBlocks.POLISHED_BLACKSTONE_STAIRS, "minecraft:polished_blackstone_stairs");
        rename(ModBlocks.POLISHED_BLACKSTONE_BRICK_STAIRS, "minecraft:polished_blackstone_brick_stairs");
        rename(ModBlocks.GILDED_BLACKSTONE, "minecraft:gilded_blackstone");
        rename(ModBlocks.POLISHED_BLACKSTONE_PRESSURE_PLATE, "minecraft:polished_blackstone_pressure_plate");
        rename(ModBlocks.POLISHED_BLACKSTONE_BUTTON, "minecraft:polished_blackstone_button");
        rename(ModBlocks.SOUL_SOIL, "minecraft:soul_soil");
        rename(ModBlocks.SHROOMLIGHT, "minecraft:shroomlight");
        rename(ModBlocks.NETHER_SPROUTS, "minecraft:nether_sprouts");
        rename(ModBlocks.WEEPING_VINES, "minecraft:weeping_vines");
        rename(ModBlocks.TWISTING_VINES, "minecraft:twisting_vines");
        rename(ModBlocks.PINK_PETALS, "minecraft:pink_petals");
        rename(ModBlocks.BAMBOO_SAPLING, "minecraft:bamboo_sapling");
        rename(ModBlocks.BAMBOO, "minecraft:bamboo");
        rename(ModBlocks.BAMBOO_MOSAIC, "minecraft:bamboo_mosaic");
        rename(ModBlocks.BAMBOO_MOSAIC_SLAB, "minecraft:bamboo_mosaic_slab");
        rename(ModBlocks.BAMBOO_MOSAIC_STAIRS, "minecraft:bamboo_mosaic_stairs");
        rename(ModBlocks.CRYING_OBSIDIAN, "minecraft:crying_obsidian");
        rename(ModBlocks.SCULK, "minecraft:sculk");
        rename(ModBlocks.SCULK_CATALYST, "minecraft:sculk_catalyst");
        rename(ModBlocks.END_GATEWAY, "minecraft:end_gateway");
        rename(ModBlocks.LIGHT, "minecraft:light");
        rename(ModBlocks.BARRIER, "minecraft:barrier");
        rename(ModBlocks.GLOW_LICHEN, "minecraft:glow_lichen");
        rename(ModBlocks.CAVE_VINE, "minecraft:cave_vines");
        rename(ModBlocks.CAVE_VINE_PLANT, "minecraft:cave_vines_plant");

        for (String color : VanillaBlockIdentities.COLORS) {
            String name = color.toUpperCase();
            rename(ModBlocks.valueOf(name + "_GLAZED_TERRACOTTA"), "minecraft:" + color + "_glazed_terracotta");
            // Vanilla's bed is red
            if (!color.equals("red")) rename(ModBlocks.valueOf(name + "_BED"), "minecraft:" + color + "_bed");
        }

        // Stairs
        rename(ModBlocks.PRISMARINE_STAIRS, "minecraft:prismarine_stairs");
        rename(ModBlocks.PRISMARINE_STAIRS_BRICK, "minecraft:prismarine_brick_stairs");
        rename(ModBlocks.PRISMARINE_STAIRS_DARK, "minecraft:dark_prismarine_stairs");
        rename(ModBlocks.SMOOTH_SANDSTONE_STAIRS, "minecraft:smooth_sandstone_stairs");
        rename(ModBlocks.SMOOTH_RED_SANDSTONE_STAIRS, "minecraft:smooth_red_sandstone_stairs");
        rename(ModBlocks.SMOOTH_QUARTZ_STAIRS, "minecraft:smooth_quartz_stairs");
        rename(ModBlocks.RED_NETHERBRICK_STAIRS, "minecraft:red_nether_brick_stairs");
        rename(ModBlocks.GRANITE_STAIRS, "minecraft:granite_stairs");
        rename(ModBlocks.POLISHED_GRANITE_STAIRS, "minecraft:polished_granite_stairs");
        rename(ModBlocks.DIORITE_STAIRS, "minecraft:diorite_stairs");
        rename(ModBlocks.POLISHED_DIORITE_STAIRS, "minecraft:polished_diorite_stairs");
        rename(ModBlocks.ANDESITE_STAIRS, "minecraft:andesite_stairs");
        rename(ModBlocks.POLISHED_ANDESITE_STAIRS, "minecraft:polished_andesite_stairs");
        rename(ModBlocks.MOSSY_STONE_BRICK_STAIRS, "minecraft:mossy_stone_brick_stairs");
        rename(ModBlocks.MOSSY_COBBLESTONE_STAIRS, "minecraft:mossy_cobblestone_stairs");
        // Vanilla's stone_stairs is cobblestone stairs, this is the real one
        rename(ModBlocks.STONE_STAIRS, "minecraft:stone_stairs");
        rename(ModBlocks.END_BRICK_STAIRS, "minecraft:end_stone_brick_stairs");
        rename(ModBlocks.RED_SANDSTONE_STAIRS, "minecraft:red_sandstone_stairs");
        rename(ModBlocks.PURPUR_STAIRS, "minecraft:purpur_stairs");
        rename(ModBlocks.COBBLED_DEEPSLATE_STAIRS, "minecraft:cobbled_deepslate_stairs");
        rename(ModBlocks.POLISHED_DEEPSLATE_STAIRS, "minecraft:polished_deepslate_stairs");
        rename(ModBlocks.DEEPSLATE_BRICK_STAIRS, "minecraft:deepslate_brick_stairs");
        rename(ModBlocks.DEEPSLATE_TILE_STAIRS, "minecraft:deepslate_tile_stairs");
        rename(ModBlocks.TUFF_STAIRS, "minecraft:tuff_stairs");
        rename(ModBlocks.POLISHED_TUFF_STAIRS, "minecraft:polished_tuff_stairs");
        rename(ModBlocks.TUFF_BRICK_STAIRS, "minecraft:tuff_brick_stairs");
        rename(ModBlocks.MUD_BRICK_STAIRS, "minecraft:mud_brick_stairs");

        // Copper doors, trapdoors and stairs come in one block per age
        for (int waxed = 0; waxed < 2; waxed++) {
            for (String age : COPPER_AGES) {
                String prefix = (waxed == 1 ? "waxed_" : "") + age;
                String name = prefix.toUpperCase();
                rename(ModBlocks.valueOf(name + "COPPER_DOOR"), "minecraft:" + prefix + "copper_door");
                rename(ModBlocks.valueOf(name + "COPPER_TRAPDOOR"), "minecraft:" + prefix + "copper_trapdoor");
                rename(ModBlocks.valueOf(name + "CUT_COPPER_STAIRS"), "minecraft:" + prefix + "cut_copper_stairs");
            }
        }

        // Walls with a single type
        rename(ModBlocks.NETHER_BRICK_WALL, "minecraft:nether_brick_wall");
        rename(ModBlocks.RED_SANDSTONE_WALL, "minecraft:red_sandstone_wall");
        rename(ModBlocks.PRISMARINE_WALL, "minecraft:prismarine_wall");
        rename(ModBlocks.RED_NETHER_BRICK_WALL, "minecraft:red_nether_brick_wall");
        rename(ModBlocks.END_BRICK_WALL, "minecraft:end_stone_brick_wall");
        rename(ModBlocks.MUD_BRICK_WALL, "minecraft:mud_brick_wall");

        // Slabs with a single type (bit 3 = top half)
        rename(ModBlocks.PURPUR_SLAB, "minecraft:purpur_slab");
        rename(ModBlocks.SMOOTH_SANDSTONE_SLAB, "minecraft:smooth_sandstone_slab");
        rename(ModBlocks.SMOOTH_RED_SANDSTONE_SLAB, "minecraft:smooth_red_sandstone_slab");
        rename(ModBlocks.SMOOTH_QUARTZ_SLAB, "minecraft:smooth_quartz_slab");
        rename(ModBlocks.RED_NETHERBRICK_SLAB, "minecraft:red_nether_brick_slab");
        rename(ModBlocks.END_BRICK_SLAB, "minecraft:end_stone_brick_slab");
        rename(ModBlocks.MUD_BRICK_SLAB, "minecraft:mud_brick_slab");

        // Vanilla planks: oak is vanilla's own block
        String[] vanillaWoods = { "SPRUCE", "BIRCH", "JUNGLE", "ACACIA", "DARK_OAK" };

        for (String wood : vanillaWoods) {
            String id = "minecraft:" + wood.toLowerCase();
            rename(ModBlocks.valueOf("FENCE_" + wood), id + "_fence");
            rename(ModBlocks.valueOf("BUTTON_" + wood), id + "_button");
            rename(ModBlocks.valueOf("PRESSURE_PLATE_" + wood), id + "_pressure_plate");
            rename(ModBlocks.valueOf("FENCE_GATE_" + wood), id + "_fence_gate");
            rename(ModBlocks.valueOf("DOOR_" + wood), id + "_door");
            rename(ModBlocks.valueOf("TRAPDOOR_" + wood), id + "_trapdoor");
            rename(ModBlocks.valueOf("SIGN_" + wood), id + "_sign");
            rename(ModBlocks.valueOf("WALL_SIGN_" + wood), id + "_wall_sign");
        }

        for (String wood : MODERN_WOODS) {
            String name = wood.toUpperCase();
            String id = "minecraft:" + wood;
            rename(ModBlocks.valueOf(name + "_STAIRS"), id + "_stairs");
            rename(ModBlocks.valueOf(name + "_BUTTON"), id + "_button");
            rename(ModBlocks.valueOf(name + "_PRESSURE_PLATE"), id + "_pressure_plate");
            rename(ModBlocks.valueOf(name + "_FENCE_GATE"), id + "_fence_gate");
            rename(ModBlocks.valueOf(name + "_DOOR"), id + "_door");
            rename(ModBlocks.valueOf(name + "_TRAPDOOR"), id + "_trapdoor");
            rename(ModBlocks.valueOf(name + "_SIGN"), id + "_sign");
            rename(ModBlocks.valueOf(name + "_WALL_SIGN"), id + "_wall_sign");
        }
    }

    private static void variants() {
        // Variants start at meta 1; EFR renders the unused meta 0 as granite
        variants(ModBlocks.STONE, lookup(meta -> Math.max(1, meta % 7) - 1, (value, existing) -> value + 1),
            "minecraft:granite", "minecraft:polished_granite", "minecraft:diorite", "minecraft:polished_diorite",
            "minecraft:andesite", "minecraft:polished_andesite");
        variants(ModBlocks.PRISMARINE_BLOCK, bits(0b1111), "minecraft:prismarine", "minecraft:prismarine_bricks", "minecraft:dark_prismarine");
        variants(ModBlocks.RED_SANDSTONE, bits(0b11), "minecraft:red_sandstone", "minecraft:chiseled_red_sandstone", "minecraft:cut_red_sandstone");
        variants(ModBlocks.SPONGE, bits(0b1), "minecraft:sponge", "minecraft:wet_sponge");
        variants(ModBlocks.RED_NETHERBRICK, bits(0b1111),
            "minecraft:red_nether_bricks", "minecraft:cracked_nether_bricks", "minecraft:chiseled_nether_bricks");

        // Logs — bits 1:0 = variant, bits 3:2 = axis
        variants(ModBlocks.LOG_STRIPPED, bits(0b11),
            "minecraft:stripped_oak_log", "minecraft:stripped_spruce_log", "minecraft:stripped_birch_log", "minecraft:stripped_jungle_log");
        variants(ModBlocks.LOG2_STRIPPED, bits(0b11), "minecraft:stripped_acacia_log", "minecraft:stripped_dark_oak_log");
        variants(ModBlocks.BARK, bits(0b11), "minecraft:oak_wood", "minecraft:spruce_wood", "minecraft:birch_wood", "minecraft:jungle_wood");
        variants(ModBlocks.BARK2, bits(0b11), "minecraft:acacia_wood", "minecraft:dark_oak_wood");
        variants(ModBlocks.WOOD_STRIPPED, bits(0b11),
            "minecraft:stripped_oak_wood", "minecraft:stripped_spruce_wood", "minecraft:stripped_birch_wood", "minecraft:stripped_jungle_wood");
        variants(ModBlocks.WOOD2_STRIPPED, bits(0b11), "minecraft:stripped_acacia_wood", "minecraft:stripped_dark_oak_wood");
        variants(ModBlocks.CHERRY_LOG, bits(0b11),
            "minecraft:cherry_log", "minecraft:cherry_wood", "minecraft:stripped_cherry_log", "minecraft:stripped_cherry_wood");
        variants(ModBlocks.MANGROVE_LOG, bits(0b11),
            "minecraft:mangrove_log", "minecraft:mangrove_wood", "minecraft:stripped_mangrove_log", "minecraft:stripped_mangrove_wood");
        variants(ModBlocks.CRIMSON_STEM, bits(0b11),
            "minecraft:crimson_stem", "minecraft:crimson_hyphae", "minecraft:stripped_crimson_stem", "minecraft:stripped_crimson_hyphae");
        variants(ModBlocks.WARPED_STEM, bits(0b11),
            "minecraft:warped_stem", "minecraft:warped_hyphae", "minecraft:stripped_warped_stem", "minecraft:stripped_warped_hyphae");
        variants(ModBlocks.BAMBOO_BLOCK, bits(0b11), "minecraft:bamboo_block", "minecraft:stripped_bamboo_block");
        variants(ModBlocks.BASALT, bits(0b11), "minecraft:basalt", "minecraft:polished_basalt");

        variants(ModBlocks.CONCRETE, bits(0b1111), colored("minecraft:", "_concrete"));
        variants(ModBlocks.CONCRETE_POWDER, bits(0b1111), colored("minecraft:", "_concrete_powder"));

        // Copper blocks — bits 1:0 = age, bit 2 = cut, bit 3 = waxed
        String[] copperBlocks = new String[16];
        String[] chiseledCopper = new String[8];
        String[] copperGrates = new String[8];
        String[] copperBulbs = new String[8];
        String[] cutCopperSlabs = new String[8];

        for (int waxed = 0; waxed < 2; waxed++) {
            for (int age = 0; age < 4; age++) {
                String prefix = "minecraft:" + (waxed == 1 ? "waxed_" : "") + COPPER_AGES[age];
                copperBlocks[waxed * 8 + age] = age == 0 ? prefix + "copper_block" : prefix + "copper";
                copperBlocks[waxed * 8 + 4 + age] = prefix + "cut_copper";
                chiseledCopper[waxed * 4 + age] = prefix + "chiseled_copper";
                copperGrates[waxed * 4 + age] = prefix + "copper_grate";
                copperBulbs[waxed * 4 + age] = prefix + "copper_bulb";
                cutCopperSlabs[waxed * 4 + age] = prefix + "cut_copper_slab";
            }
        }

        variants(ModBlocks.COPPER_BLOCK, bits(0b1111), copperBlocks);
        variants(ModBlocks.CHISELED_COPPER, bits(0b1111), chiseledCopper);
        variants(ModBlocks.COPPER_GRATE, bits(0b1111), copperGrates);
        // Slabs keep the top half in bit 3, so all 8 copper variants fit in bits 2:0
        variants(ModBlocks.CUT_COPPER_SLAB, bits(0b111), cutCopperSlabs);
        aliasVariants(ModBlocks.DOUBLE_CUT_COPPER_SLAB, bits(0b111), cutCopperSlabs);
        stateForm(ModBlocks.DOUBLE_CUT_COPPER_SLAB, "type", "double");

        // Copper bulbs — bits 1:0 = age, bit 2 = lit, bit 3 = waxed
        IntegerMetaBlockProperty copperBulb = lookup(
            meta -> (meta & 3) | (meta & 8) >> 1,
            (value, existing) -> (value & 3) | (existing & 4) | (value & 4) << 1);
        variants(ModBlocks.COPPER_BULB, copperBulb, copperBulbs);
        aliasVariants(ModBlocks.POWERED_COPPER_BULB, copperBulb, copperBulbs);
        stateForm(ModBlocks.POWERED_COPPER_BULB, "powered", "true");

        variants(ModBlocks.DEEPSLATE_BRICKS, bits(0b1111),
            "minecraft:deepslate_bricks", "minecraft:cracked_deepslate_bricks", "minecraft:deepslate_tiles",
            "minecraft:cracked_deepslate_tiles", "minecraft:chiseled_deepslate");
        variants(ModBlocks.TUFF, bits(0b1111),
            "minecraft:tuff", "minecraft:polished_tuff", "minecraft:tuff_bricks", "minecraft:chiseled_tuff", "minecraft:chiseled_tuff_bricks");
        variants(ModBlocks.RAW_ORE_BLOCK, bits(0b1111), "minecraft:raw_copper_block", "minecraft:raw_iron_block", "minecraft:raw_gold_block");
        variants(ModBlocks.PACKED_MUD, bits(0b1111), "minecraft:packed_mud", "minecraft:mud_bricks");
        variants(ModBlocks.BLACKSTONE, bits(0b1111),
            "minecraft:blackstone", "minecraft:polished_blackstone", "minecraft:polished_blackstone_bricks",
            "minecraft:cracked_polished_blackstone_bricks", "minecraft:chiseled_polished_blackstone");
        variants(ModBlocks.NETHER_WART, bits(0b1111), "minecraft:nether_wart_block", "minecraft:warped_wart_block");
        variants(ModBlocks.NYLIUM, bits(0b1111), "minecraft:crimson_nylium", "minecraft:warped_nylium");
        variants(ModBlocks.NETHER_ROOTS, bits(0b1111), "minecraft:crimson_roots", "minecraft:warped_roots");
        variants(ModBlocks.NETHER_FUNGUS, bits(0b1111), "minecraft:crimson_fungus", "minecraft:warped_fungus");
        variants(ModBlocks.AZALEA, bits(0b1111), "minecraft:azalea", "minecraft:flowering_azalea");
        // Leaves — bits 1:0 = variant, bits 3:2 = decay flags
        variants(ModBlocks.AZALEA_LEAVES, bits(0b11), "minecraft:azalea_leaves", "minecraft:flowering_azalea_leaves");

        // Amethyst buds — meta % 6 = facing, metas 6+ are the bigger stage
        IntegerMetaBlockProperty budStage = lookup(meta -> meta < 6 ? 0 : 1, (value, existing) -> existing % 6 + value * 6);
        variants(ModBlocks.AMETHYST_CLUSTER_1, budStage, "minecraft:small_amethyst_bud", "minecraft:medium_amethyst_bud");
        variants(ModBlocks.AMETHYST_CLUSTER_2, budStage, "minecraft:large_amethyst_bud", "minecraft:amethyst_cluster");

        variants(ModBlocks.SOUL_TORCH, torchPlacement(), "minecraft:soul_torch", "minecraft:soul_wall_torch");

        // Slabs — bits 2:0 = variant, bit 3 = top half. Double slabs are a state of the single slab in modern.
        slabs(ModBlocks.RED_SANDSTONE_SLAB, ModBlocks.DOUBLE_RED_SANDSTONE_SLAB, "minecraft:red_sandstone_slab", "minecraft:cut_red_sandstone_slab");
        slabs(ModBlocks.STONE_SLAB, ModBlocks.DOUBLE_STONE_SLAB,
            "minecraft:stone_slab", "minecraft:mossy_cobblestone_slab", "minecraft:mossy_stone_brick_slab", "minecraft:cut_sandstone_slab");
        slabs(ModBlocks.STONE_SLAB_2, ModBlocks.DOUBLE_STONE_SLAB_2,
            "minecraft:granite_slab", "minecraft:polished_granite_slab", "minecraft:diorite_slab", "minecraft:polished_diorite_slab",
            "minecraft:andesite_slab", "minecraft:polished_andesite_slab");
        slabs(ModBlocks.PRISMARINE_SLAB, ModBlocks.DOUBLE_PRISMARINE_SLAB,
            "minecraft:prismarine_slab", "minecraft:prismarine_brick_slab", "minecraft:dark_prismarine_slab");
        slabs(ModBlocks.DEEPSLATE_SLAB, ModBlocks.DOUBLE_DEEPSLATE_SLAB, "minecraft:cobbled_deepslate_slab", "minecraft:polished_deepslate_slab");
        slabs(ModBlocks.DEEPSLATE_BRICK_SLAB, ModBlocks.DOUBLE_DEEPSLATE_BRICK_SLAB, "minecraft:deepslate_brick_slab", "minecraft:deepslate_tile_slab");
        slabs(ModBlocks.TUFF_SLAB, ModBlocks.DOUBLE_TUFF_SLAB, "minecraft:tuff_slab", "minecraft:polished_tuff_slab", "minecraft:tuff_brick_slab");
        slabs(ModBlocks.BLACKSTONE_SLAB, ModBlocks.DOUBLE_BLACKSTONE_SLAB,
            "minecraft:blackstone_slab", "minecraft:polished_blackstone_slab", "minecraft:polished_blackstone_brick_slab");

        // Walls — meta = variant
        variants(ModBlocks.STONE_WALL, bits(0b1111),
            "minecraft:stone_brick_wall", "minecraft:mossy_stone_brick_wall", "minecraft:sandstone_wall", "minecraft:brick_wall");
        variants(ModBlocks.STONE_WALL_2, bits(0b1111), "minecraft:granite_wall", "minecraft:diorite_wall", "minecraft:andesite_wall");
        variants(ModBlocks.DEEPSLATE_WALL, bits(0b1111), "minecraft:cobbled_deepslate_wall", "minecraft:polished_deepslate_wall");
        variants(ModBlocks.DEEPSLATE_BRICK_WALL, bits(0b1111), "minecraft:deepslate_brick_wall", "minecraft:deepslate_tile_wall");
        variants(ModBlocks.TUFF_WALL, bits(0b1111), "minecraft:tuff_wall", "minecraft:polished_tuff_wall", "minecraft:tuff_brick_wall");
        variants(ModBlocks.BLACKSTONE_WALL, bits(0b1111),
            "minecraft:blackstone_wall", "minecraft:polished_blackstone_wall", "minecraft:polished_blackstone_brick_wall");
    }

    /// Blocks shared by the post-1.12 wood types (crimson, warped, mangrove, cherry, bamboo), in that meta order.
    private static void woodVariants() {
        String[] planks = new String[MODERN_WOODS.length];
        String[] slabs = new String[MODERN_WOODS.length];
        String[] fences = new String[MODERN_WOODS.length];

        for (int i = 0; i < MODERN_WOODS.length; i++) {
            planks[i] = "minecraft:" + MODERN_WOODS[i] + "_planks";
            slabs[i] = "minecraft:" + MODERN_WOODS[i] + "_slab";
            fences[i] = "minecraft:" + MODERN_WOODS[i] + "_fence";
        }

        variants(ModBlocks.WOOD_PLANKS, bits(0b1111), planks);
        slabs(ModBlocks.WOOD_SLAB, ModBlocks.DOUBLE_WOOD_SLAB, slabs);
        variants(ModBlocks.WOOD_FENCE, bits(0b1111), fences);

        // Sapling — bits 2:0 = variant, bit 3 = stage
        variants(ModBlocks.SAPLING, bits(0b111), "minecraft:mangrove_propagule", "minecraft:cherry_sapling");
        // Leaves — bits 1:0 = variant, bits 3:2 = decay flags
        variants(ModBlocks.LEAVES, bits(0b11), "minecraft:mangrove_leaves", "minecraft:cherry_leaves");
    }

    /// EFR blocks that are a state of another block in modern, or that duplicate a vanilla block.
    private static void aliases() {
        alias(ModBlocks.DAYLIGHT_DETECTOR_INVERTED, "minecraft:daylight_detector");
        alias(ModBlocks.DAYLIGHT_DETECTOR, "minecraft:daylight_detector");
        alias(ModBlocks.DEEPSLATE_LIT_REDSTONE_ORE, "minecraft:deepslate_redstone_ore");
        alias(ModBlocks.LIT_SMOKER, "minecraft:smoker");
        alias(ModBlocks.LIT_BLAST_FURNACE, "minecraft:blast_furnace");
        alias(ModBlocks.DOUBLE_PURPUR_SLAB, "minecraft:purpur_slab");
        alias(ModBlocks.DOUBLE_SMOOTH_SANDSTONE_SLAB, "minecraft:smooth_sandstone_slab");
        alias(ModBlocks.DOUBLE_SMOOTH_RED_SANDSTONE_SLAB, "minecraft:smooth_red_sandstone_slab");
        alias(ModBlocks.DOUBLE_SMOOTH_QUARTZ_SLAB, "minecraft:smooth_quartz_slab");
        alias(ModBlocks.DOUBLE_RED_NETHERBRICK_SLAB, "minecraft:red_nether_brick_slab");
        alias(ModBlocks.DOUBLE_END_BRICK_SLAB, "minecraft:end_stone_brick_slab");
        alias(ModBlocks.DOUBLE_MUD_BRICK_SLAB, "minecraft:mud_brick_slab");
        alias(ModBlocks.DOUBLE_BAMBOO_MOSAIC_SLAB, "minecraft:bamboo_mosaic_slab");

        stateForm(ModBlocks.DAYLIGHT_DETECTOR_INVERTED, "inverted", "true");
        stateForm(ModBlocks.DEEPSLATE_LIT_REDSTONE_ORE, "lit", "true");
        stateForm(ModBlocks.LIT_SMOKER, "lit", "true");
        stateForm(ModBlocks.LIT_BLAST_FURNACE, "lit", "true");

        for (ModBlocks doubled : new ModBlocks[] {
            ModBlocks.DOUBLE_PURPUR_SLAB, ModBlocks.DOUBLE_SMOOTH_SANDSTONE_SLAB, ModBlocks.DOUBLE_SMOOTH_RED_SANDSTONE_SLAB,
            ModBlocks.DOUBLE_SMOOTH_QUARTZ_SLAB, ModBlocks.DOUBLE_RED_NETHERBRICK_SLAB, ModBlocks.DOUBLE_END_BRICK_SLAB,
            ModBlocks.DOUBLE_MUD_BRICK_SLAB, ModBlocks.DOUBLE_BAMBOO_MOSAIC_SLAB,
        }) {
            stateForm(doubled, "type", "double");
        }

        // Potion contents live in the tile entity; modern has no potion cauldron
        alias(ModBlocks.POTION_CAULDRON, "minecraft:water_cauldron");

        // Tile replacements for vanilla blocks
        alias(ModBlocks.BREWING_STAND, "minecraft:brewing_stand");
        alias(ModBlocks.BEACON, "minecraft:beacon");
        alias(ModBlocks.ENCHANTMENT_TABLE, "minecraft:enchanting_table");
        aliasVariants(ModBlocks.ANVIL, bits(0b1100), "minecraft:anvil", "minecraft:chipped_anvil", "minecraft:damaged_anvil");

        // Silk-touched mushroom blocks: always the all-cap texture
        alias(ModBlocks.BROWN_MUSHROOM, "minecraft:brown_mushroom_block");
        alias(ModBlocks.RED_MUSHROOM, "minecraft:red_mushroom_block");

        // Old textures for vanilla blocks
        alias(ModBlocks.ROSE, "minecraft:poppy");
        alias(ModBlocks.OLD_GRAVEL, "minecraft:gravel");
    }

    private static void slabs(ModBlocks single, ModBlocks doubled, String... modernIds) {
        variants(single, bits(0b111), modernIds);
        aliasVariants(doubled, bits(0b111), modernIds);
        stateForm(doubled, "type", "double");
    }

    private static void rename(ModBlocks block, String modernId) {
        if (block.isEnabled()) BLOCKS.rename(block.get(), modernId);
    }

    private static void stateForm(ModBlocks block, String property, String value) {
        if (block.isEnabled()) BLOCKS.stateForm(block.get(), property, value);
    }

    private static void alias(ModBlocks block, String modernId) {
        if (block.isEnabled()) BLOCKS.alias(block.get(), modernId);
    }

    private static void variants(ModBlocks block, IntegerMetaBlockProperty backing, String... modernIds) {
        if (block.isEnabled()) BLOCKS.setVariants(block.get(), backing, modernIds);
    }

    private static void aliasVariants(ModBlocks block, IntegerMetaBlockProperty backing, String... modernIds) {
        if (block.isEnabled()) BLOCKS.aliasVariants(block.get(), backing, modernIds);
    }
}
