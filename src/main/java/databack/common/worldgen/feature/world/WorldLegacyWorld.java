package databack.common.worldgen.feature.world;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.util.ForgeDirection;

import org.jetbrains.annotations.NotNull;

import databack.common.interop.heightmap.HeightmapType;
import databack.common.interop.heightmap.WorldHeightmapExt;
import databack.mixins.DBMods;

/// [LegacyWorld] over a 1.7 world, whose layers are `minY..minY + height`.
public final class WorldLegacyWorld implements LegacyWorld {

    @NotNull
    private final World world;
    private final int minY, height;

    public WorldLegacyWorld(@NotNull World world, int minY, int height) {
        this.world = world;
        this.minY = minY;
        this.height = height;
    }

    @Override
    public int minY() {
        return minY;
    }

    @Override
    public int height() {
        return height;
    }

    @NotNull
    @Override
    public Block getBlock(int x, int y, int z) {
        return world.blockExists(x, y, z) ? world.getBlock(x, y, z) : Blocks.air;
    }

    @Override
    public int getMeta(int x, int y, int z) {
        return world.blockExists(x, y, z) ? world.getBlockMetadata(x, y, z) : 0;
    }

    /// Writes into the chunk, as 1.7's own `setBlock` does first, then does only what the write needs. Light is
    /// updated only when the light or opacity changes (ore replacing stone changes neither), and players watching the
    /// chunk are sent the change. Other world listeners aren't told: the block is generated, not placed.
    ///
    /// With Cubic Chunks, blocks live in cubes rather than the column's sections, so writes go through the world
    /// instead (with the same flags: players are told, neighbours aren't).
    @Override
    public void setBlock(int x, int y, int z, @NotNull Block block, int meta) {
        if (DBMods.CUBIC_CHUNKS.isModLoaded()) {
            world.setBlock(x, y, z, block, meta, 2);
            return;
        }

        if (y < 0 || y >= world.getHeight()) return;

        Chunk chunk = world.getChunkFromChunkCoords(x >> 4, z >> 4);
        Block old = chunk.getBlock(x & 15, y, z & 15);
        // Sets the block and meta; false when both are already there
        if (!chunk.func_150807_a(x & 15, y, z & 15, block, meta)) return;

        // Updates block and sky light around the block
        if (old.getLightValue() != block.getLightValue() || old.getLightOpacity() != block.getLightOpacity()) {
            world.func_147451_t(x, y, z);
        }
        if (world instanceof WorldServer server) server.getPlayerManager().markBlockForUpdate(x, y, z);
    }

    @Override
    public boolean isSideSolid(int x, int y, int z, @NotNull ForgeDirection side) {
        return world.isSideSolid(x, y, z, side, false);
    }

    @Override
    public boolean canBlockStay(@NotNull Block block, int x, int y, int z) {
        return block.canBlockStay(world, x, y, z);
    }

    /// The heightmap gives the top block, or 0 when the column has none.
    @Override
    public int surface(@NotNull HeightmapType heightmap, int x, int z) {
        int top = WorldHeightmapExt.getHeightmap(world, heightmap).getTop(x, minY + height - 1, z);
        return Math.max(top + 1, minY);
    }

    @Override
    public void scheduleUpdate(int x, int y, int z, @NotNull Block block, int delay) {
        world.scheduleBlockUpdate(x, y, z, block, delay);
    }
}
