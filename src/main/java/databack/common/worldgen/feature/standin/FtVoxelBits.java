package databack.common.worldgen.feature.standin;

import java.util.BitSet;

import org.jetbrains.annotations.ApiStatus;

/// Stand-in for a box of on/off cells, as trees mark their blocks in.
@ApiStatus.Internal
public final class FtVoxelBits {

    private final int xSize, ySize, zSize;
    private final BitSet cells;

    public FtVoxelBits(int xSize, int ySize, int zSize) {
        this.xSize = xSize;
        this.ySize = ySize;
        this.zSize = zSize;
        this.cells = new BitSet(xSize * ySize * zSize);
    }

    private int index(int x, int y, int z) {
        return (x * ySize + y) * zSize + z;
    }

    private boolean contains(int x, int y, int z) {
        return x >= 0 && y >= 0 && z >= 0 && x < xSize && y < ySize && z < zSize;
    }

    public void fill(int x, int y, int z) {
        if (contains(x, y, z)) cells.set(index(x, y, z));
    }

    public boolean isFull(int x, int y, int z) {
        return contains(x, y, z) && cells.get(index(x, y, z));
    }
}
