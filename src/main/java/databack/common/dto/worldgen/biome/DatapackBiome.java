package databack.common.dto.worldgen.biome;

import java.util.List;
import java.util.Map;

import javax.annotation.Nonnegative;

import org.jetbrains.annotations.Nullable;

import com.gtnewhorizon.gtnhlib.color.ImmutableColor;
import databack.common.annotation.RangeFloat;
import databack.common.dto.SoundEventRef;
import databack.common.dto.particle.IParticle;
import databack.common.dto.worldgen.biome.BuiltinBiomeAttributes.RGBBiomeAttribute;
import databack.common.dto.worldgen.carver.BiomeCarvers;

@SuppressWarnings("unused")
public class DatapackBiome {

    public float temperature;
    public float downfall;
    public boolean has_precipitation;
    /** PositionalEnvironmentAttributeMap — no Java equivalent yet. @since 1.21.11 */
    @Nullable
    public EnvironmentEffects attributes;

    @Nullable
    public TemperatureModifier temperature_modifier;
    @RangeFloat(min = 0, max = 1)
    public float creature_spawn_probability;
    public BiomeEffects effects;
    public Map<MobCategory, List<SpawnerData>> spawners;
    public Map<String, MobSpawnCost> spawn_costs;
    public BiomeCarvers carvers;
    public PlacedFeatureSet[][] features;

    public boolean hasFeature(String featureId) {
        if (features == null) return false;
        for (PlacedFeatureSet[] step : features) {
            for (PlacedFeatureSet entry : step) {
                if (entry.containsFeature(featureId)) return true;
            }
        }
        return false;
    }

    public enum TemperatureModifier {
        none,
        frozen
    }

    public static class BiomeEffects {
        public RGBBiomeAttribute water_color;
        @Nullable
        public ImmutableColor grass_color;
        @Nullable
        public ImmutableColor foliage_color;
        @Nullable
        public ImmutableColor dry_foliage_color;
        @Nullable
        public GrassColorModifier grass_color_modifier;
        @Nullable
        public ImmutableColor sky_color;
        @Nullable
        public ImmutableColor fog_color;
        @Nullable
        public ImmutableColor water_fog_color;
        @Nullable
        public String ambient_sound;
        @Nullable
        public MoodSound mood_sound;
        @Nullable
        public BiomeSoundAdditions additions_sound;
        @Nullable
        public BiomeMusicList music;
        @Nullable
        public Float music_volume;
        @Nullable
        public BiomeParticle particle;
    }

    public enum GrassColorModifier {
        none,
        dark_forest,
        swamp
    }

    public static class MoodSound {
        public SoundEventRef sound;
        @Nonnegative
        public int tick_delay;
        @Nonnegative
        public int block_search_extent;
        public float offset;
    }

    public static class BiomeSoundAdditions {
        public SoundEventRef sound;
        @RangeFloat(min = 0, max = 1)
        public float tick_chance;
    }

    public static class BiomeMusic {
        public SoundEventRef sound;
        @Nonnegative
        public int min_delay;
        @Nonnegative
        public int max_delay;
        @Nullable
        public Boolean replace_current_music;
    }

    public static class WeightedBiomeMusic {
        public int weight;
        public BiomeMusic data;
    }

    /** Normalized music entries from a WeightedList&lt;BiomeMusic&gt; JSON array. */
    public static class BiomeMusicList {

        public final java.util.List<WeightedBiomeMusic> entries;

        BiomeMusicList(java.util.List<WeightedBiomeMusic> entries) {
            this.entries = entries;
        }

    }

    public static class BiomeParticle {
        public IParticle options;
        @RangeFloat(min = 0, max = 1)
        public float probability;
    }

    public enum MobCategory {
        monster,
        creature,
        ambient,
        axolotls,
        underground_water_creature,
        water_creature,
        water_ambient,
        misc
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
