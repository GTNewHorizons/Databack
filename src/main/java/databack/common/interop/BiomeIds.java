package databack.common.interop;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.world.biome.BiomeGenBase;

public class BiomeIds {

    private static final Map<BiomeGenBase, String> registry = new HashMap<>();

    static {
        register(BiomeGenBase.ocean, "minecraft:ocean");
        register(BiomeGenBase.plains, "minecraft:plains");
        register(BiomeGenBase.desert, "minecraft:desert");
        register(BiomeGenBase.extremeHills, "minecraft:extreme_hills");
        register(BiomeGenBase.forest, "minecraft:forest");
        register(BiomeGenBase.taiga, "minecraft:taiga");
        register(BiomeGenBase.swampland, "minecraft:swampland");
        register(BiomeGenBase.river, "minecraft:river");
        register(BiomeGenBase.hell, "minecraft:hell");
        register(BiomeGenBase.sky, "minecraft:sky");
        register(BiomeGenBase.frozenOcean, "minecraft:frozen_ocean");
        register(BiomeGenBase.frozenRiver, "minecraft:frozen_river");
        register(BiomeGenBase.icePlains, "minecraft:ice_plains");
        register(BiomeGenBase.iceMountains, "minecraft:ice_mountains");
        register(BiomeGenBase.mushroomIsland, "minecraft:mushroom_island");
        register(BiomeGenBase.mushroomIslandShore, "minecraft:mushroom_island_shore");
        register(BiomeGenBase.beach, "minecraft:beach");
        register(BiomeGenBase.desertHills, "minecraft:desert_hills");
        register(BiomeGenBase.forestHills, "minecraft:forest_hills");
        register(BiomeGenBase.taigaHills, "minecraft:taiga_hills");
        register(BiomeGenBase.extremeHillsEdge, "minecraft:extreme_hills_edge");
        register(BiomeGenBase.jungle, "minecraft:jungle");
        register(BiomeGenBase.jungleHills, "minecraft:jungle_hills");
        register(BiomeGenBase.jungleEdge, "minecraft:jungle_edge");
        register(BiomeGenBase.deepOcean, "minecraft:deep_ocean");
        register(BiomeGenBase.stoneBeach, "minecraft:stone_beach");
        register(BiomeGenBase.coldBeach, "minecraft:cold_beach");
        register(BiomeGenBase.birchForest, "minecraft:birch_forest");
        register(BiomeGenBase.birchForestHills, "minecraft:birch_forest_hills");
        register(BiomeGenBase.roofedForest, "minecraft:roofed_forest");
        register(BiomeGenBase.coldTaiga, "minecraft:cold_taiga");
        register(BiomeGenBase.coldTaigaHills, "minecraft:cold_taiga_hills");
        register(BiomeGenBase.megaTaiga, "minecraft:mega_taiga");
        register(BiomeGenBase.megaTaigaHills, "minecraft:mega_taiga_hills");
        register(BiomeGenBase.extremeHillsPlus, "minecraft:extreme_hills_plus");
        register(BiomeGenBase.savanna, "minecraft:savanna");
        register(BiomeGenBase.savannaPlateau, "minecraft:savanna_plateau");
        register(BiomeGenBase.mesa, "minecraft:mesa");
        register(BiomeGenBase.mesaPlateau_F, "minecraft:mesa_plateau_f");
        register(BiomeGenBase.mesaPlateau, "minecraft:mesa_plateau");
    }

    public static void register(BiomeGenBase biome, String id) {
        registry.put(biome, id);
    }

    public static String getBiomeId(BiomeGenBase biome) {
        String id = registry.get(biome);

        if (id != null) return id;

        return "unknown:" + biome.biomeName;
    }
}
