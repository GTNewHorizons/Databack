package databack.common.worldgen.biome.preset;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/// Java 8 replacement for `List.of` in extracted preset code. Overloads mirror the JDK's, so a call site only needs
/// its owner changed.
@ApiStatus.Internal
public final class PresetLists {

    private PresetLists() {}

    @NotNull
    public static List<Object> of(Object... values) {
        return wrap(values.clone());
    }

    @NotNull
    public static List<Object> of() {
        return wrap(new Object[0]);
    }

    @NotNull
    public static List<Object> of(Object a) {
        return wrap(new Object[] { a });
    }

    @NotNull
    public static List<Object> of(Object a, Object b) {
        return wrap(new Object[] { a, b });
    }

    @NotNull
    public static List<Object> of(Object a, Object b, Object c) {
        return wrap(new Object[] { a, b, c });
    }

    @NotNull
    public static List<Object> of(Object a, Object b, Object c, Object d) {
        return wrap(new Object[] { a, b, c, d });
    }

    @NotNull
    public static List<Object> of(Object a, Object b, Object c, Object d, Object e) {
        return wrap(new Object[] { a, b, c, d, e });
    }

    @NotNull
    public static List<Object> of(Object a, Object b, Object c, Object d, Object e, Object f) {
        return wrap(new Object[] { a, b, c, d, e, f });
    }

    @NotNull
    public static List<Object> of(Object a, Object b, Object c, Object d, Object e, Object f, Object g) {
        return wrap(new Object[] { a, b, c, d, e, f, g });
    }

    @NotNull
    public static List<Object> of(Object a, Object b, Object c, Object d, Object e, Object f, Object g, Object h) {
        return wrap(new Object[] { a, b, c, d, e, f, g, h });
    }

    @NotNull
    public static List<Object> of(
        Object a, Object b, Object c, Object d, Object e, Object f, Object g, Object h, Object i
    ) {
        return wrap(new Object[] { a, b, c, d, e, f, g, h, i });
    }

    @NotNull
    public static List<Object> of(
        Object a, Object b, Object c, Object d, Object e, Object f, Object g, Object h, Object i, Object j
    ) {
        return wrap(new Object[] { a, b, c, d, e, f, g, h, i, j });
    }

    private static List<Object> wrap(Object[] values) {
        return Collections.unmodifiableList(Arrays.asList(values));
    }
}
