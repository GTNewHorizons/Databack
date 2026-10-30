package databack.common.interop.heightmap;

import net.minecraft.block.Block;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

import cpw.mods.fml.common.Optional;
import databack.mixins.DBMods;

public class CustomHeightmap implements Heightmap {

    /// Whether `block` (with `meta`), at a position, counts towards the heightmap.
    @FunctionalInterface
    public interface BlockTest {

        boolean test(World world, Block block, int meta, int x, int y, int z);
    }

    private final World world;
    private final BlockTest predicate;

    public CustomHeightmap(World world, BlockTest predicate) {
        this.world = world;
        this.predicate = predicate;
    }

    /// Walks down from the column's 1.7 height; 0 when nothing counts.
    @Override
    public int getTop(int worldX, int worldY, int worldZ) {
        int baseTop = world.getHeightValue(worldX, worldZ);
        if (DBMods.CUBIC_CHUNKS.isModLoaded()) return getTopThroughWorld(worldX, baseTop, worldZ);

        // One chunk lookup for the whole column, instead of one per block
        Chunk chunk = world.getChunkFromBlockCoords(worldX, worldZ);
        int x = worldX & 15, z = worldZ & 15;
        for (int y = baseTop; y >= 0; y--) {
            if (predicate.test(world, chunk.getBlock(x, y, z), chunk.getBlockMetadata(x, y, z), worldX, y, worldZ)) {
                return y;
            }
        }
        return 0;
    }

    /// Cubic Chunks' columns have no bottom, so the walk has no floor.
    private int getTopThroughWorld(int worldX, int baseTop, int worldZ) {
        int y = baseTop;
        while (!predicate.test(world, world.getBlock(worldX, y, worldZ), world.getBlockMetadata(worldX, y, worldZ),
            worldX, y, worldZ)) {
            y--;
        }
        return y;
    }

    @Optional.Method(modid = "cubicchunks")
    private int getTopCC(int worldX, int worldY, int worldZ) {
        throw new UnsupportedOperationException("not yet implemented");
    }
}
