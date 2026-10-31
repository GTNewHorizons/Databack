package databack.common.interop.heightmap;

import net.minecraft.block.Block;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

import com.cardinalstar.cubicchunks.world.ICubicWorld;

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

    /// Walks down from the column's 1.7 height; 0 when nothing counts. With Cubic Chunks, walks down from `worldY`
    /// instead, to the world's lowest generated layer, and gives the layer below it when nothing counts.
    @Override
    public int getTop(int worldX, int worldY, int worldZ) {
        if (DBMods.CUBIC_CHUNKS.isModLoaded()) return getTopCC(worldX, worldY, worldZ);

        int baseTop = world.getHeightValue(worldX, worldZ);

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

    @Override
    public boolean counts(Block block, int meta, int x, int y, int z) {
        return predicate.test(world, block, meta, x, y, z);
    }

    /// Cubic Chunks' columns have no fixed bottom or 1.7 height.
    @Optional.Method(modid = "cubicchunks")
    private int getTopCC(int worldX, int worldY, int worldZ) {
        int bottom = ((ICubicWorld) world).getMinGenerationHeight();
        for (int y = worldY; y >= bottom; y--) {
            if (predicate.test(world, world.getBlock(worldX, y, worldZ), world.getBlockMetadata(worldX, y, worldZ),
                worldX, y, worldZ)) {
                return y;
            }
        }
        return bottom - 1;
    }
}
