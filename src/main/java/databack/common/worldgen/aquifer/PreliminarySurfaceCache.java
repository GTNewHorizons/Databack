package databack.common.worldgen.aquifer;

import java.util.concurrent.atomic.AtomicReferenceArray;

import org.jetbrains.annotations.NotNull;

import databack.common.annotation.ThreadSafe;
import databack.common.worldgen.density.DensityNode;

/// The preliminary surface level of each 4x4-column cell, memoized. The aquifer asks for the same cells many times per
/// chunk, and neighbouring chunks (and the noise and carver steps of one chunk) share them; each costs a column scan.
///
/// A fixed-size, direct-mapped cache: collisions replace the older entry, and entries are immutable once published,
/// so readers never lock. Assumption: the level is a pure function of the column.
@ThreadSafe
final class PreliminarySurfaceCache {

    private static final int CAPACITY = 4096;

    private static final class Entry {

        final int cellX, cellZ, level;

        Entry(int cellX, int cellZ, int level) {
            this.cellX = cellX;
            this.cellZ = cellZ;
            this.level = level;
        }
    }

    @NotNull
    private final DensityNode preliminarySurface;
    private final AtomicReferenceArray<Entry> entries = new AtomicReferenceArray<>(CAPACITY);

    PreliminarySurfaceCache(@NotNull DensityNode preliminarySurface) {
        this.preliminarySurface = preliminarySurface;
    }

    /// The level of the cell containing column `(x, z)`, sampled at the cell's corner.
    int at(int x, int z) {
        int cellX = x >> 2, cellZ = z >> 2;
        int index = index(cellX, cellZ);

        Entry entry = entries.get(index);
        if (entry != null && entry.cellX == cellX && entry.cellZ == cellZ) return entry.level;

        int level = (int) Math.floor(preliminarySurface.sample(cellX << 2, 0, cellZ << 2));
        entries.set(index, new Entry(cellX, cellZ, level));
        return level;
    }

    private static int index(int cellX, int cellZ) {
        long h = ((long) cellX & 0xFFFFFFFFL | (long) cellZ << 32) * 0x9E3779B97F4A7C15L;
        return (int) (h >>> 40) & (CAPACITY - 1);
    }
}
