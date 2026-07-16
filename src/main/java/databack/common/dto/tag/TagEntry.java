package databack.common.dto.tag;

import java.lang.reflect.Type;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

public final class TagEntry {

    /** The resource location or tag ID (without leading '#'). */
    public final String id;

    /** True when the original value started with '#' (i.e. this is a tag reference). */
    public final boolean isTagRef;

    /**
     * False only when explicitly set in the object form: {@code {"id": "...", "required": false}}.
     * When false, a missing entry or tag is a warning, not a load failure.
     */
    public final boolean required;

    public TagEntry(String id, boolean isTagRef, boolean required) {
        this.id = id;
        this.isTagRef = isTagRef;
        this.required = required;
    }

    public static final class Deserializer implements JsonDeserializer<TagEntry> {

        @Override
        public TagEntry deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
            throws JsonParseException {
            if (json.isJsonPrimitive()) {
                String raw = json.getAsString();
                boolean isRef = raw.startsWith("#");
                return new TagEntry(isRef ? raw.substring(1) : raw, isRef, true);
            }
            if (json.isJsonObject()) {
                JsonObject obj = json.getAsJsonObject();
                if (!obj.has("id")) {
                    throw new JsonParseException("Tag entry object is missing required 'id' field");
                }
                String raw = obj.get("id").getAsString();
                boolean isRef = raw.startsWith("#");
                boolean req = !obj.has("required") || obj.get("required").getAsBoolean();
                return new TagEntry(isRef ? raw.substring(1) : raw, isRef, req);
            }
            throw new JsonParseException("Tag entry must be a string or object, got: " + json);
        }
    }
}
