package databack.common.serde;

import java.lang.reflect.Type;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import lombok.Getter;
import lombok.Setter;

public class TaggedUnionLoader<Union> implements JsonSerializer<Union>, JsonDeserializer<Union> {

    @Setter
    @Getter
    private String tagField = "type";

    private final BiMap<String, Class<? extends Union>> variants = HashBiMap.create();

    @Setter
    private JsonDeserializer<Union> fallback;

    public TaggedUnionLoader<Union> addVariant(String name, Class<? extends Union> clazz) {
        variants.put(name, clazz);

        return this;
    }

    @Override
    public Union deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
        throws JsonParseException {
        if (!json.isJsonObject()) {
            if (fallback == null) {
                throw new JsonParseException("Expected json object: " + json);
            } else {
                return fallback.deserialize(json, typeOfT, context);
            }
        }

        JsonObject obj = (JsonObject) json;
        JsonElement tag = obj.get(tagField);

        if (tag == null) {
            throw new JsonParseException("Tagged union did not have '" + tagField + "' field: " + json);
        }

        if (!tag.isJsonPrimitive() || !tag.getAsJsonPrimitive().isString()) {
            throw new JsonParseException("Expected value of '" + tagField + "' to be a string: " + json);
        }

        Class<? extends Union> clazz = variants.get(tag.getAsJsonPrimitive().getAsString());

        if (clazz == null) {
            throw new JsonParseException("Unknown variant '" + tag.getAsJsonPrimitive().getAsString() + "': " + json);
        }

        return context.deserialize(json, clazz);
    }

    @Override
    public JsonElement serialize(Union src, Type typeOfSrc, JsonSerializationContext context) {
        String variantName = variants.inverse().get(src.getClass());

        if (variantName == null) {
            throw new JsonParseException("Unknown variant '" + src.getClass().getName() + "': " + src);
        }

        JsonElement json = context.serialize(src, src.getClass());

        if (!json.isJsonObject()) {
            throw new JsonParseException("Expected variant '" + variantName + "' to serialize to object: " + json);
        }

        JsonObject obj = (JsonObject) json;

        obj.addProperty(tagField, variantName);

        return obj;
    }
}
