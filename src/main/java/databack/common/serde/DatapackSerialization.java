package databack.common.serde;

import java.util.HashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.Getter;

@SuppressWarnings({ "rawtypes", "unchecked" })
public class DatapackSerialization {

    @Getter
    private static GsonBuilder builder = new GsonBuilder();

    @Getter
    private static Gson gson;

    private static final Map<String, TaggedUnionLoader> UNIONS = new HashMap<>();

    public static void finish() {
        gson = builder.create();
        builder = null;
    }

    public static <T> TaggedUnionLoader<T> createTaggedUnionLoader(String name, Class<T> iface) {
        return createTaggedUnionLoader(name, iface, "type");
    }

    public static <T> TaggedUnionLoader<T> createTaggedUnionLoader(String name, Class<T> iface, String tagField) {
        assertIniting();

        TaggedUnionLoader<T> loader = new TaggedUnionLoader<>();
        loader.setTagField(tagField);

        builder.registerTypeAdapter(iface, loader);

        UNIONS.put(name, loader);

        return loader;
    }

    private static void assertIniting() {
        if (builder == null) {
            throw new IllegalStateException("Cannot create TaggedUnionLoader after forge preInit stage");
        }
    }

    public static <T> TaggedUnionLoader<T> getTaggedUnionLoader(String name) {
        return UNIONS.computeIfAbsent(name, $ -> new TaggedUnionLoader());
    }

    public static void init() {

    }
}
