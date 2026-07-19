package databack.common.dto.dimension;

import java.lang.reflect.Type;
import java.util.List;
import java.util.function.Supplier;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import databack.common.dto.worldgen.multi_noise_biome_source.MultiNoiseBiomeSourceParameterList;
import databack.common.dto.worldgen.noise_settings.NoiseGeneratorSettings;
import databack.common.dto.worldgen.noise_settings.NoiseGeneratorSettings.ClimateParameters;
import databack.common.handlers.NoiseSettingsList;
import databack.common.serde.DatapackSerialization;
import databack.common.serde.TaggedUnionLoader;

public class BuiltinDimensionGenerators {

    public static void init() {
        TaggedUnionLoader<DimensionGenerator> gens = DatapackSerialization.createTaggedUnionLoader(
            "builtin/dimension_generator", DimensionGenerator.class);
        gens.addVariant("minecraft:noise", NoiseDimensionGenerator.class);
        gens.addVariant("minecraft:flat", FlatDimensionGenerator.class);
        gens.addVariant("minecraft:debug", DebugDimensionGenerator.class);

        TaggedUnionLoader<BiomeGeneration> biomes = DatapackSerialization.createTaggedUnionLoader(
            "builtin/biome_generation", BiomeGeneration.class);
        biomes.addVariant("minecraft:multi_noise", MultiNoiseBiomes.class);
        biomes.addVariant("minecraft:fixed", FixedBiomes.class);
        biomes.addVariant("minecraft:checkerboard", CheckerboardBiomes.class);
        biomes.addVariant("minecraft:the_end", TheEndBiomes.class);

        DatapackSerialization.getBuilder()
            .registerTypeAdapter(NoiseSettingsRef.class, new NoiseSettingsRefDeserializer());
        DatapackSerialization.getBuilder()
            .registerTypeAdapter(IMultiNoisePreset.class, new MultiNoisePresetDeserializer());
    }

    // -- DimensionGenerator variants --

    public interface DimensionGenerator {}

    public static class NoiseDimensionGenerator implements DimensionGenerator {

        @NotNull
        public NoiseSettingsRef settings;

        @NotNull
        public BiomeGeneration biome_source;
    }

    public static class FlatDimensionGenerator implements DimensionGenerator {

        @NotNull
        public FlatSettings settings;
    }

    /** Marker for the debug world type. No additional fields. */
    public static class DebugDimensionGenerator implements DimensionGenerator {}

    // -- Flat settings --

    @SuppressWarnings("NotNullFieldNotInitialized")
    public static class FlatSettings {

        @NotNull
        public List<FlatLayer> layers;

        @Nullable
        public String biome;

        public boolean lakes;
        public boolean features;

        /** Can be a structure set ID, a structure set tag, or a list of structure set IDs. */
        @Nullable
        public JsonElement structure_overrides;
    }

    public static class FlatLayer {

        public int height;

        @Nullable
        public String block;
    }

    // -- BiomeGeneration variants --

    public interface BiomeGeneration {}

    public static class MultiNoiseBiomes implements BiomeGeneration {

        /**
         * Mutually exclusive with {@link #biomes}.
         * Either a string resource ID or an inline parameter list object.
         */
        @Nullable
        public IMultiNoisePreset preset;

        /** Mutually exclusive with {@link #preset}. Cannot be empty if present. */
        @Nullable
        public List<BiomeEntry> biomes;
    }

    public static class FixedBiomes implements BiomeGeneration {

        @NotNull
        public String biome;
    }

    public static class CheckerboardBiomes implements BiomeGeneration {

        /** Can be a biome ID, a biome tag, or a list of biome IDs. */
        @NotNull
        public JsonElement biomes;

        /** Value between 0 and 62 (inclusive). Determines the square size on an exponential scale. */
        public int scale = 2;
    }

    /** Marker for end biome generation. No additional fields. */
    public static class TheEndBiomes implements BiomeGeneration {}

    // -- BiomeEntry (used in MultiNoiseBiomes.biomes) --

    @SuppressWarnings("NotNullFieldNotInitialized")
    public static class BiomeEntry {

        @NotNull
        public String biome;

        @NotNull
        public ClimateParameters parameters;
    }

    // -- NoiseSettingsRef: either a string resource ID or inline NoiseGeneratorSettings --

    public interface NoiseSettingsRef extends Supplier<NoiseGeneratorSettings> {}

    public static class NoiseSettingsIdRef implements NoiseSettingsRef {

        @NotNull
        public final String id;

        public NoiseSettingsIdRef(@NotNull String id) {
            this.id = id;
        }

        /** @return {@code null} until resolved against the noise settings handler. */
        @Override
        @Nullable
        public NoiseGeneratorSettings get() {
            return NoiseSettingsList.RT.getHandler().getNoiseSettings(id);
        }
    }

    public static class NoiseSettingsInlineRef implements NoiseSettingsRef {

        @NotNull
        public final NoiseGeneratorSettings settings;

        public NoiseSettingsInlineRef(@NotNull NoiseGeneratorSettings settings) {
            this.settings = settings;
        }

        @Override
        @NotNull
        public NoiseGeneratorSettings get() {
            return settings;
        }
    }

    private static class NoiseSettingsRefDeserializer implements JsonDeserializer<NoiseSettingsRef> {

        @Override
        public NoiseSettingsRef deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
            throws JsonParseException {
            if (json.isJsonPrimitive()) {
                return new NoiseSettingsIdRef(json.getAsString());
            }
            return new NoiseSettingsInlineRef(context.deserialize(json, NoiseGeneratorSettings.class));
        }
    }

    // -- IMultiNoisePreset: either a string resource ID or inline MultiNoiseBiomeSourceParameterList --

    public interface IMultiNoisePreset {}

    public static class MultiNoisePresetIdRef implements IMultiNoisePreset {

        @NotNull
        public final String id;

        public MultiNoisePresetIdRef(@NotNull String id) {
            this.id = id;
        }
    }

    public static class MultiNoisePresetInlineRef implements IMultiNoisePreset {

        @NotNull
        public final MultiNoiseBiomeSourceParameterList parameterList;

        public MultiNoisePresetInlineRef(@NotNull MultiNoiseBiomeSourceParameterList parameterList) {
            this.parameterList = parameterList;
        }
    }

    private static class MultiNoisePresetDeserializer implements JsonDeserializer<IMultiNoisePreset> {

        @Override
        public IMultiNoisePreset deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
            throws JsonParseException {
            if (json.isJsonPrimitive()) {
                return new MultiNoisePresetIdRef(json.getAsString());
            }
            return new MultiNoisePresetInlineRef(
                context.deserialize(json, MultiNoiseBiomeSourceParameterList.class));
        }
    }
}
