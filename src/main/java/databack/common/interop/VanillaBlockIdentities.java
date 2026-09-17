package databack.common.interop;

import static com.gtnewhorizon.gtnhlib.blockstate.registry.BlockPropertyRegistry.registerProperty;

import net.minecraft.init.Blocks;

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
        fern,
        rose,
        paeonia;
    }

    public enum DirtVariant implements BlockVariant {
        dirt,
        coarse_dirt,
        podzol;
    }

    public static void initVanilla() {
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
    }
}
