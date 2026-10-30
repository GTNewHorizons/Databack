package databack.common.worldgen.biome;

import java.util.concurrent.atomic.AtomicReferenceArray;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.gtnewhorizon.gtnhlib.util.CoordinatePacker;
import databack.common.annotation.ThreadSafe;
import databack.common.worldgen.biome.zoom.ZoomSource;

/// Remembers a biome source's recent quart cells, for everything that asks the same cells again: neighbouring chunks'
/// zooms, carvers asking about every chunk in range, and feature decoration. A fixed-size table where a new cell
/// replaces whichever shared its slot; entries are immutable, so readers need no lock.
@ThreadSafe
public final class CachedBiomeSource implements ZoomSource<String> {

    private static final int SLOTS = 1 << 16;

    private static final class Cell {

        final long key;
        @Nullable
        final String biome;

        Cell(long key, @Nullable String biome) {
            this.key = key;
            this.biome = biome;
        }
    }

    @NotNull
    private final ZoomSource<String> source;
    private final AtomicReferenceArray<Cell> cells = new AtomicReferenceArray<>(SLOTS);

    public CachedBiomeSource(@NotNull ZoomSource<String> source) {
        this.source = source;
    }

    @Nullable
    @Override
    public String getNoiseBiome(int quartX, int quartY, int quartZ) {
        // Quart coordinates are a subset of block coordinates, so they pack the same way
        long key = CoordinatePacker.pack(quartX, quartY, quartZ);
        int slot = (int) (key * 0x9E3779B97F4A7C15L >>> 48);

        Cell cell = cells.get(slot);
        if (cell != null && cell.key == key) return cell.biome;

        String biome = source.getNoiseBiome(quartX, quartY, quartZ);
        cells.set(slot, new Cell(key, biome));
        return biome;
    }
}
