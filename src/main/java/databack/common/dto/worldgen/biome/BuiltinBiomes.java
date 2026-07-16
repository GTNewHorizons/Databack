package databack.common.dto.worldgen.biome;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import databack.common.dto.worldgen.biome.DatapackBiome.BiomeMusic;
import databack.common.dto.worldgen.biome.DatapackBiome.BiomeMusicList;
import databack.common.dto.worldgen.biome.DatapackBiome.WeightedBiomeMusic;
import databack.common.serde.DatapackSerialization;

public class BuiltinBiomes {

    public static void init() {
        DatapackSerialization.getBuilder()
            .registerTypeAdapter(BiomeMusicList.class, new BiomeMusicListDeserializer())
            .registerTypeAdapter(PlacedFeatureSet.class, (JsonDeserializer<PlacedFeatureSet>) (json, type, ctx) -> {
                String s = json.getAsString();
                return s.startsWith("#") ? new PlacedFeatureTag(s.substring(1)) : new PlacedFeatureId(s);
            });
    }

    private static class BiomeMusicListDeserializer implements JsonDeserializer<BiomeMusicList> {

        @Override
        public BiomeMusicList deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
            throws JsonParseException {
            if (json.isJsonObject()) {
                WeightedBiomeMusic entry = new WeightedBiomeMusic();
                entry.weight = 1;
                entry.data = context.deserialize(json, BiomeMusic.class);
                List<WeightedBiomeMusic> list = new ArrayList<>(1);
                list.add(entry);
                return new BiomeMusicList(list);
            }

            if (json.isJsonArray()) {
                JsonArray array = json.getAsJsonArray();
                List<WeightedBiomeMusic> list = new ArrayList<>(array.size());
                for (JsonElement element : array) {
                    list.add(context.deserialize(element, WeightedBiomeMusic.class));
                }
                return new BiomeMusicList(list);
            }

            throw new JsonParseException("Expected object or array for music: " + json);
        }

    }

}
