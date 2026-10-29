package databack.common.worldgen.carver.standin;

import org.jetbrains.annotations.ApiStatus;

/// Stand-in for a chunk position (`carver/probes/chunk_pos.tsv`).
@ApiStatus.Internal
public final class CvChunkPos {

    public final int x, z;

    public CvChunkPos(int x, int z) {
        this.x = x;
        this.z = z;
    }

    public int getMinBlockX() {
        return x << 4;
    }

    public int getMinBlockZ() {
        return z << 4;
    }

    public int getBlockX(int offset) {
        return getMinBlockX() + offset;
    }

    public int getBlockZ(int offset) {
        return getMinBlockZ() + offset;
    }

    public int getMiddleBlockX() {
        return getBlockX(8);
    }

    public int getMiddleBlockZ() {
        return getBlockZ(8);
    }
}
