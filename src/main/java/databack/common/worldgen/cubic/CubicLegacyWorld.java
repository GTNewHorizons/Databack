package databack.common.worldgen.cubic;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.ParametersAreNonnullByDefault;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.cardinalstar.cubicchunks.server.chunkio.ICubeLoader;
import com.cardinalstar.cubicchunks.util.CubePos;
import com.cardinalstar.cubicchunks.world.api.ICubeProviderServer.Requirement;
import com.cardinalstar.cubicchunks.world.cube.Cube;
import com.gtnewhorizon.gtnhlib.util.data.ImmutableBlockMeta;

import databack.common.interop.heightmap.Heightmap;
import databack.common.interop.heightmap.HeightmapType;
import databack.common.interop.heightmap.WorldHeightmapExt;
import databack.common.worldgen.feature.world.LegacyWorld;

/// A Cubic Chunks world as features see it while populating, making as few cubes as it can.
///
/// A cube that was never made holds just its column's terrain, so reads take the terrain instead of making the cube.
/// Cubes are made (generated, not populated) only where a feature writes, or asks whether a block can stay or a side
/// is solid, which 1.7's blocks answer from the world.
///
/// Remembers which cubes exist, so is meant for one population: cubes made elsewhere meanwhile aren't seen.
@ParametersAreNonnullByDefault
final class CubicLegacyWorld implements LegacyWorld {

    /// The generator's terrain, before features.
    interface Terrain {

        /// The column's blocks over the generator's range, laid out as
        /// [databack.common.worldgen.ModernWorldGenerator#generateColumn].
        @NotNull
        ImmutableBlockMeta @NotNull [] blocks(int columnX, int columnZ);

        /// As [LegacyWorld#surface].
        int surface(@NotNull HeightmapType type, int x, int z);
    }

    private static final HeightmapType[] TYPES = HeightmapType.values();

    @NotNull
    private final World world;
    @NotNull
    private final ICubeLoader loader;
    @NotNull
    private final Terrain terrain;
    private final int minY, height;
    /// Null for cubes known not to exist.
    private final Map<CubePos, Cube> cubes = new HashMap<>();
    /// By [#surfaceKey], until a write could move them.
    private final Map<Long, Integer> surfaces = new HashMap<>();

    CubicLegacyWorld(World world, ICubeLoader loader, Terrain terrain, int minY, int height) {
        this.world = world;
        this.loader = loader;
        this.terrain = terrain;
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
        if (!isInRange(y)) return Blocks.air;

        Cube cube = existing(x >> 4, y >> 4, z >> 4);
        return cube != null ? cube.getBlock(x, y, z) : terrainAt(x, y, z).getBlock();
    }

    @Override
    public int getMeta(int x, int y, int z) {
        if (!isInRange(y)) return 0;

        Cube cube = existing(x >> 4, y >> 4, z >> 4);
        return cube != null ? cube.getBlockMetadata(x, y, z) : terrainAt(x, y, z).getBlockMeta();
    }

    /// Through the world (players are told, neighbours aren't), once the cube is made.
    @Override
    public void setBlock(int x, int y, int z, @NotNull Block block, int meta) {
        if (!isInRange(y)) return;

        generate(x >> 4, y >> 4, z >> 4);
        world.setBlock(x, y, z, block, meta, 2);
        written(x, y, z);
    }

    /// A heightmap's surface is the layer above its top block, so writing below the top can't move it.
    private void written(int x, int y, int z) {
        for (HeightmapType type : TYPES) {
            Long key = surfaceKey(type, x, z);
            Integer surface = surfaces.get(key);
            if (surface != null && y >= surface - 1) surfaces.remove(key);
        }
    }

    @Override
    public boolean isSideSolid(int x, int y, int z, @NotNull ForgeDirection side) {
        generateAround(x, y, z);
        return world.isSideSolid(x, y, z, side, false);
    }

    @Override
    public boolean canBlockStay(@NotNull Block block, int x, int y, int z) {
        generateAround(x, y, z);
        return block.canBlockStay(world, x, y, z);
    }

    /// Walks down from the range's top through the cubes that exist. A cube that was never made holds just terrain,
    /// so the terrain's own heightmap answers for it.
    @Override
    public int surface(@NotNull HeightmapType type, int x, int z) {
        Long key = surfaceKey(type, x, z);
        Integer known = surfaces.get(key);
        if (known != null) return known;

        int surface = walk(type, x, z);
        surfaces.put(key, surface);
        return surface;
    }

    private int walk(HeightmapType type, int x, int z) {
        Heightmap heightmap = WorldHeightmapExt.getHeightmap(world, type);
        int terrainSurface = terrain.surface(type, x, z);

        int top = minY + height - 1;
        for (int cubeY = top >> 4; cubeY >= minY >> 4; cubeY--) {
            int cubeMinY = Math.max(minY, cubeY << 4), cubeMaxY = Math.min(top, cubeY << 4 | 15);

            Cube cube = existing(x >> 4, cubeY, z >> 4);
            if (cube != null) {
                if (cube.isEmpty()) continue;

                for (int y = cubeMaxY; y >= cubeMinY; y--) {
                    Block block = cube.getBlock(x, y, z);
                    if (heightmap.counts(block, cube.getBlockMetadata(x, y, z), x, y, z)) return y + 1;
                }
            } else if (terrainSurface - 1 >= cubeMinY) {
                if (terrainSurface - 1 <= cubeMaxY) return terrainSurface;

                // The terrain's top is in a cube above, since changed; this one's blocks are still the terrain's
                for (int y = cubeMaxY; y >= cubeMinY; y--) {
                    ImmutableBlockMeta block = terrainAt(x, y, z);
                    if (heightmap.counts(block.getBlock(), block.getBlockMeta(), x, y, z)) return y + 1;
                }
            }
        }
        return minY;
    }

    private static long surfaceKey(HeightmapType type, int x, int z) {
        return ((long) x & 0x3FFFFFF) << 29 | ((long) z & 0x3FFFFFF) << 3 | type.ordinal();
    }

    @Override
    public void scheduleUpdate(int x, int y, int z, @NotNull Block block, int delay) {
        world.scheduleBlockUpdate(x, y, z, block, delay);
    }

    private boolean isInRange(int y) {
        return y >= minY && y < minY + height;
    }

    private ImmutableBlockMeta terrainAt(int x, int y, int z) {
        return terrain.blocks(x >> 4, z >> 4)[index(x, y, z)];
    }

    private int index(int x, int y, int z) {
        return ((y - minY) * 16 + (z & 15)) * 16 + (x & 15);
    }

    /// Loaded or saved; null if it was never made.
    @Nullable
    private Cube existing(int cubeX, int cubeY, int cubeZ) {
        CubePos pos = new CubePos(cubeX, cubeY, cubeZ);
        if (cubes.containsKey(pos)) return cubes.get(pos);

        Cube cube = loader.getCube(cubeX, cubeY, cubeZ, Requirement.LOAD);
        cubes.put(pos, cube);
        return cube;
    }

    private void generate(int cubeX, int cubeY, int cubeZ) {
        if (existing(cubeX, cubeY, cubeZ) != null) return;

        cubes.put(new CubePos(cubeX, cubeY, cubeZ), loader.getCube(cubeX, cubeY, cubeZ, Requirement.GENERATE));
    }

    /// The cubes of the block and its neighbours, which a block asks about.
    private void generateAround(int x, int y, int z) {
        for (int cubeY = (y - 1) >> 4; cubeY <= (y + 1) >> 4; cubeY++) {
            if (!isInRange(cubeY << 4)) continue;

            for (int cubeX = (x - 1) >> 4; cubeX <= (x + 1) >> 4; cubeX++) {
                for (int cubeZ = (z - 1) >> 4; cubeZ <= (z + 1) >> 4; cubeZ++) generate(cubeX, cubeY, cubeZ);
            }
        }
    }
}
