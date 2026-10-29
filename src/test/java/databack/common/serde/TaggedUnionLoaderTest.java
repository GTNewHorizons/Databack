package databack.common.serde;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

/// Variants, unknown types with and without a placeholder.
class TaggedUnionLoaderTest {

    interface Shape {

        String name();
    }

    static final class Square implements Shape {

        int side;

        @Override
        public String name() {
            return "square " + side;
        }
    }

    private static Gson gson(boolean placeholder) {
        TaggedUnionLoader<Shape> loader = new TaggedUnionLoader<>();
        loader.setInterfaceType(Shape.class);
        loader.setName("test/shape");
        loader.addVariant("test:square", Square.class);
        if (placeholder) loader.setUnknown(type -> () -> "unknown " + type);
        return new GsonBuilder().registerTypeAdapter(Shape.class, loader).create();
    }

    @Test
    void knownTypesParse() {
        Shape shape = gson(true).fromJson("{\"type\":\"test:square\",\"side\":3}", Shape.class);
        assertInstanceOf(Square.class, shape);
        assertEquals("square 3", shape.name());
    }

    @Test
    void unknownTypesBecomeThePlaceholder() {
        Shape shape = gson(true).fromJson("{\"type\":\"other:circle\",\"radius\":2}", Shape.class);
        assertEquals("unknown other:circle", shape.name());
    }

    @Test
    void unknownTypesFailWithoutAPlaceholder() {
        assertThrows(JsonParseException.class, () -> gson(false).fromJson("{\"type\":\"other:circle\"}", Shape.class));
    }
}
