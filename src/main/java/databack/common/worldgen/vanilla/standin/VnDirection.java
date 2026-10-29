package databack.common.worldgen.vanilla.standin;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/// Stand-in for the six directions, in vanilla's order (`feature/probes/direction.tsv`), and their rotations
/// (`feature/probes/direction_ops.tsv`). Ordered by that order.
@ApiStatus.Internal
public final class VnDirection implements Comparable<VnDirection> {

    public static final VnDirection DOWN = new VnDirection("down", 0, 0, -1, 0, VnAxis.Y);
    public static final VnDirection UP = new VnDirection("up", 1, 0, 1, 0, VnAxis.Y);
    public static final VnDirection NORTH = new VnDirection("north", 2, 0, 0, -1, VnAxis.Z);
    public static final VnDirection SOUTH = new VnDirection("south", 3, 0, 0, 1, VnAxis.Z);
    public static final VnDirection WEST = new VnDirection("west", 4, -1, 0, 0, VnAxis.X);
    public static final VnDirection EAST = new VnDirection("east", 5, 1, 0, 0, VnAxis.X);

    private static final VnDirection[] VALUES = { DOWN, UP, NORTH, SOUTH, WEST, EAST };

    @NotNull
    private final String name;
    private final int ordinal;
    private final int stepX, stepY, stepZ;
    @NotNull
    private final VnAxis axis;

    private VnDirection(@NotNull String name, int ordinal, int stepX, int stepY, int stepZ, @NotNull VnAxis axis) {
        this.name = name;
        this.ordinal = ordinal;
        this.stepX = stepX;
        this.stepY = stepY;
        this.stepZ = stepZ;
        this.axis = axis;
    }

    @NotNull
    public static VnDirection[] values() {
        return VALUES.clone();
    }

    public int getStepX() {
        return stepX;
    }

    public int getStepY() {
        return stepY;
    }

    public int getStepZ() {
        return stepZ;
    }

    @NotNull
    public VnAxis getAxis() {
        return axis;
    }

    /// Whether the direction steps towards larger coordinates.
    @NotNull
    public VnAxisDirection getAxisDirection() {
        return stepX + stepY + stepZ > 0 ? VnAxisDirection.POSITIVE : VnAxisDirection.NEGATIVE;
    }

    @NotNull
    public VnDirection getOpposite() {
        return VALUES[ordinal ^ 1];
    }

    /// A quarter turn clockwise seen from above: north to east, east to south. Throws for up and down.
    @NotNull
    public VnDirection getClockWise() {
        if (this == NORTH) return EAST;
        if (this == EAST) return SOUTH;
        if (this == SOUTH) return WEST;
        if (this == WEST) return NORTH;
        throw new IllegalStateException("Cannot turn " + name + " clockwise");
    }

    @Override
    public int compareTo(@NotNull VnDirection other) {
        return Integer.compare(ordinal, other.ordinal);
    }

    /// The name datapacks use.
    @NotNull
    public String getSerializedName() {
        return name;
    }

    @Override
    public String toString() {
        return name;
    }
}
