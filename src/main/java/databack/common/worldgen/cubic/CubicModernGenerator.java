package databack.common.worldgen.cubic;

import java.util.Collections;
import java.util.List;

import javax.annotation.ParametersAreNonnullByDefault;

import net.minecraft.entity.EnumCreatureType;
import net.minecraft.init.Blocks;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.ChunkPosition;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.cardinalstar.cubicchunks.api.ICube;
import com.cardinalstar.cubicchunks.api.worldgen.GenerationResult;
import com.cardinalstar.cubicchunks.api.worldgen.IWorldGenerator;
import com.cardinalstar.cubicchunks.mixin.api.ICubicWorldInternal;
import com.cardinalstar.cubicchunks.server.chunkio.CubeInitLevel;
import com.cardinalstar.cubicchunks.server.chunkio.ICubeLoader;
import com.cardinalstar.cubicchunks.server.chunkio.IPreloadFailureDelegate;
import com.cardinalstar.cubicchunks.util.CubePos;
import com.cardinalstar.cubicchunks.world.api.ICubeProviderServer.Requirement;
import com.cardinalstar.cubicchunks.world.cube.Cube;
import com.gtnewhorizon.gtnhlib.util.data.ImmutableBlockMeta;

import databack.common.interop.heightmap.HeightmapType;
import databack.common.worldgen.ModernWorldGenerator;
import databack.common.worldgen.feature.population.ChunkSetData;
import databack.common.worldgen.feature.population.DeferredPopulation;

/// [ModernWorldGenerator]'s terrain and features for a Cubic Chunks world, over the dimension's whole range.
///
/// Terrain is made a column at a time, as for 1.7, and kept for a while so the column's other cubes don't make it
/// again. Cubes outside the dimension's range are empty. Features are placed a cube's layers of a column at a time
/// ([#populate]), so populating a cube makes only the cubes its features touch.
@ParametersAreNonnullByDefault
public final class CubicModernGenerator implements IWorldGenerator, IPreloadFailureDelegate {

    /// Columns of terrain kept; each holds a block per position of the column.
    private static final int KEPT_COLUMNS = 32;
    /// Columns of terrain heightmaps kept, which population asks for long after the terrain is dropped; each is 6 KiB.
    private static final int KEPT_SURFACES = 2048;

    @NotNull
    private final World world;
    @NotNull
    private final ModernWorldGenerator generator;
    private final int minCubeY, maxCubeY;

    /// By [DeferredPopulation#key].
    private final LruCache<ImmutableBlockMeta[]> columns = new LruCache<>(KEPT_COLUMNS);
    /// By [DeferredPopulation#key].
    private final LruCache<TerrainSurfaces> surfaces = new LruCache<>(KEPT_SURFACES);
    private final CubicLegacyWorld.Terrain terrain = new CubicLegacyWorld.Terrain() {

        @NotNull
        @Override
        public ImmutableBlockMeta @NotNull [] blocks(int columnX, int columnZ) {
            return terrain(columnX, columnZ);
        }

        @Override
        public int surface(@NotNull HeightmapType type, int x, int z) {
            return terrainSurfaces(x >> 4, z >> 4).surface(type, x, z);
        }
    };

    /// Assumption: `generator` was made for a cubic world.
    public CubicModernGenerator(@NotNull World world, @NotNull ModernWorldGenerator generator) {
        this.world = world;
        this.generator = generator;
        this.minCubeY = generator.minY() >> 4;
        this.maxCubeY = (generator.minY() + generator.height() - 1) >> 4;
    }

    @Override
    public GenerationResult<Chunk> provideColumn(World world, int columnX, int columnZ) {
        Chunk column = new Chunk(world, columnX, columnZ);
        keep(column, generator.generateColumn(column));
        return new GenerationResult<>(column);
    }

    @Override
    public GenerationResult<Cube> provideCube(@Nullable Chunk column, int cubeX, int cubeY, int cubeZ) {
        boolean newColumn = column == null;
        if (newColumn) column = provideColumn(world, cubeX, cubeZ).object;

        Cube cube = new Cube(column, cubeY);
        if (isInRange(cubeY)) fill(cube.getOrCreateStorage(), terrain(column), cubeY);

        if (!newColumn) return new GenerationResult<>(cube);

        column.generateSkylightMap();
        return new GenerationResult<>(cube, Collections.singletonList(column), null);
    }

    /// Keeps the column's terrain and its heightmaps, which it gives back.
    private TerrainSurfaces keep(Chunk column, ImmutableBlockMeta[] blocks) {
        long key = DeferredPopulation.key(column.xPosition, column.zPosition);
        TerrainSurfaces surfaces = TerrainSurfaces.of(world, blocks, column.xPosition, column.zPosition,
            generator.minY(), generator.height());

        columns.put(key, blocks);
        this.surfaces.put(key, surfaces);
        return surfaces;
    }

    /// The column's terrain, made again if it's no longer kept (e.g. the column was loaded, but not this cube).
    private ImmutableBlockMeta[] terrain(Chunk column) {
        ImmutableBlockMeta[] blocks = columns.get(DeferredPopulation.key(column.xPosition, column.zPosition));
        if (blocks != null) return blocks;

        blocks = generator.generateColumn(column);
        keep(column, blocks);
        return blocks;
    }

    private ImmutableBlockMeta[] terrain(int columnX, int columnZ) {
        return terrain(loader().getColumn(columnX, columnZ, Requirement.GENERATE));
    }

    /// Made again with the terrain if they're no longer kept.
    private TerrainSurfaces terrainSurfaces(int columnX, int columnZ) {
        long key = DeferredPopulation.key(columnX, columnZ);
        TerrainSurfaces kept = surfaces.get(key);
        if (kept != null) return kept;

        Chunk column = loader().getColumn(columnX, columnZ, Requirement.GENERATE);
        return keep(column, generator.generateColumn(column));
    }

    /// Copies cube `cubeY`'s layers of the column's terrain into `ebs`, laid out as [ModernWorldGenerator#generateColumn].
    private void fill(ExtendedBlockStorage ebs, ImmutableBlockMeta[] blocks, int cubeY) {
        for (int y = 0; y < 16; y++) {
            int layer = (cubeY << 4 | y) - generator.minY();
            if (layer < 0 || layer >= generator.height()) continue;

            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    ImmutableBlockMeta block = blocks[(layer * 16 + z) * 16 + x];
                    if (block.getBlock() == Blocks.air) continue;

                    ebs.func_150818_a(x, y, z, block.getBlock());
                    if (block.getBlockMeta() != 0) ebs.setExtBlockMetadata(x, y, z, block.getBlockMeta());
                }
            }
        }
    }

    /// Places the features positioned in the cube's layers and the layers of the cubes above and below, for its column
    /// and the columns around it, unless they're placed already. Those are the features that write into the cube;
    /// features reaching further (tall trees) may still write into it later. Cubes are made only where features need
    /// them ([CubicLegacyWorld]).
    @Override
    public void populate(Cube cube) {
        if (isInRange(cube.getY())) decorateAround(cube.getX(), cube.getY(), cube.getZ());
        cube.markPopulated(Cube.POP_ALL);
    }

    private void decorateAround(int cubeX, int cubeY, int cubeZ) {
        CubicLegacyWorld view = new CubicLegacyWorld(world, loader(), terrain, generator.minY(),
            generator.height());
        ChunkSetData decorated = ChunkSetData.get(world, ChunkSetData.DECORATED_BANDS);

        for (int y = cubeY - 1; y <= cubeY + 1; y++) {
            if (!isInRange(y)) continue;

            for (int x = cubeX - 1; x <= cubeX + 1; x++) {
                for (int z = cubeZ - 1; z <= cubeZ + 1; z++) decorateOnce(view, decorated, x, y, z);
            }
        }
    }

    private void decorateOnce(CubicLegacyWorld view, ChunkSetData decorated, int columnX, int cubeY, int columnZ) {
        if (!decorated.chunks().add(bandKey(columnX, cubeY, columnZ))) return;
        decorated.markDirty();

        generator.decorateBand(columnX, columnZ, cubeY << 4, cubeY << 4 | 15, view);
    }

    /// A key for [ChunkSetData], unique for columns within ±2^21 and cube layers within ±2^19.
    static long bandKey(int columnX, int cubeY, int columnZ) {
        return ((long) columnX & 0x3FFFFF) << 42 | ((long) columnZ & 0x3FFFFF) << 20 | cubeY & 0xFFFFF;
    }

    private boolean isInRange(int cubeY) {
        return cubeY >= minCubeY && cubeY <= maxCubeY;
    }

    private ICubeLoader loader() {
        return ((ICubicWorldInternal.Server) world).getCubeCache().getCubeLoader();
    }

    /// Called on a worker thread for columns that aren't saved: their densities, and their neighbours' (which
    /// populating them needs), are started early.
    @Override
    public void onColumnPreloadFailed(ChunkCoordIntPair pos) {
        prefetchAround(pos.chunkXPos, pos.chunkZPos);
    }

    @Override
    public void onCubePreloadFailed(CubePos pos, CubeInitLevel actual, CubeInitLevel wanted) {
        if (actual.ordinal() < CubeInitLevel.Generated.ordinal()
            && wanted.ordinal() >= CubeInitLevel.Generated.ordinal()) {
            prefetchAround(pos.getX(), pos.getZ());
        }
    }

    private void prefetchAround(int columnX, int columnZ) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) generator.prefetch(columnX + dx, columnZ + dz);
        }
    }

    @Override
    public void recreateStructures(ICube cube) {}

    @Override
    public void recreateStructures(Chunk column) {
        generator.recreateStructures(column.xPosition, column.zPosition);
    }

    @Override
    public List<BiomeGenBase.SpawnListEntry> getPossibleCreatures(EnumCreatureType type, int x, int y, int z) {
        return generator.getPossibleCreatures(type, x, y, z);
    }

    @Nullable
    @Override
    public ChunkPosition getNearestStructure(String name, int x, int y, int z) {
        return generator.func_147416_a(world, name, x, y, z);
    }
}
