package databack.common.worldgen.biome.zoom;

import org.jetbrains.annotations.NotNull;

import databack.common.annotation.ThreadSafe;

/// Per-block biomes for one world: vanilla's biome zoom over a [ZoomSource]. Thread-safe if the source is.
@ThreadSafe
public final class ZoomedBiomes<T> {

    @NotNull
    private final ZoomLookup lookup;

    ZoomedBiomes(@NotNull ZoomLookup lookup) {
        this.lookup = lookup;
    }

    /// The biome of block `(x, y, z)`: the source's biome for the single quart cell the zoom picks for this block
    /// (one of the 8 around it; see docs/worldgen/biome-source.md section 5).
    @SuppressWarnings("unchecked")
    public T biome(int x, int y, int z) {
        return (T) lookup.getBiome(new ZoomPos(x, y, z));
    }
}
