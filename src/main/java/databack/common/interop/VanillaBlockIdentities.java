package databack.common.interop;

import static com.gtnewhorizon.gtnhlib.blockstate.registry.BlockPropertyRegistry.registerProperty;

import java.util.Arrays;
import java.util.function.IntBinaryOperator;
import java.util.function.IntUnaryOperator;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;

import com.gtnewhorizon.gtnhlib.blockstate.properties.IntegerBlockProperty;
import com.gtnewhorizon.gtnhlib.blockstate.properties.IntegerBlockProperty.IntegerMetaBlockProperty;
import databack.common.interop.modern_block.FunctionalMetaProperty;
import databack.common.interop.modern_block.BlockVariant;
import databack.common.interop.modern_block.VariantBlockProperty;
import databack.common.interop.registry.BlockIdentityRegistry;
import databack.common.interop.registry.ItemIdentityRegistry;

public class VanillaBlockIdentities {

    public enum TreeType1 implements BlockVariant {
        oak,
        spruce,
        birch,
        jungle;
    }

    public enum TreeType2 implements BlockVariant {
        acacia,
        dark_oak;
    }

    public enum TreeType3 implements BlockVariant {
        oak,
        spruce,
        birch,
        jungle,
        acacia,
        dark_oak;
    }

    public enum FlowerVariant implements BlockVariant {
        poppy,
        blue_orchid,
        allium,
        azure_bluet,
        red_tulip,
        orange_tulip,
        white_tulip,
        pink_tulip,
        oxeye_daisy;
    }

    public enum TallGrassVariant implements BlockVariant {
        dead_bush,
        short_grass,
        fern;
    }

    public enum DoublePlantVariant implements BlockVariant {
        sunflower,
        lilac, // aka syringa
        tall_grass,
        large_fern,
        rose_bush,
        peony;
    }

    public enum DirtVariant implements BlockVariant {
        dirt,
        coarse_dirt,
        podzol;
    }

    public static void init() {
        var blocks = BlockIdentityRegistry.INSTANCE;
        var items = ItemIdentityRegistry.INSTANCE;

        var treeType1 = new VariantBlockProperty<>(TreeType1.class, "variant", 0b11);
        var treeType2 = new VariantBlockProperty<>(TreeType2.class, "variant", 0b11);
        var treeType3 = new VariantBlockProperty<>(TreeType3.class, "variant", 0b111);

        // Logs — bits 1:0 = variant, bits 3:2 = axis (handled by BlockRotatedPillar)
        registerProperty(Blocks.log, treeType1);
        registerProperty(Blocks.log2, treeType2);

        blocks.setVariantProperty(Blocks.log, treeType1, variant -> "minecraft:" + variant + "_log");
        blocks.setVariantProperty(Blocks.log2, treeType2, variant -> "minecraft:" + variant + "_log");
        items.setVariantProperty(Blocks.log, treeType1, variant -> "minecraft:" + variant + "_log");
        items.setVariantProperty(Blocks.log2, treeType2, variant -> "minecraft:" + variant + "_log");

        // Leaves — bits 1:0 = variant, bit 2 = decayable, bit 3 = check_decay
        registerProperty(Blocks.leaves, treeType1);
        registerProperty(Blocks.leaves2, treeType2);

        blocks.setVariantProperty(Blocks.leaves, treeType1, variant -> "minecraft:" + variant + "_leaves");
        blocks.setVariantProperty(Blocks.leaves2, treeType2, variant -> "minecraft:" + variant + "_leaves");
        items.setVariantProperty(Blocks.leaves, treeType1, variant -> "minecraft:" + variant + "_leaves");
        items.setVariantProperty(Blocks.leaves2, treeType2, variant -> "minecraft:" + variant + "_leaves");

        // Saplings — bits 2:0 = type, bit 3 = stage
        registerProperty(Blocks.sapling, treeType3);
        blocks.setVariantProperty(Blocks.sapling, treeType3, variant -> "minecraft:" + variant + "_sapling");
        items.setVariantProperty(Blocks.sapling, treeType3, variant -> "minecraft:" + variant + "_sapling");

        // Planks — bits 2:0 = variant
        registerProperty(Blocks.planks, treeType3);
        blocks.setVariantProperty(Blocks.planks, treeType3, variant -> "minecraft:" + variant + "_planks");
        items.setVariantProperty(Blocks.planks, treeType3, variant -> "minecraft:" + variant + "_planks");

        // Red flowers — bits 3:0 = type
        var flowerVariant = new VariantBlockProperty<>(FlowerVariant.class, "variant", 0b1111);
        registerProperty(Blocks.red_flower, flowerVariant);
        blocks.setVariantProperty(Blocks.red_flower, flowerVariant, variant -> "minecraft:" + variant);
        items.setVariantProperty(Blocks.red_flower, flowerVariant, variant -> "minecraft:" + variant);

        // Tall grass — bits 1:0 = type
        var tallGrassVariant = new VariantBlockProperty<>(TallGrassVariant.class, "variant", 0b11);
        registerProperty(Blocks.tallgrass, tallGrassVariant);
        blocks.setVariantProperty(Blocks.tallgrass, tallGrassVariant, variant -> "minecraft:" + variant);
        items.setVariantProperty(Blocks.tallgrass, tallGrassVariant, variant -> "minecraft:" + variant);

        // Double plants — bits 2:0 = variant, bit 3 = half (upper)
        var doublePlantVariant = new VariantBlockProperty<>(DoublePlantVariant.class, "variant", 0b111);
        registerProperty(Blocks.double_plant, doublePlantVariant);
        blocks.setVariantProperty(Blocks.double_plant, doublePlantVariant, variant -> "minecraft:" + variant);
        items.setVariantProperty(Blocks.double_plant, doublePlantVariant, variant -> "minecraft:" + variant);

        // Dirt — bits 1:0 = variant
        var dirtVariant = new VariantBlockProperty<>(DirtVariant.class, "variant", 0b11);
        registerProperty(Blocks.dirt, dirtVariant);
        blocks.setVariantProperty(Blocks.dirt, dirtVariant, variant -> "minecraft:" + variant);
        items.setVariantProperty(Blocks.dirt, dirtVariant, variant -> "minecraft:" + variant);

        // Pure renames
        blocks.rename(Blocks.grass, "minecraft:grass_block");
        blocks.rename(Blocks.reeds, "minecraft:sugar_cane");
        blocks.rename(Blocks.melon_block, "minecraft:melon");
        blocks.rename(Blocks.lit_pumpkin, "minecraft:jack_o_lantern");
        blocks.rename(Blocks.yellow_flower, "minecraft:dandelion");
        blocks.rename(Blocks.web, "minecraft:cobweb");
        blocks.rename(Blocks.deadbush, "minecraft:dead_bush");
        blocks.rename(Blocks.waterlily, "minecraft:lily_pad");

        items.rename(Blocks.grass, "minecraft:grass_block");
        items.rename(Blocks.reeds, "minecraft:sugar_cane");
        items.rename(Blocks.melon_block, "minecraft:melon");
        items.rename(Blocks.yellow_flower, "minecraft:dandelion");
        items.rename(Blocks.web, "minecraft:cobweb");
        items.rename(Blocks.deadbush, "minecraft:dead_bush");
        items.rename(Blocks.waterlily, "minecraft:lily_pad");

        initBlocks(blocks);
    }

    public static final String[] COLORS = {
        "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
        "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black",
    };

    /// A variant index stored in the bits selected by `mask`.
    public static IntegerMetaBlockProperty bits(int mask) {
        return (IntegerMetaBlockProperty) IntegerBlockProperty.meta("variant", mask, Integer.numberOfTrailingZeros(mask));
    }

    /// A variant index for metas that don't fit a single bit range.
    public static IntegerMetaBlockProperty lookup(IntUnaryOperator decode, IntBinaryOperator encode) {
        return new FunctionalMetaProperty("variant", decode, encode);
    }

    /// `prefix + color + suffix` for each of the 16 dye colours, in meta order.
    public static String[] colored(String prefix, String suffix) {
        String[] ids = new String[COLORS.length];

        for (int i = 0; i < COLORS.length; i++) {
            ids[i] = prefix + COLORS[i] + suffix;
        }

        return ids;
    }

    /// Torches: metas 1-4 are attached to a wall, 5 (and 0) stand on the floor.
    public static IntegerMetaBlockProperty torchPlacement() {
        return lookup(
            meta -> meta >= 1 && meta <= 4 ? 1 : 0,
            (value, existing) -> value == 0 ? 5 : (existing >= 1 && existing <= 4 ? existing : 1));
    }

    private static void initBlocks(BlockIdentityRegistry blocks) {
        // Some 1.7 names are also modern names for a different block, so the renames below must claim them
        blocks.rename(Blocks.snow, "minecraft:snow_block");
        blocks.rename(Blocks.snow_layer, "minecraft:snow");
        blocks.rename(Blocks.stone_stairs, "minecraft:cobblestone_stairs");

        blocks.rename(Blocks.noteblock, "minecraft:note_block");
        blocks.rename(Blocks.bed, "minecraft:red_bed");
        blocks.rename(Blocks.golden_rail, "minecraft:powered_rail");
        blocks.rename(Blocks.piston_extension, "minecraft:moving_piston");
        blocks.rename(Blocks.brick_block, "minecraft:bricks");
        blocks.rename(Blocks.mob_spawner, "minecraft:spawner");
        blocks.rename(Blocks.standing_sign, "minecraft:oak_sign");
        blocks.rename(Blocks.wall_sign, "minecraft:oak_wall_sign");
        blocks.rename(Blocks.wooden_door, "minecraft:oak_door");
        blocks.rename(Blocks.wooden_pressure_plate, "minecraft:oak_pressure_plate");
        blocks.rename(Blocks.wooden_button, "minecraft:oak_button");
        blocks.rename(Blocks.fence, "minecraft:oak_fence");
        blocks.rename(Blocks.fence_gate, "minecraft:oak_fence_gate");
        blocks.rename(Blocks.trapdoor, "minecraft:oak_trapdoor");
        blocks.rename(Blocks.portal, "minecraft:nether_portal");
        blocks.rename(Blocks.nether_brick, "minecraft:nether_bricks");
        blocks.rename(Blocks.quartz_ore, "minecraft:nether_quartz_ore");
        blocks.rename(Blocks.hardened_clay, "minecraft:terracotta");
        blocks.rename(Blocks.unpowered_repeater, "minecraft:repeater");
        blocks.rename(Blocks.unpowered_comparator, "minecraft:comparator");

        // Modern's other airs: cave air only marks carved space, void air lies outside the world
        blocks.redirect("minecraft:cave_air", Blocks.air);
        blocks.redirect("minecraft:void_air", Blocks.air);

        // Meta is the fluid level, as in modern: 0 is a source, 1-7 flow, 8 and up fall
        for (Block fluid : new Block[] { Blocks.water, Blocks.flowing_water, Blocks.lava, Blocks.flowing_lava }) {
            registerProperty(fluid, IntegerBlockProperty.meta("level", 0b1111, 0));
        }

        // States of another block in modern
        blocks.alias(Blocks.flowing_water, "minecraft:water");
        blocks.alias(Blocks.flowing_lava, "minecraft:lava");
        blocks.alias(Blocks.lit_furnace, "minecraft:furnace");
        blocks.alias(Blocks.lit_redstone_ore, "minecraft:redstone_ore");
        blocks.alias(Blocks.lit_redstone_lamp, "minecraft:redstone_lamp");
        blocks.alias(Blocks.powered_repeater, "minecraft:repeater");
        blocks.alias(Blocks.powered_comparator, "minecraft:comparator");

        blocks.setVariants(Blocks.torch, torchPlacement(), "minecraft:torch", "minecraft:wall_torch");
        blocks.setVariants(Blocks.redstone_torch, torchPlacement(), "minecraft:redstone_torch", "minecraft:redstone_wall_torch");
        blocks.aliasVariants(Blocks.unlit_redstone_torch, torchPlacement(), "minecraft:redstone_torch", "minecraft:redstone_wall_torch");

        blocks.stateForm(Blocks.lit_furnace, "lit", "true");
        blocks.stateForm(Blocks.lit_redstone_ore, "lit", "true");
        blocks.stateForm(Blocks.lit_redstone_lamp, "lit", "true");
        blocks.stateForm(Blocks.powered_repeater, "powered", "true");
        blocks.stateForm(Blocks.powered_comparator, "powered", "true");
        blocks.stateForm(Blocks.unlit_redstone_torch, "lit", "false");

        blocks.setVariants(Blocks.sand, bits(0b1), "minecraft:sand", "minecraft:red_sand");
        blocks.setVariants(Blocks.sandstone, bits(0b11), "minecraft:sandstone", "minecraft:chiseled_sandstone", "minecraft:cut_sandstone");
        blocks.setVariants(Blocks.stonebrick, bits(0b11),
            "minecraft:stone_bricks", "minecraft:mossy_stone_bricks", "minecraft:cracked_stone_bricks", "minecraft:chiseled_stone_bricks");
        blocks.setVariants(Blocks.monster_egg, bits(0b111),
            "minecraft:infested_stone", "minecraft:infested_cobblestone", "minecraft:infested_stone_bricks",
            "minecraft:infested_mossy_stone_bricks", "minecraft:infested_cracked_stone_bricks", "minecraft:infested_chiseled_stone_bricks");
        blocks.setVariants(Blocks.cobblestone_wall, bits(0b1), "minecraft:cobblestone_wall", "minecraft:mossy_cobblestone_wall");
        blocks.setVariants(Blocks.anvil, bits(0b1100), "minecraft:anvil", "minecraft:chipped_anvil", "minecraft:damaged_anvil");

        // 0 = plain, 1 = chiseled, 2-4 = pillar (axis)
        blocks.setVariants(Blocks.quartz_block, lookup(
                meta -> Math.min(meta, 2),
                (value, existing) -> value < 2 ? value : (existing >= 2 && existing <= 4 ? existing : 2)),
            "minecraft:quartz_block", "minecraft:chiseled_quartz_block", "minecraft:quartz_pillar");

        blocks.setVariants(Blocks.wool, bits(0b1111), colored("minecraft:", "_wool"));
        blocks.setVariants(Blocks.carpet, bits(0b1111), colored("minecraft:", "_carpet"));
        blocks.setVariants(Blocks.stained_glass, bits(0b1111), colored("minecraft:", "_stained_glass"));
        blocks.setVariants(Blocks.stained_glass_pane, bits(0b1111), colored("minecraft:", "_stained_glass_pane"));
        blocks.setVariants(Blocks.stained_hardened_clay, bits(0b1111), colored("minecraft:", "_terracotta"));

        // Metas 10 and 15 are the stem; the rest are cap faces
        IntegerMetaBlockProperty mushroomStem = lookup(
            meta -> meta == 10 || meta == 15 ? 1 : 0,
            (value, existing) -> value == 1 ? 15 : (existing == 10 || existing == 15 ? 14 : existing));
        blocks.setVariants(Blocks.brown_mushroom_block, mushroomStem, "minecraft:brown_mushroom_block", "minecraft:mushroom_stem");
        blocks.setVariants(Blocks.red_mushroom_block, mushroomStem, "minecraft:red_mushroom_block", "minecraft:mushroom_stem");

        // Meta is the water level
        blocks.setVariants(Blocks.cauldron, lookup(
                meta -> meta == 0 ? 0 : 1,
                (value, existing) -> value == 0 ? 0 : (existing == 0 ? 1 : existing)),
            "minecraft:cauldron", "minecraft:water_cauldron");

        // The skull type lives in the tile entity; meta 1 is the floor, 2-5 are walls, bit 3 is no-drop
        blocks.setVariants(Blocks.skull, lookup(
                meta -> (meta & 7) <= 1 ? 0 : 1,
                (value, existing) -> value == 0 ? (existing & 8) | 1 : ((existing & 7) >= 2 ? existing : (existing & 8) | 2)),
            "minecraft:skeleton_skull", "minecraft:skeleton_wall_skull");

        // Slabs — bits 2:0 = variant, bit 3 = top half
        String[] stoneSlabs = {
            "minecraft:smooth_stone_slab", "minecraft:sandstone_slab", "minecraft:petrified_oak_slab", "minecraft:cobblestone_slab",
            "minecraft:brick_slab", "minecraft:stone_brick_slab", "minecraft:nether_brick_slab", "minecraft:quartz_slab",
        };
        String[] woodSlabs = {
            "minecraft:oak_slab", "minecraft:spruce_slab", "minecraft:birch_slab",
            "minecraft:jungle_slab", "minecraft:acacia_slab", "minecraft:dark_oak_slab",
        };

        // Double stone slab metas 8, 9 and 15 are the 'seamless' full blocks. Registered before the single slab so the
        // single slab ends up owning the slab ids.
        String[] doubleStoneSlabs = Arrays.copyOf(stoneSlabs, 11);
        doubleStoneSlabs[8] = "minecraft:smooth_stone";
        doubleStoneSlabs[9] = "minecraft:smooth_sandstone";
        doubleStoneSlabs[10] = "minecraft:smooth_quartz";
        blocks.setVariants(Blocks.double_stone_slab, lookup(
                meta -> meta == 8 || meta == 9 ? meta : meta == 15 ? 10 : meta & 7,
                (value, existing) -> value < 10 ? value : 15),
            doubleStoneSlabs);
        blocks.setVariants(Blocks.stone_slab, bits(0b111), stoneSlabs);

        blocks.setVariants(Blocks.wooden_slab, bits(0b111), woodSlabs);
        blocks.aliasVariants(Blocks.double_wooden_slab, bits(0b111), woodSlabs);

        // After the single slabs claimed the slab ids, so only those become double forms (not smooth_stone etc)
        blocks.stateForm(Blocks.double_stone_slab, "type", "double");
        blocks.stateForm(Blocks.double_wooden_slab, "type", "double");
    }
}
