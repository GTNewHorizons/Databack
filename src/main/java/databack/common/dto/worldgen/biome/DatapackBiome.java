package databack.common.dto.worldgen.biome;

import java.util.Map;

import org.jetbrains.annotations.Nullable;

import com.gtnewhorizon.gtnhlib.color.ImmutableColor;
import databack.common.annotation.RangeFloat;

public class DatapackBiome {

    public float temperature;
    public float downfall;
    public boolean has_precipitation;
    @Nullable
    public TemperatureModifier temperature_modifier;
    @RangeFloat(min = 0, max = 1)
    public float creature_spawn_probability;
    public BiomeEffects effects;
    public Map<MobCategory, SpawnerData> spawners;
    public Map<String, MobSpawnCost> spawn_costs;
    public Void carvers; // TODO: fill in this type
    public Void features; // TODO: fill in this type

    public enum TemperatureModifier {
        none,
        frozen;
    }

    public static class BiomeEffects {
        public ImmutableColor water_color;
        public ImmutableColor grass_color;
        public ImmutableColor foliage_color;
        public ImmutableColor dry_foliage_color;
        public GrassColorModifier grass_color_modifier;
    }

    public enum GrassColorModifier {
        none,
        dark_forest,
        swamp;
    }

    public enum MobCategory {
        monster,
        creature,
        ambient,
        axolotls,
        underground_water_creature,
        water_creature,
        water_ambient,
        misc;
    }

    public static class SpawnerData {
        public String type;
        public int weight;
        public int minCount;
        public int maxCount;
    }

    public static class MobSpawnCost {
        public double energy_budget;
        public double charge;
    }

}
