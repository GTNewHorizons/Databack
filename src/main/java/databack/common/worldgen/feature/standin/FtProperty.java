package databack.common.worldgen.feature.standin;

import java.util.List;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import databack.common.worldgen.vanilla.standin.VnAxis;
import databack.common.worldgen.vanilla.standin.VnDirection;

/// Stand-in for a block state property: its name. Its values are not known.
@ApiStatus.Internal
public final class FtProperty {

    @NotNull
    private final String name;

    private FtProperty(@NotNull String name) {
        this.name = name;
    }

    @NotNull
    @Contract("_ -> new")
    public static FtProperty of(@NotNull String name) {
        return new FtProperty(name);
    }

    @NotNull
    public String getName() {
        return name;
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
