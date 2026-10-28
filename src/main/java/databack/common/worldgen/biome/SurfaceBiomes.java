package databack.common.worldgen.biome;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import databack.common.annotation.ThreadSafe;
import databack.common.worldgen.biome.zoom.BiomeZoom;
import databack.common.worldgen.biome.zoom.ZoomSource;
import databack.common.worldgen.surface.BlockBiomes;

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
        if (zoom != null) return zoom.zoom(cells, zoomSeed)::biome;
        return (x, y, z) -> cells.getNoiseBiome(x >> 2, y >> 2, z >> 2);
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
    private static final class CachedCells implements ZoomSource<String> {

        private final ZoomSource<String> source;
        private final Map<Cell, String> cells = new HashMap<>();

        CachedCells(ZoomSource<String> source) {
            this.source = source;
        }

        @Override
        public String getNoiseBiome(int quartX, int quartY, int quartZ) {
            Cell key = new Cell(quartX, quartY, quartZ);
            if (cells.containsKey(key)) return cells.get(key);

            String biome = source.getNoiseBiome(quartX, quartY, quartZ);
            cells.put(key, biome);
            return biome;
        }
    }

    private static final class Cell {

        private final int x;
        private final int y;
        private final int z;

        Cell(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Cell other && x == other.x && y == other.y && z == other.z;
        }

        @Override
        public int hashCode() {
            return Objects.hash(x, y, z);
        }
    }
}
