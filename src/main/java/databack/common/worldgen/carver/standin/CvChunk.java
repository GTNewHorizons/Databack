package databack.common.worldgen.carver.standin;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/// Stand-in for the chunk being carved: its states over `layers` layers from `minY`, at index
/// `((y - minY) * 16 + z) * 16 + x` (chunk-local x and z). Blocks outside it read as air and ignore writes.
@ApiStatus.Internal
public final class CvChunk {

    @NotNull
    private final CvChunkPos pos;
    private final int minY, layers;
    @NotNull
    private final CvBlockState[] states;

    /// Assumption: `states` holds `layers * 256` states; it is carved in place.
    public CvChunk(@NotNull CvChunkPos pos, int minY, int layers, @NotNull CvBlockState[] states) {
        this.pos = pos;
        this.minY = minY;
        this.layers = layers;
        this.states = states;
    }

    @NotNull
    public CvChunkPos getPos() {
        return pos;
    }

    @NotNull
    public CvBlockState getBlockState(@NotNull CvBlockPos at) {
        int index = index(at);
        return index < 0 ? CvBlocks.AIR.defaultBlockState() : states[index];
    }

    /// Returns the previous state.
    @NotNull
    public CvBlockState setBlockState(@NotNull CvBlockPos at, @NotNull CvBlockState state) {
        int index = index(at);
        if (index < 0) return CvBlocks.AIR.defaultBlockState();

        CvBlockState previous = states[index];
        states[index] = state;
        return previous;
    }

    public boolean isUpgrading() {
        return false;
    }

    /// Fluids next to carved blocks would be scheduled to flow; 1.7 updates them when they are next touched.
    public void markPosForPostprocessing(@NotNull CvBlockPos at) {}

    private int index(CvBlockPos at) {
        int x = at.getX() - pos.getMinBlockX(), y = at.getY() - minY, z = at.getZ() - pos.getMinBlockZ();
        if (x < 0 || x > 15 || z < 0 || z > 15 || y < 0 || y >= layers) return -1;
        return (y * 16 + z) * 16 + x;
    }
}
