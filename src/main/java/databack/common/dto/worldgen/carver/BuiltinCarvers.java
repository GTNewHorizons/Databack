package databack.common.dto.worldgen.carver;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnegative;

import org.jetbrains.annotations.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import databack.common.annotation.RangeFloat;
import databack.common.dto.worldgen.height_provider.IHeightProvider;
import databack.common.dto.worldgen.height_provider.VerticalAnchor;
import databack.common.serde.DatapackSerialization;
import databack.common.serde.TaggedUnionLoader;

public class BuiltinCarvers {

    public static void init() {
        TaggedUnionLoader<IConfiguredCarver> loader = DatapackSerialization
            .createTaggedUnionLoader("worldgen/configured_carver", IConfiguredCarver.class);

        loader.addVariant("cave", CaveCarver.class);
        loader.addVariant("nether_cave", CaveCarver.class);
        loader.addVariant("canyon", CanyonCarver.class);

        // String → lazy resource-location ref to a configured_carver data file
        loader.setFallback((json, typeOfT, context) -> {
            if (!json.isJsonPrimitive() || !json.getAsJsonPrimitive().isString()) {
                throw new JsonParseException("Expected typed object or resource location string for carver: " + json);
            }
            return new CarverRef(json.getAsString());
        });

        DatapackSerialization.getBuilder()
            .registerTypeAdapter(BiomeCarvers.class, new BiomeCarversDeserializer());
    }

    // Lazy reference to a configured carver by resource location.
    static class CarverRef implements IConfiguredCarver {

        final String id;

        CarverRef(String id) {
            this.id = id;
        }

    }

    static class BlockStateDto {

        public String Name;
        @Nullable
        public Map<String, String> Properties;

    }

    static class CarverDebugSettings {

        @Nullable public Boolean debug_mode;
        public BlockStateDto air_state;
        public BlockStateDto water_state;
        public BlockStateDto lava_state;
        public BlockStateDto barrier_state;

    }

    static class CarverConfigBase {

        @RangeFloat(min = 0, max = 1)
        public float probability;
        public IHeightProvider y;
        /** FloatProvider<number> — stored raw until IFloatProvider is implemented. */
        public JsonElement yScale;
        public VerticalAnchor lava_level;
        /** @until 1.18 */
        @Nullable public Boolean aquifers_enabled;
        @Nullable public CarverDebugSettings debug_settings;
        /** Block IDs or a block tag. String or String[]. @since 1.19 */
        @Nullable public JsonElement replaceable;

    }

    static class CaveCarverConfig extends CarverConfigBase {

        /** FloatProvider<number> */
        public JsonElement horizontal_radius_multiplier;
        /** FloatProvider<number> */
        public JsonElement vertical_radius_multiplier;
        /** FloatProvider<number>, range -1..1 */
        public JsonElement floor_level;

    }

    static class CaveCarver implements IConfiguredCarver {

        public CaveCarverConfig config;

    }

    static class CanyonShape {

        /** FloatProvider<number> */
        public JsonElement distance_factor;
        /** FloatProvider<number> */
        public JsonElement thickness;
        @Nonnegative
        public int width_smoothness;
        /** FloatProvider<number> */
        public JsonElement horizontal_radius_factor;
        public float vertical_radius_default_factor;
        public float vertical_radius_center_factor;

    }

    static class CanyonCarverConfig extends CarverConfigBase {

        /** FloatProvider<number> */
        public JsonElement vertical_rotation;
        public CanyonShape shape;

    }

    static class CanyonCarver implements IConfiguredCarver {

        public CanyonCarverConfig config;

    }

    /**
     * Deserializes the biome carvers field, which is either:
     * - Old format (until 1.21.2): JSON object keyed by CarveStep
     * - New format (since 1.21.2): JSON array of carver refs, or a tag string
     *
     * New-format entries are assigned to all steps, as step is intrinsic to the
     * carver type's registration (not stored in biome JSON).
     */
    private static class BiomeCarversDeserializer implements JsonDeserializer<BiomeCarvers> {

        @Override
        public BiomeCarvers deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
            throws JsonParseException {
            Map<CarveStep, List<IConfiguredCarver>> phases = new EnumMap<>(CarveStep.class);

            if (json.isJsonObject()) {
                deserializeByStep(json.getAsJsonObject(), phases, context);
            } else if (json.isJsonArray()) {
                List<IConfiguredCarver> all = deserializeList(json.getAsJsonArray(), context);
                for (CarveStep step : CarveStep.values()) {
                    phases.put(step, all);
                }
            } else if (json.isJsonPrimitive()) {
                // Tag ref covering all carvers — resolution deferred to registry
                CarverRef ref = new CarverRef(json.getAsString());
                List<IConfiguredCarver> all = Collections.singletonList(ref);
                for (CarveStep step : CarveStep.values()) {
                    phases.put(step, all);
                }
            } else {
                throw new JsonParseException("Expected object, array, or string for carvers: " + json);
            }

            return new BiomeCarvers(phases);
        }

        private void deserializeByStep(
            JsonObject obj,
            Map<CarveStep, List<IConfiguredCarver>> phases,
            JsonDeserializationContext context) {
            for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
                CarveStep step;
                try {
                    step = CarveStep.valueOf(entry.getKey());
                } catch (IllegalArgumentException e) {
                    throw new JsonParseException("Unknown carve step '" + entry.getKey() + "'", e);
                }

                JsonElement value = entry.getValue();
                List<IConfiguredCarver> carvers;

                if (value.isJsonArray()) {
                    carvers = deserializeList(value.getAsJsonArray(), context);
                } else if (value.isJsonPrimitive()) {
                    // Tag ref for this step
                    carvers = new ArrayList<>();
                    carvers.add(new CarverRef(value.getAsString()));
                } else {
                    throw new JsonParseException("Expected array or tag string for carve step '" + entry.getKey() + "': " + value);
                }

                phases.put(step, carvers);
            }
        }

        private List<IConfiguredCarver> deserializeList(JsonArray array, JsonDeserializationContext context) {
            List<IConfiguredCarver> list = new ArrayList<>(array.size());
            for (JsonElement element : array) {
                list.add(context.deserialize(element, IConfiguredCarver.class));
            }
            return list;
        }

    }

}
