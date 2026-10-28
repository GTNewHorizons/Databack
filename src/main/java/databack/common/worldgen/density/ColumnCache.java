package databack.common.worldgen.density;

import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.concurrent.atomic.LongAdder;

/// A thread-safe, fixed-size, direct-mapped cache of interpolation corner columns, keyed by absolute cell
/// coordinates. Lets adjacent chunks reuse the corners on their shared edges (see [PureChunkEvaluator]).
///
/// Only valid for pure trees ([ChunkAnalysis#isPure]), where a corner's value doesn't depend on which chunk computes
/// it. One cache belongs to one [ChunkPlan]: every entry holds `interpolators x (cellsY + 1)` values.
///
/// Collisions simply replace the older entry. Entries are immutable once published, so readers never need a lock.
public final class ColumnCache {

    private static final class Entry {

        final long key;
        final double[][] values;

        Entry(long key, double[][] values) {
            this.key = key;
            this.values = values;
        }
    }

    private final AtomicReferenceArray<Entry> entries;
    private final int mask;

    private final LongAdder hits = new LongAdder(), misses = new LongAdder();

    /// @param capacity rounded up to a power of two
    public ColumnCache(int capacity) {
        int size = Integer.highestOneBit(Math.max(16, capacity - 1)) << 1;
        this.entries = new AtomicReferenceArray<>(size);
        this.mask = size - 1;
    }

    private static long key(int cellX, int cellZ) {
        return (long) cellX & 0xFFFFFFFFL | ((long) cellZ & 0xFFFFFFFFL) << 32;
    }

    private int index(long key) {
        long h = key * 0x9E3779B97F4A7C15L;
        return (int) (h >>> 40) & mask;
    }

    double[][] get(int cellX, int cellZ) {
        long key = key(cellX, cellZ);
        Entry e = entries.get(index(key));
        if (e != null && e.key == key) {
            hits.increment();
            return e.values;
        }
        misses.increment();
        return null;
    }

    void put(int cellX, int cellZ, double[][] values) {
        long key = key(cellX, cellZ);
        entries.set(index(key), new Entry(key, values));
    }

    public long hits() {
        return hits.sum();
    }

    public long misses() {
        return misses.sum();
    }
}
