package databack.common.interop.heightmap;

import net.minecraft.block.Block;

public interface Heightmap {

    int getTop(int worldX, int worldY, int worldZ);

    /// Whether `block` (with `meta`), at a position, counts towards the heightmap, for callers that walk blocks the
    /// world doesn't hold yet.
    boolean counts(Block block, int meta, int x, int y, int z);
}
