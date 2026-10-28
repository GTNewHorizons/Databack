package databack.common.worldgen.biome.zoom;

import org.jetbrains.annotations.ApiStatus;

/// Stand-in for vanilla's block position in rewritten biome zoom code.
@ApiStatus.Internal
public final class ZoomPos {

    private final int x;
    private final int y;
    private final int z;

    public ZoomPos(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getZ() {
        return z;
    }
}
