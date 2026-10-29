package databack.common.worldgen.vanilla.standin;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/// Stand-in for the six directions, in vanilla's order (`feature/probes/direction.tsv`).
@ApiStatus.Internal
public final class VnDirection {

    public static final VnDirection DOWN = new VnDirection("down", 0, -1, 0);
    public static final VnDirection UP = new VnDirection("up", 0, 1, 0);
    public static final VnDirection NORTH = new VnDirection("north", 0, 0, -1);
    public static final VnDirection SOUTH = new VnDirection("south", 0, 0, 1);
    public static final VnDirection WEST = new VnDirection("west", -1, 0, 0);
    public static final VnDirection EAST = new VnDirection("east", 1, 0, 0);

    private static final VnDirection[] VALUES = { DOWN, UP, NORTH, SOUTH, WEST, EAST };

    @NotNull
    private final String name;
    private final int stepX, stepY, stepZ;

    private VnDirection(@NotNull String name, int stepX, int stepY, int stepZ) {
        this.name = name;
        this.stepX = stepX;
        this.stepY = stepY;
        this.stepZ = stepZ;
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
