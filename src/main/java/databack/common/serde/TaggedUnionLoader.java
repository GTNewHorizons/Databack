package databack.common.serde;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

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

public class TaggedUnionLoader<Union> implements JsonDeserializer<Union> {

    @Setter
    @Getter
    private String tagField = "type";

    @Setter
    @Getter
    private Class<Union> interfaceType;

    private final Map<String, Class<? extends Union>> variants = new HashMap<>();

    private JsonDeserializer<Union> fallback;

    public TaggedUnionLoader<Union> addVariant(String name, Class<? extends Union> clazz) {
        variants.put(name, clazz);

        return this;
    }

    public TaggedUnionLoader<Union> setFallback(JsonDeserializer<Union> fallback) {
        this.fallback = fallback;
        return this;
    }

    public TaggedUnionLoader<Union> setFallback(Class<? extends Union> fallback) {
        this.fallback = (json, typeOfT, context) -> context.deserialize(json, fallback);

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
            if (fallback != null) {
                return fallback.deserialize(json, typeOfT, context);
            }
            throw new JsonParseException("Tagged union did not have '" + tagField + "' field: " + json);
        }

        if (!tag.isJsonPrimitive() || !tag.getAsJsonPrimitive().isString()) {
            throw new JsonParseException("Expected value of '" + tagField + "' to be a string: " + json);
        }

        Class<? extends Union> clazz = variants.get(tag.getAsJsonPrimitive().getAsString());

        if (clazz == null) {
            throw new JsonParseException("Unknown variant '" + tag.getAsJsonPrimitive().getAsString() + "' while parsing " + interfaceType.getName() + ": " + json);
        }

        try {
            return context.deserialize(json, clazz);
        } catch (JsonParseException e) {
            throw new JsonParseException("Error while deserializing '" + tag.getAsJsonPrimitive().getAsString() + "/" + clazz.getName() + "'", e);
        }
    }
}
