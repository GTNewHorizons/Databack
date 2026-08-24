package databack.common.dto.tag;

import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public final class TagFile {

    /**
     * Dedicated Gson instance for tag files. Independent of {@code DatapackSerialization}
     * so tag loading has no ordering dependency on the main GSON lifecycle.
     */
    public static final Gson GSON = new GsonBuilder()
        .registerTypeAdapter(TagEntry.class, new TagEntry.Deserializer())
        .create();

    /**
     * When true, entries from lower-priority packs that define the same tag are ignored.
     * Defaults to false (additive).
     */
    public boolean replace = false;

    /** The list of tag entries. May be null if the key is absent in the JSON. */
    public List<TagEntry> values;
}
