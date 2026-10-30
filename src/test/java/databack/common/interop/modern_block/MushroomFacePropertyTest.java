package databack.common.interop.modern_block;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/// [MushroomFaceProperty] against 1.7's huge mushroom layouts.
class MushroomFacePropertyTest {

    private static final MushroomFaceProperty[] FACES = MushroomFaceProperty.all();

    /// Setting every face to what a layout already shows gives the layout back.
    @Test
    void layoutsRoundTrip() {
        for (int meta : new int[] { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 14, 15 }) {
            int result = meta;
            for (MushroomFaceProperty face : FACES) result = face.getMetaPrimitive(face.getValuePrimitive(meta), result);
            assertEquals(meta, result, "meta " + meta);
        }
    }

    @Test
    void capFacesPickTheirLayout() {
        assertEquals(1, with(0, "up", "north", "west"));
        assertEquals(9, with(0, "up", "south", "east"));
        assertEquals(5, with(0, "up"));
        assertEquals(0, with(5));
    }

    /// A cap's side without a top still shows 1.7's top: the block above covers it.
    @Test
    void sidesWithoutATopKeepTheTop() {
        assertEquals(2, with(0, "north"));
        assertEquals(6, with(0, "east"));
    }

    @Test
    void stemsShowEndsOnlyWithUpOrDown() {
        assertEquals(10, with(15, "north", "south", "west", "east"));
        assertEquals(15, with(10, "up", "down", "north", "south", "west", "east"));
    }

    /// `meta` with exactly the faces named, set one at a time in order.
    private static int with(int meta, String... faces) {
        java.util.Set<String> shown = new java.util.HashSet<>(java.util.Arrays.asList(faces));
        for (MushroomFaceProperty face : FACES) meta = face.getMetaPrimitive(shown.contains(face.getName()), meta);
        return meta;
    }
}
