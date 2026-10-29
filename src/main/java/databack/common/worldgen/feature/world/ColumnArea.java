package databack.common.worldgen.feature.world;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/// A box of block columns, bounds inclusive.
public final class ColumnArea {

    public final int minX, minZ, maxX, maxZ;

    public ColumnArea(int minX, int minZ, int maxX, int maxZ) {
        this.minX = minX;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxZ = maxZ;
    }

    /// The columns of chunks `minChunkX..maxChunkX` by `minChunkZ..maxChunkZ`, inclusive.
    @NotNull
    @Contract("_, _, _, _ -> new")
    public static ColumnArea ofChunks(int minChunkX, int minChunkZ, int maxChunkX, int maxChunkZ) {
        return new ColumnArea(minChunkX << 4, minChunkZ << 4, (maxChunkX << 4) + 15, (maxChunkZ << 4) + 15);
    }

    @Contract(pure = true)
    public boolean contains(int x, int z) {
        return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
    }

    @Override
    public String toString() {
        return "[" + minX + ", " + minZ + "].." + "[" + maxX + ", " + maxZ + "]";
    }
}
