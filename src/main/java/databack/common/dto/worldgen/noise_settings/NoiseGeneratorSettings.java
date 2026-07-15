package databack.common.dto.worldgen.noise_settings;

import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.annotations.SerializedName;
import databack.common.dto.worldgen.BlockState;
import databack.common.dto.worldgen.density_function.IDensityFunction;

public class NoiseGeneratorSettings {

    @NotNull
    public BlockState default_block;

    @NotNull
    public BlockState default_fluid;

    public int sea_level;
    public boolean disable_mob_generation;
    public boolean aquifers_enabled;
    public boolean ore_veins_enabled;
    public boolean legacy_random_source;

    @NotNull
    public NoiseSettings noise;

    @NotNull
    public NoiseRouter noise_router;

    @Nullable
    public List<ClimateParameters> spawn_target;

    // TODO: MaterialRuleRef not yet implemented
    @Nullable
    public JsonElement surface_rule;

    public static class NoiseSettings {

        public int min_y;
        public int height;
        public int size_horizontal;
        public int size_vertical;
    }

    public static class NoiseRouter {

        @NotNull
        public IDensityFunction barrier;

        @NotNull
        public IDensityFunction fluid_level_floodedness;

        @NotNull
        public IDensityFunction fluid_level_spread;

        @NotNull
        public IDensityFunction lava;

        @NotNull
        public IDensityFunction vein_toggle;

        @NotNull
        public IDensityFunction vein_ridged;

        @NotNull
        public IDensityFunction vein_gap;

        @NotNull
        public IDensityFunction temperature;

        @NotNull
        public IDensityFunction vegetation;

        @NotNull
        public IDensityFunction continents;

        @NotNull
        public IDensityFunction erosion;

        @NotNull
        public IDensityFunction depth;

        @NotNull
        public IDensityFunction ridges;

        @Nullable
        public IDensityFunction initial_density_without_jaggedness;

        @NotNull
        public IDensityFunction final_density;
    }

    public static class ClimateParameters {

        @NotNull
        public float[] temperature;

        @NotNull
        public float[] humidity;

        @NotNull
        public float[] continentalness;

        @NotNull
        public float[] erosion;

        @NotNull
        public float[] weirdness;

        @NotNull
        public float[] depth;

        public float offset;
    }
}
