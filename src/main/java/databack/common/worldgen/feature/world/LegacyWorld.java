package databack.common.worldgen.feature.world;

import net.minecraft.block.Block;
import net.minecraftforge.common.util.ForgeDirection;

import org.jetbrains.annotations.NotNull;

import databack.common.interop.heightmap.HeightmapType;

/// The 1.7 world operations features need, so [PopulationLevel] can be tested without a world.
public interface LegacyWorld {

    int minY();

    /// The number of block layers.
    int height();

    /// Air where the world has no chunk: reading must not generate one.
    @NotNull
    Block getBlock(int x, int y, int z);

    int getMeta(int x, int y, int z);

    /// Sets the block without updating its neighbours.
    void setBlock(int x, int y, int z, @NotNull Block block, int meta);

    boolean isSideSolid(int x, int y, int z, @NotNull ForgeDirection side);

    /// Whether `block` could stay at the position, whatever is there now.
    boolean canBlockStay(@NotNull Block block, int x, int y, int z);

    /// The first Y above the surface `heightmap` tracks in column `(x, z)`; [#minY] for an empty column.
    int surface(@NotNull HeightmapType heightmap, int x, int z);

    void scheduleUpdate(int x, int y, int z, @NotNull Block block, int delay);
}
