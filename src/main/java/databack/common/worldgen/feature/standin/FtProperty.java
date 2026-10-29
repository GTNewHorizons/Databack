package databack.common.worldgen.feature.standin;

import java.util.List;
import java.util.function.Function;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import databack.common.worldgen.vanilla.standin.VnAxis;
import databack.common.worldgen.vanilla.standin.VnDirection;

/// Stand-in for a block state property: its name, and the type its values read as (vanilla's own property constants
/// are [FtBlockProperties]). Its possible values are not known.
@ApiStatus.Internal
public final class FtProperty {

    @NotNull
    private final String name;
    /// A serialized value as vanilla's value, or null if it is not one.
    @NotNull
    private final Function<String, Comparable<?>> parse;

    private FtProperty(@NotNull String name, @NotNull Function<String, Comparable<?>> parse) {
        this.name = name;
        this.parse = parse;
    }

    /// A property whose values read as their names.
    @NotNull
    @Contract("_ -> new")
    public static FtProperty of(@NotNull String name) {
        return new FtProperty(name, value -> value);
    }

    @NotNull
    @Contract("_ -> new")
    public static FtProperty integer(@NotNull String name) {
        return new FtProperty(name, value -> {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                return null;
            }
        });
    }

    @NotNull
    @Contract("_ -> new")
    public static FtProperty bool(@NotNull String name) {
        return new FtProperty(name, value -> value.equals("true") ? Boolean.TRUE
            : value.equals("false") ? Boolean.FALSE : null);
    }

    @NotNull
    @Contract("_ -> new")
    public static FtProperty direction(@NotNull String name) {
        return new FtProperty(name, value -> {
            for (VnDirection direction : VnDirection.values()) {
                if (direction.getSerializedName().equals(value)) return direction;
            }
            return null;
        });
    }

    @NotNull
    @Contract("_ -> new")
    public static FtProperty axis(@NotNull String name) {
        return new FtProperty(name, value -> {
            for (VnAxis axis : new VnAxis[] { VnAxis.X, VnAxis.Y, VnAxis.Z }) {
                if (axis.getName().equals(value)) return axis;
            }
            return null;
        });
    }

    @NotNull
    public String getName() {
        return name;
    }

    /// `value` (serialized) as vanilla's value for this property, or null if it is not a value of it.
    @Nullable
    Comparable<?> parse(@NotNull String value) {
        return parse.apply(value);
    }

    /// Unknown: states only know the properties they were given, not their blocks' ([FtBlockState]).
    @NotNull
    public List<?> getPossibleValues() {
        throw new UnsupportedOperationException("The values of block state property " + name + " are unknown");
    }

    /// A property value as a block state names it: `y` for the Y axis, `3` for 3.
    @NotNull
    @Contract(pure = true)
    static String serialize(@NotNull Object value) {
        if (value instanceof VnAxis) return ((VnAxis) value).getName();
        if (value instanceof VnDirection) return ((VnDirection) value).getSerializedName();
        return value.toString();
    }

    /// Properties are equal by name.
    @Override
    public boolean equals(Object o) {
        return o instanceof FtProperty && ((FtProperty) o).name.equals(name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    @Override
    public String toString() {
        return name;
    }
}
