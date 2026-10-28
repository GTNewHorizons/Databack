package databack.common.biome;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.util.ResourceLocation;
import net.minecraft.world.biome.BiomeGenBase;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import databack.common.interop.registry.ProxyBiomeRegistry;

/// Modern vanilla biome ids whose 1.7 vanilla counterpart has a different name. Only vanilla needs this: modded
/// datapack biomes have no 1.7 counterpart. Biomes whose names match need no alias (e.g. `minecraft:plains`).
public final class VanillaBiomeAliases {

    private static final Logger LOGGER = LogManager.getLogger("databack-biomes");

    /// Modern id to the id [ProxyBiomeRegistry] gives the 1.7 biome.
    public static final Map<String, String> ALIASES;

    static {
        Map<String, String> aliases = new LinkedHashMap<>();
        aliases.put("minecraft:snowy_plains", "minecraft:ice_plains");
        aliases.put("minecraft:ice_spikes", "minecraft:ice_plains_spikes");
        aliases.put("minecraft:snowy_taiga", "minecraft:cold_taiga");
        aliases.put("minecraft:snowy_beach", "minecraft:cold_beach");
        aliases.put("minecraft:old_growth_pine_taiga", "minecraft:mega_taiga");
        aliases.put("minecraft:old_growth_spruce_taiga", "minecraft:mega_spruce_taiga");
        aliases.put("minecraft:old_growth_birch_forest", "minecraft:birch_forest_m");
        aliases.put("minecraft:dark_forest", "minecraft:roofed_forest");
        aliases.put("minecraft:swamp", "minecraft:swampland");
        aliases.put("minecraft:sparse_jungle", "minecraft:jungle_edge");
        aliases.put("minecraft:windswept_hills", "minecraft:extreme_hills");
        aliases.put("minecraft:windswept_forest", "minecraft:extreme_hills+");
        aliases.put("minecraft:windswept_gravelly_hills", "minecraft:extreme_hills_m");
        aliases.put("minecraft:windswept_savanna", "minecraft:savanna_m");
        aliases.put("minecraft:badlands", "minecraft:mesa");
        aliases.put("minecraft:wooded_badlands", "minecraft:mesa_plateau_f");
        aliases.put("minecraft:eroded_badlands", "minecraft:mesa_(bryce)");
        aliases.put("minecraft:mushroom_fields", "minecraft:mushroom_island");
        aliases.put("minecraft:stony_shore", "minecraft:stone_beach");
        aliases.put("minecraft:nether_wastes", "minecraft:hell");
        aliases.put("minecraft:the_end", "minecraft:sky");
        ALIASES = Collections.unmodifiableMap(aliases);
    }

    private VanillaBiomeAliases() {}

    /// Re-registers each aliased 1.7 biome in `registry` under its modern id (its 1.7 id is dropped). Assumes every
    /// 1.7 biome has been registered.
    public static void apply(@NotNull ProxyBiomeRegistry registry) {
        for (Map.Entry<String, String> alias : ALIASES.entrySet()) {
            BiomeGenBase biome = registry.getObject(new ResourceLocation(alias.getValue()));
            if (biome == null) {
                LOGGER.warn("No 1.7 biome {} to alias as {}", alias.getValue(), alias.getKey());
                continue;
            }
            registry.putBiomeReplacing(new ResourceLocation(alias.getKey()), biome);
        }
    }
}
