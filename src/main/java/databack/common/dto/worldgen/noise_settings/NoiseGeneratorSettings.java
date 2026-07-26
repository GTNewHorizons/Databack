package databack.common.dto.worldgen.noise_settings;

import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.gtnewhorizon.gtnhlib.blockstate.core.BlockState;
import databack.common.dto.worldgen.density_function.IDensityFunctionFactory;
import databack.common.serde.DatapackSerialization;

@SuppressWarnings({ "unused", "NotNullFieldNotInitialized" })
public class NoiseGeneratorSettings {

    public static void init() {
        DatapackSerialization.getBuilder().registerTypeAdapter(
            ClimatePoint.class, (JsonDeserializer<ClimatePoint>) (json, typeOfT, context) -> {
                if (json.isJsonPrimitive()) {
                    return new ClimatePoint(json.getAsFloat(), json.getAsFloat());
                } else {
                    JsonArray array = json.getAsJsonArray();

                    return new ClimatePoint(array.get(0).getAsFloat(), array.get(1).getAsFloat());
                }
            });
    }

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
        public IDensityFunctionFactory barrier;

        @NotNull
        public IDensityFunctionFactory fluid_level_floodedness;

        @NotNull
        public IDensityFunctionFactory fluid_level_spread;

        @NotNull
        public IDensityFunctionFactory lava;

        @NotNull
        public IDensityFunctionFactory vein_toggle;

        @NotNull
        public IDensityFunctionFactory vein_ridged;

        @NotNull
        public IDensityFunctionFactory vein_gap;

        @NotNull
        public IDensityFunctionFactory temperature;

        @NotNull
        public IDensityFunctionFactory vegetation;

        @NotNull
        public IDensityFunctionFactory continents;

        @NotNull
        public IDensityFunctionFactory erosion;

        @NotNull
        public IDensityFunctionFactory depth;

        @NotNull
        public IDensityFunctionFactory ridges;

        @Nullable
        public IDensityFunctionFactory initial_density_without_jaggedness;

        @NotNull
        public IDensityFunctionFactory final_density;
    }

    public static class ClimatePoint {
        public float min, max;

        public ClimatePoint(float min, float max) {
            this.min = min;
            this.max = max;
        }

        public float distance(float value) {
            if (value < min) return min - value;
            if (value > max) return value - max;
            return 0;
        }
    }

    public static class ClimateParameters {

        @NotNull
        public ClimatePoint temperature;

        @NotNull
        public ClimatePoint humidity;

        @NotNull
        public ClimatePoint continentalness;

        @NotNull
        public ClimatePoint erosion;

        @NotNull
        public ClimatePoint weirdness;

        @NotNull
        public ClimatePoint depth;

        public float offset;
    }
}
