package databack.common.dto.worldgen.biome;

import java.util.List;

import net.minecraft.util.IChatComponent;

import org.jetbrains.annotations.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.annotations.SerializedName;

import databack.common.dto.SoundEventRef;

import databack.common.annotation.RangeFloat;
import databack.common.dto.particle.IParticle;
import databack.common.dto.worldgen.biome.BuiltinBiomeAttributes.BooleanBiomeAttribute;
import databack.common.dto.worldgen.biome.BuiltinBiomeAttributes.FloatBiomeAttribute;
import databack.common.dto.worldgen.biome.BuiltinBiomeAttributes.RGBABiomeAttribute;
import databack.common.dto.worldgen.biome.BuiltinBiomeAttributes.RGBBiomeAttribute;

@SuppressWarnings("unused")
public class EnvironmentEffects {

    @SerializedName("minecraft:visual/cloud_height")
    public FloatBiomeAttribute cloud_height;

    @SerializedName("minecraft:visual/fog_start_distance")
    public FloatBiomeAttribute fog_start_distance;

    @SerializedName("minecraft:visual/moon_angle")
    public FloatBiomeAttribute moon_angle;

    @SerializedName("minecraft:visual/star_angle")
    public FloatBiomeAttribute star_angle;

    @SerializedName("minecraft:visual/sun_angle")
    public FloatBiomeAttribute sun_angle;

    @SerializedName("minecraft:visual/water_fog_start_distance")
    public FloatBiomeAttribute water_fog_start_distance;

    @SerializedName("minecraft:visual/cloud_fog_end_distance")
    public FloatBiomeAttribute cloud_fog_end_distance;

    @SerializedName("minecraft:visual/fog_end_distance")
    public FloatBiomeAttribute fog_end_distance;

    @SerializedName("minecraft:visual/sky_fog_end_distance")
    public FloatBiomeAttribute sky_fog_end_distance;

    @SerializedName("minecraft:visual/water_fog_end_distance")
    public FloatBiomeAttribute water_fog_end_distance;

    @SerializedName("minecraft:visual/sky_light_factor")
    public FloatBiomeAttribute sky_light_factor;

    @SerializedName("minecraft:visual/star_brightness")
    public FloatBiomeAttribute star_brightness;

    @SerializedName("minecraft:audio/music_volume")
    public FloatBiomeAttribute music_volume;

    @SerializedName("minecraft:gameplay/cat_waking_up_gift_chance")
    public FloatBiomeAttribute cat_waking_up_gift_chance;

    @SerializedName("minecraft:gameplay/surface_slime_spawn_chance")
    public FloatBiomeAttribute surface_slime_spawn_chance;

    @SerializedName("minecraft:gameplay/turtle_egg_hatch_chance")
    public FloatBiomeAttribute turtle_egg_hatch_chance;

    @SerializedName("minecraft:gameplay/sky_light_level")
    public FloatBiomeAttribute sky_light_level;

    @SerializedName("minecraft:visual/cloud_color")
    public RGBABiomeAttribute cloud_color;

    @SerializedName("minecraft:visual/sunrise_sunset_color")
    public RGBABiomeAttribute sunrise_sunset_color;

    @SerializedName("minecraft:visual/ambient_light_color")
    public RGBBiomeAttribute ambient_light_color;

    @SerializedName("minecraft:visual/block_light_tint")
    public RGBBiomeAttribute block_light_tint;

    @SerializedName("minecraft:visual/night_vision_color")
    public RGBBiomeAttribute night_vision_color;

    @SerializedName("minecraft:visual/fog_color")
    public RGBBiomeAttribute fog_color;

    @SerializedName("minecraft:visual/water_fog_color")
    public RGBBiomeAttribute water_fog_color;

    @SerializedName("minecraft:visual/sky_color")
    public RGBBiomeAttribute sky_color;

    @SerializedName("minecraft:visual/sky_light_color")
    public RGBBiomeAttribute sky_light_color;

    @SerializedName("minecraft:audio/firefly_bush_sounds")
    public BooleanBiomeAttribute firefly_bush_sounds;

    @SerializedName("minecraft:gameplay/bees_stay_in_hive")
    public BooleanBiomeAttribute bees_stay_in_hive;

    @SerializedName("minecraft:gameplay/can_pillager_patrol_spawn")
    public BooleanBiomeAttribute can_pillager_patrol_spawn;

    @SerializedName("minecraft:gameplay/can_start_raid")
    public BooleanBiomeAttribute can_start_raid;

    @SerializedName("minecraft:gameplay/creaking_active")
    public BooleanBiomeAttribute creaking_active;

    @SerializedName("minecraft:gameplay/fast_lava")
    public BooleanBiomeAttribute fast_lava;

    @SerializedName("minecraft:gameplay/increased_fire_burnout")
    public BooleanBiomeAttribute increased_fire_burnout;

    @SerializedName("minecraft:gameplay/monsters_burn")
    public BooleanBiomeAttribute monsters_burn;

    @SerializedName("minecraft:gameplay/nether_portal_spawns_piglin")
    public BooleanBiomeAttribute nether_portal_spawns_piglin;

    @SerializedName("minecraft:gameplay/piglins_zombify")
    public BooleanBiomeAttribute piglins_zombify;

    @SerializedName("minecraft:gameplay/respawn_anchor_works")
    public BooleanBiomeAttribute respawn_anchor_works;

    @SerializedName("minecraft:gameplay/snow_golem_melts")
    public BooleanBiomeAttribute snow_golem_melts;

    @SerializedName("minecraft:gameplay/water_evaporates")
    public BooleanBiomeAttribute water_evaporates;

    /** Particle spawned around dripstone. Particle type is a complex tagged union. */
    @SerializedName("minecraft:visual/default_dripstone_particle")
    @Nullable
    public JsonElement default_dripstone_particle;

    @SerializedName("minecraft:visual/ambient_particles")
    @Nullable
    public List<AmbientParticle> ambient_particles;

    @SerializedName("minecraft:audio/background_music")
    @Nullable
    public BackgroundMusic background_music;

    @SerializedName("minecraft:audio/ambient_sounds")
    @Nullable
    public AmbientSounds ambient_sounds;

    @SerializedName("minecraft:gameplay/bed_rule")
    @Nullable
    public BedRule bed_rule;

    @SerializedName("minecraft:visual/moon_phase")
    @Nullable
    public MoonPhase moon_phase;

    @SerializedName("minecraft:gameplay/eyeblossom_open")
    @Nullable
    public TriState eyeblossom_open;

    @SerializedName("minecraft:gameplay/villager_activity")
    @Nullable
    public String villager_activity;

    @SerializedName("minecraft:gameplay/baby_villager_activity")
    @Nullable
    public String baby_villager_activity;

    public enum TriState {
        @SerializedName("true")
        TRUE,
        @SerializedName("false")
        FALSE,
        @SerializedName("default")
        DEFAULT
    }

    public static class AmbientParticle {
        public IParticle particle;
        @RangeFloat(min = 0, max = 1)
        public float probability;
    }

    public static class BackgroundMusic {
        @Nullable
        @SerializedName("default")
        public DatapackBiome.BiomeMusic default_music;
        @Nullable
        public DatapackBiome.BiomeMusic underwater;
        @Nullable
        public DatapackBiome.BiomeMusic creative;
    }

    public static class AmbientSounds {
        @Nullable
        public SoundEventRef loop;
        @Nullable
        public DatapackBiome.MoodSound mood;
        /** Single {@link DatapackBiome.BiomeSoundAdditions} or list thereof. */
        @Nullable
        public JsonElement additions;
    }

    public static class BedRule {
        public BedRuleType can_sleep;
        public BedRuleType can_set_spawn;
        @Nullable
        public Boolean explodes;
        @Nullable
        public IChatComponent error_message;
    }

    public enum BedRuleType {
        always,
        when_dark,
        never
    }

    public enum MoonPhase {
        full_moon,
        waning_gibbous,
        third_quarter,
        waning_crescent,
        new_moon,
        waxing_crescent,
        first_quarter,
        waxing_gibbous
    }

}
