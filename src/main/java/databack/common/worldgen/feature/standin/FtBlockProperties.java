package databack.common.worldgen.feature.standin;

import java.util.OptionalInt;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import databack.common.worldgen.vanilla.standin.VnBlockPos;
import databack.common.worldgen.vanilla.standin.VnDirection;

/// Stand-in for the block state property constants vanilla's feature classes name, on whichever block class names
/// them (pillars, leaves, vines, cocoa, beehives...), and for the vine helpers. Vanilla's constant names are the
/// property names in capitals.
@ApiStatus.Internal
public final class FtBlockProperties {

    public static final FtProperty AXIS = FtProperty.axis("axis");
    public static final FtProperty FACING = FtProperty.direction("facing");
    public static final FtProperty AGE = FtProperty.integer("age");
    public static final FtProperty DISTANCE = FtProperty.integer("distance");
    public static final FtProperty PERSISTENT = FtProperty.bool("persistent");
    public static final FtProperty WATERLOGGED = FtProperty.bool("waterlogged");
    public static final FtProperty NORTH = FtProperty.bool("north");
    public static final FtProperty EAST = FtProperty.bool("east");
    public static final FtProperty SOUTH = FtProperty.bool("south");
    public static final FtProperty WEST = FtProperty.bool("west");
    public static final FtProperty UP = FtProperty.bool("up");
    public static final FtProperty SNOWY = FtProperty.bool("snowy");

    private FtBlockProperties() {}

    /// A leaves state's distance from a log, or none if the state has no distance.
    @NotNull
    public static OptionalInt getOptionalDistanceAt(@NotNull FtBlockState state) {
        Object distance = state.getValueOrElse(DISTANCE, null);
        return distance instanceof Integer ? OptionalInt.of((Integer) distance) : OptionalInt.empty();
    }

    /// A vine's side property facing `face`. Vines have no side facing down.
    @NotNull
    public static FtProperty getPropertyForFace(@NotNull VnDirection face) {
        for (FtProperty side : new FtProperty[] { UP, NORTH, EAST, SOUTH, WEST }) {
            if (side.getName().equals(face.getSerializedName())) return side;
        }
        throw new IllegalArgumentException("Vines have no " + face.getSerializedName() + " side");
    }

    /// Whether a vine can hang on the block at `pos`, which lies towards `face` from the vine: the block's side facing
    /// the vine must be sturdy.
    public static boolean isAcceptableNeighbour(@NotNull FtLevel level, @NotNull VnBlockPos pos,
        @NotNull VnDirection face) {
        return level.getBlockState(pos).isFaceSturdy(level, pos, face.getOpposite());
    }
}
