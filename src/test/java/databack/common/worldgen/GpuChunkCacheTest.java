package databack.common.worldgen;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;

import org.junit.jupiter.api.Test;

import com.gtnewhorizon.gtnhlib.util.CoordinatePacker2D;

/**
 * Unit tests for {@link GpuChunkCache}.
 *
 * Uses the package-private test constructor to replace GPU submission with a simple supplier,
 * so no Vulkan runtime is required.
 */
public class GpuChunkCacheTest {

    /** Supplier that returns a fresh float[16][4096] and records how many times it was called. */
    private static class CountingSupplier implements BiFunction<Integer, Integer, float[][]> {

        final Map<Long, Integer> callsPerColumn = new HashMap<>();

        @Override
        public float[][] apply(Integer cx, Integer cz) {
            long k = CoordinatePacker2D.packChunk(cx, cz);
            callsPerColumn.merge(k, 1, Integer::sum);
            return new float[16][4096];
        }

        int totalUniqueCalls() {
            return callsPerColumn.size();
        }

        int callsFor(int cx, int cz) {
            return callsPerColumn.getOrDefault(CoordinatePacker2D.packChunk(cx, cz), 0);
        }
    }

    @Test
    public void testCacheHit_supplierCalledOnce() {
        CountingSupplier supplier = new CountingSupplier();
        GpuChunkCache cache = new GpuChunkCache(1, supplier);

        float[][] first  = cache.getOrCompute(0, 0);
        float[][] second = cache.getOrCompute(0, 0);

        assertSame(first, second, "Second call must return the cached array");
        assertEquals(1, supplier.callsFor(0, 0), "Supplier must be called exactly once per column");
    }

    @Test
    public void testBatch_neighboursPreSubmitted() {
        int radius = 2;
        CountingSupplier supplier = new CountingSupplier();
        GpuChunkCache cache = new GpuChunkCache(radius, supplier);

        cache.getOrCompute(0, 0);

        int expected = (2 * radius + 1) * (2 * radius + 1); // 5×5 = 25
        assertEquals(expected, supplier.totalUniqueCalls(),
            "All columns in the batch radius must be submitted on first access");
    }

    @Test
    public void testNoDoubleSubmit_overlappingBatches() {
        int radius = 2;
        CountingSupplier supplier = new CountingSupplier();
        GpuChunkCache cache = new GpuChunkCache(radius, supplier);

        // First call: covers x ∈ [-2,2], z ∈ [-2,2] (25 columns).
        cache.getOrCompute(0, 0);
        // Second call: covers x ∈ [2,6], z ∈ [-2,2] (25 columns).
        // Overlap: x=2, z ∈ [-2,2] = 5 columns already submitted.
        cache.getOrCompute(4, 0);

        // 25 + 25 - 5 = 45 unique columns total.
        assertEquals(45, supplier.totalUniqueCalls(),
            "Overlapping batch columns must not be submitted twice");
    }

    @Test
    public void testCachedNeighbour_noResubmit() {
        int radius = 2;
        CountingSupplier supplier = new CountingSupplier();
        GpuChunkCache cache = new GpuChunkCache(radius, supplier);

        // (0,0) batch also pre-submits (1,1).
        cache.getOrCompute(0, 0);
        int countAfterFirst = supplier.totalUniqueCalls();

        // Requesting (1,1) directly — already cached, no new submission.
        cache.getOrCompute(1, 1);

        assertEquals(countAfterFirst, supplier.totalUniqueCalls(),
            "Neighbour pre-submitted by earlier batch must not be resubmitted");
    }

    @Test
    public void testReturnedArrayShape() {
        GpuChunkCache cache = new GpuChunkCache(0, (cx, cz) -> new float[16][4096]);

        float[][] densities = cache.getOrCompute(5, 3);

        assertEquals(16, densities.length, "Must have 16 Y-sections");
        for (int y = 0; y < 16; y++) {
            assertEquals(4096, densities[y].length, "Each section must have 4096 elements");
        }
    }

    @Test
    public void testBatchRadius0_onlyRequestedColumn() {
        AtomicInteger calls = new AtomicInteger();
        GpuChunkCache cache = new GpuChunkCache(0, (cx, cz) -> {
            calls.incrementAndGet();
            return new float[16][4096];
        });

        cache.getOrCompute(7, -3);

        assertEquals(1, calls.get(), "Radius 0 must submit exactly the requested column");
    }
}
