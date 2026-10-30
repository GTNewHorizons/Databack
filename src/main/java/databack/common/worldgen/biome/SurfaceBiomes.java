package databack.common.worldgen.biome;

import java.util.Objects;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.gtnewhorizon.gtnhlib.util.CoordinatePacker;
import databack.common.annotation.ThreadSafe;
import databack.common.worldgen.biome.zoom.BiomeZoom;
import databack.common.worldgen.biome.zoom.ZoomSource;
import databack.common.worldgen.biome.zoom.ZoomedBiomes;
import databack.common.worldgen.surface.BlockBiomes;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

/// The 2D biome of each column of a chunk: the biome at the column's surface block, as the zoom picks it from a biome
/// source (spec section 5). Without a zoom, a block uses its own quart cell's biome, which is wrong near borders.
@ThreadSafe
public final class SurfaceBiomes {

    @NotNull
    private final ZoomSource<String> source;
    @Nullable
    private final BiomeZoom zoom;
    private final long zoomSeed;

    /// `zoom` may be null when it is unavailable; `zoomSeed` is then ignored.
    public SurfaceBiomes(@NotNull ZoomSource<String> source, @Nullable BiomeZoom zoom, long zoomSeed) {
        this.source = source;
        this.zoom = zoom;
        this.zoomSeed = zoomSeed;
    }

    /// Per-block biomes for one chunk's work: each quart cell is computed once. Not thread-safe; use one per chunk.
    @NotNull
    public BlockBiomes forChunk() {
        CachedCells cells = new CachedCells(source);
        if (zoom != null) return new ZoomedBlocks(cells, zoom.zoom(cells, zoomSeed));
        return (x, y, z) -> cells.getNoiseBiome(x >> 2, y >> 2, z >> 2);
    }

    /// The zoom picks one of the 8 corners of the quart cube at `floor((b - 2) / 4)` (docs/worldgen/biome-source.md
    /// section 5.2), so where all 8 share a biome, every block zooming from that cube gets it; the zoom only runs where
    /// they differ. Each cube is checked once. Not thread-safe.
    private static final class ZoomedBlocks implements BlockBiomes {

        /// Marks a cube whose corners differ; compared by identity.
        @SuppressWarnings("StringOperationCanBeSimplified")
        private static final String MIXED = new String("mixed");

        private final CachedCells cells;
        private final ZoomedBiomes<String> zoomed;
        private final Long2ObjectOpenHashMap<String> cubes = new Long2ObjectOpenHashMap<>();

        ZoomedBlocks(CachedCells cells, ZoomedBiomes<String> zoomed) {
            this.cells = cells;
            this.zoomed = zoomed;
        }

        @Nullable
        @Override
        public String biome(int x, int y, int z) {
            int cubeX = x - 2 >> 2, cubeY = y - 2 >> 2, cubeZ = z - 2 >> 2;
            long key = CoordinatePacker.pack(cubeX, cubeY, cubeZ);

            String uniform = cubes.get(key);
            if (uniform == null && !cubes.containsKey(key)) {
                uniform = uniformBiome(cubeX, cubeY, cubeZ);
                cubes.put(key, uniform);
            }
            return uniform == MIXED ? zoomed.biome(x, y, z) : uniform;
        }

        /// The corners' shared biome, or [#MIXED].
        @Nullable
        private String uniformBiome(int cubeX, int cubeY, int cubeZ) {
            String first = cells.getNoiseBiome(cubeX, cubeY, cubeZ);
            for (int corner = 1; corner < 8; corner++) {
                String other = cells.getNoiseBiome(cubeX + (corner & 1), cubeY + (corner >> 1 & 1),
                    cubeZ + (corner >> 2));
                if (!Objects.equals(first, other)) return MIXED;
            }
            return first;
        }
    }

    /// The biome id of each column of chunk `(chunkX, chunkZ)`, indexed `z * 16 + x`, at `surfaceY` (same indexing;
    /// see [SurfaceHeights]). Columns at [SurfaceHeights#NONE] use `fallbackY` instead. Ids are null where the source
    /// has none.
    @NotNull
    public String[] columns(int chunkX, int chunkZ, int @NotNull [] surfaceY, int fallbackY) {
        return columns(forChunk(), chunkX, chunkZ, surfaceY, fallbackY);
    }

    /// As the other `columns`, through `blocks` (from [#forChunk]).
    @NotNull
    public String[] columns(
        @NotNull BlockBiomes blocks, int chunkX, int chunkZ, int @NotNull [] surfaceY, int fallbackY
    ) {
        String[] biomes = new String[256];
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                int y = surfaceY[z * 16 + x];
                biomes[z * 16 + x] = blocks.biome(chunkX * 16 + x, y == SurfaceHeights.NONE ? fallbackY : y,
                    chunkZ * 16 + z);
            }
        }

        return biomes;
    }

    /// One chunk's quart cell biomes, each computed once. Neighbouring columns mostly read the same few cells.
    /// The source's cells, each computed once. Not thread-safe.
    private static final class CachedCells implements ZoomSource<String> {

        private final ZoomSource<String> source;
        private final Long2ObjectOpenHashMap<String> cells = new Long2ObjectOpenHashMap<>();

        CachedCells(ZoomSource<String> source) {
            this.source = source;
        }

        @Override
        public String getNoiseBiome(int quartX, int quartY, int quartZ) {
            long key = CoordinatePacker.pack(quartX, quartY, quartZ);
            String biome = cells.get(key);
            if (biome != null || cells.containsKey(key)) return biome;

            biome = source.getNoiseBiome(quartX, quartY, quartZ);
            cells.put(key, biome);
            return biome;
        }
    }
}
