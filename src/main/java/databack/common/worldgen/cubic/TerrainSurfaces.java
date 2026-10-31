package databack.common.worldgen.cubic;

import javax.annotation.ParametersAreNonnullByDefault;

import net.minecraft.init.Blocks;
import net.minecraft.world.World;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import com.gtnewhorizon.gtnhlib.util.data.ImmutableBlockMeta;

import databack.common.interop.heightmap.Heightmap;
import databack.common.interop.heightmap.HeightmapType;
import databack.common.interop.heightmap.WorldHeightmapExt;

/// A column's heightmaps before features, from its terrain, so cubes that were never made can answer for their part
/// of a heightmap without the terrain itself. Immutable.
@ParametersAreNonnullByDefault
final class TerrainSurfaces {

    private static final HeightmapType[] TYPES = HeightmapType.values();

    /// By type, then `z * 16 + x`: the first Y above the top block that counts, or `minY` if none does.
    private final int[][] surfaces = new int[TYPES.length][256];

    private TerrainSurfaces() {}

    /// `blocks` is laid out as [databack.common.worldgen.ModernWorldGenerator#generateColumn], over layers
    /// `minY..minY + height`.
    @NotNull
    @Contract("_, _, _, _, _, _ -> new")
    static TerrainSurfaces of(World world, ImmutableBlockMeta[] blocks, int columnX, int columnZ, int minY,
        int height) {
        TerrainSurfaces out = new TerrainSurfaces();
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                int top = topNonAir(blocks, x, z, height);
                for (HeightmapType type : TYPES) {
                    out.surfaces[type.ordinal()][z * 16 + x] = surface(WorldHeightmapExt.getHeightmap(world, type),
                        blocks, top, columnX << 4 | x, columnZ << 4 | z, minY);
                }
            }
        }
        return out;
    }

    /// As [databack.common.worldgen.feature.world.LegacyWorld#surface], for the terrain alone.
    @Contract(pure = true)
    int surface(HeightmapType type, int x, int z) {
        return surfaces[type.ordinal()][(z & 15) * 16 + (x & 15)];
    }

    /// The highest layer above which the column holds only air; -1 if it's all air. Every heightmap's top is at or
    /// below it.
    private static int topNonAir(ImmutableBlockMeta[] blocks, int x, int z, int height) {
        int layer = height - 1;
        while (layer >= 0 && blocks[(layer * 16 + z) * 16 + x].getBlock() == Blocks.air) layer--;
        return layer;
    }

    private static int surface(Heightmap heightmap, ImmutableBlockMeta[] blocks, int top, int worldX, int worldZ,
        int minY) {
        int x = worldX & 15, z = worldZ & 15;
        for (int layer = top; layer >= 0; layer--) {
            ImmutableBlockMeta block = blocks[(layer * 16 + z) * 16 + x];
            if (heightmap.counts(block.getBlock(), block.getBlockMeta(), worldX, layer + minY, worldZ)) {
                return layer + minY + 1;
            }
        }
        return minY;
    }
}
