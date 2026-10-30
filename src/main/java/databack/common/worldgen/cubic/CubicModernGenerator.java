package databack.common.worldgen.cubic;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
import com.cardinalstar.cubicchunks.api.util.Box;
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

import databack.common.worldgen.ModernWorldGenerator;
import databack.common.worldgen.feature.population.ChunkSetData;
import databack.common.worldgen.feature.population.DeferredPopulation;

/// [ModernWorldGenerator]'s terrain and features for a Cubic Chunks world, over the dimension's whole range.
///
/// A naive port: terrain is made a column at a time, as for 1.7, and kept for a while so the column's other cubes
/// don't make it again. Cubes outside the dimension's range are empty. Features are placed a column at a time once
/// its 3×3 is generated, and a column's cubes are finished once every column around it has its features.
@ParametersAreNonnullByDefault
public final class CubicModernGenerator implements IWorldGenerator, IPreloadFailureDelegate {

    /// Columns of terrain kept; each holds a block per position of the column.
    private static final int KEPT_COLUMNS = 32;

    @NotNull
    private final World world;
    @NotNull
    private final ModernWorldGenerator generator;
    private final int minCubeY, maxCubeY;

    /// Columns by [DeferredPopulation#key], oldest first. Guarded by itself.
    private final LinkedHashMap<Long, ImmutableBlockMeta[]> columns = new LinkedHashMap<Long, ImmutableBlockMeta[]>(
        KEPT_COLUMNS * 2, 0.75f, true) {

        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, ImmutableBlockMeta[]> eldest) {
            return size() > KEPT_COLUMNS;
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
        if (cubeY >= minCubeY && cubeY <= maxCubeY) fill(cube.getOrCreateStorage(), terrain(column), cubeY);

        if (!newColumn) return new GenerationResult<>(cube);

        column.generateSkylightMap();
        return new GenerationResult<>(cube, Collections.singletonList(column), null);
    }

    private void keep(Chunk column, ImmutableBlockMeta[] blocks) {
        synchronized (columns) {
            columns.put(DeferredPopulation.key(column.xPosition, column.zPosition), blocks);
        }
    }

    /// The column's terrain, made again if it's no longer kept (e.g. the column was loaded, but not this cube).
    private ImmutableBlockMeta[] terrain(Chunk column) {
        ImmutableBlockMeta[] blocks;
        synchronized (columns) {
            blocks = columns.get(DeferredPopulation.key(column.xPosition, column.zPosition));
        }
        if (blocks != null) return blocks;

        blocks = generator.generateColumn(column);
        keep(column, blocks);
        return blocks;
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

    /// Places the features of every column around the cube's that has none yet, then finishes the cube's whole column.
    /// Cubes outside the dimension's range have nothing to place.
    @Override
    public void populate(Cube cube) {
        int cubeX = cube.getX(), cubeZ = cube.getZ();
        if (cube.getY() < minCubeY || cube.getY() > maxCubeY) {
            cube.markPopulated(Cube.POP_ALL);
            return;
        }

        ICubeLoader loader = ((ICubicWorldInternal.Server) world).getCubeCache().getCubeLoader();
        try {
            loader.cacheCubes(cubeX - 2, minCubeY, cubeZ - 2, 5, maxCubeY - minCubeY + 1, 5);

            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) decorateOnce(loader, cubeX + dx, cubeZ + dz);
            }

            for (int cubeY = minCubeY; cubeY <= maxCubeY; cubeY++) {
                Cube finished = loader.getCube(cubeX, cubeY, cubeZ, Requirement.GENERATE);
                finished.markPopulated(Cube.POP_ALL);
                loader.onCubeGenerated(finished);
            }
        } finally {
            loader.uncacheCubes();
        }
    }

    /// Features write into the columns around theirs, so those are generated first.
    private void decorateOnce(ICubeLoader loader, int columnX, int columnZ) {
        ChunkSetData decorated = ChunkSetData.get(world, ChunkSetData.DECORATED_COLUMNS);
        long key = DeferredPopulation.key(columnX, columnZ);
        if (decorated.chunks().contains(key)) return;

        for (int x = columnX - 1; x <= columnX + 1; x++) {
            for (int z = columnZ - 1; z <= columnZ + 1; z++) {
                for (int cubeY = minCubeY; cubeY <= maxCubeY; cubeY++) loader.getCube(x, cubeY, z, Requirement.GENERATE);
            }
        }

        generator.decorate(columnX, columnZ);
        decorated.chunks().add(key);
        decorated.markDirty();
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
