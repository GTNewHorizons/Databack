package databack.common.worldgen.vanilla.standin;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import databack.common.worldgen.rng.RandomSource;

/// Stand-in for the three axes. As property values they are named as in datapacks.
@ApiStatus.Internal
public final class VnAxis implements Comparable<VnAxis> {

    public static final VnAxis X = new VnAxis("x", 0);
    public static final VnAxis Y = new VnAxis("y", 1);
    public static final VnAxis Z = new VnAxis("z", 2);

    private static final VnAxis[] VALUES = { X, Y, Z };

    @NotNull
    private final String name;
    private final int ordinal;

    private VnAxis(@NotNull String name, int ordinal) {
        this.name = name;
        this.ordinal = ordinal;
    }

    /// One bounded draw over x, y, z (`feature/probes/axis_random.tsv`).
    @NotNull
    public static VnAxis getRandom(@NotNull RandomSource random) {
        return VALUES[random.nextInt(VALUES.length)];
    }

    @NotNull
    public String getName() {
        return name;
    }

    @Override
    public int compareTo(@NotNull VnAxis other) {
        return Integer.compare(ordinal, other.ordinal);
    }

    @Override
    public String toString() {
        return name;
    }
}
