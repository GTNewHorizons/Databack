package databack.common.worldgen;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Consumer;

import com.gtnewhorizon.gtnhlib.datastructs.space.HashMap2D;
import com.gtnewhorizon.gtnhlib.datastructs.space.HashSet2D;
import com.gtnewhorizon.gtnhlib.space.ImmutableXZ;
import com.gtnewhorizon.gtnhlib.space.XZAddressable;
import mcgpu.core.hwaccel.scheduling.ComputePlan;
import mcgpu.core.hwaccel.scheduling.KernelScheduler;

import databack.common.worldgen.dag.DFPlanBuilder;

/**
 * Batches GPU density-function evaluation across chunk columns and caches the results.
 *
 * <p>When a column is first requested, all unsubmitted columns within {@link #batchRadius} are
 * collected into a single {@link KernelScheduler#submit} call. Because {@code submit} is
 * blocking, every terminal callback has fired and every {@code float[][]} in {@link #computed}
 * is fully populated before {@link #getOrCompute} returns.
 *
 * <h3>Buffer layout</h3>
 * {@code float[][] densities = getOrCompute(cx, cz)} returns one {@code float[4096]} per Y-section
 * (indices 0–15). Each section is indexed {@code z*256 + y*16 + x} (local coords 0–15).
 * A positive value means solid; zero or negative means air.
 *
 * <h3>Thread safety</h3>
 * Not thread-safe. Must be called from a single thread (the server thread).
 */
public class GpuChunkCache {

    static final int DEFAULT_BATCH_RADIUS = 4;

    private final DFPlanBuilder planBuilder;
    private final KernelScheduler scheduler;
    /** Non-null only in tests; replaces the GPU path. */
    private final BiFunction<Integer, Integer, float[][]> testSupplier;
    final int batchRadius;

    /** Fully computed columns: key → float[16][4096]. */
    private final HashMap2D<float[][]> computed = new HashMap2D<>();
    /** Columns already submitted (or complete) — prevents double-submission. */
    private final HashSet2D submitted = new HashSet2D();

    public GpuChunkCache(DFPlanBuilder planBuilder, KernelScheduler scheduler) {
        this(planBuilder, scheduler, null, DEFAULT_BATCH_RADIUS);
    }

    /** Test constructor: bypasses GPU entirely, using {@code supplier} as the density source. */
    GpuChunkCache(int batchRadius, BiFunction<Integer, Integer, float[][]> supplier) {
        this(null, null, supplier, batchRadius);
    }

    private GpuChunkCache(DFPlanBuilder planBuilder, KernelScheduler scheduler,
            BiFunction<Integer, Integer, float[][]> testSupplier, int batchRadius) {
        this.planBuilder  = planBuilder;
        this.scheduler    = scheduler;
        this.testSupplier = testSupplier;
        this.batchRadius  = batchRadius;
    }

    /**
     * Returns the pre-computed density data for the given chunk column, submitting a GPU batch
     * first if the column has not yet been evaluated.
     *
     * @return {@code float[16][4096]} — one section per Y-level, indexed {@code z*256+y*16+x}
     */
    public float[][] getOrCompute(int chunkX, int chunkZ) {
        if (computed.containsKey(chunkX, chunkZ)) return computed.get(chunkX, chunkZ);

        List<XZAddressable> batch = new ArrayList<>();
        for (int dx = -batchRadius; dx <= batchRadius; dx++) {
            for (int dz = -batchRadius; dz <= batchRadius; dz++) {
                int cx = chunkX + dx, cz = chunkZ + dz;

                if (submitted.add(cx, cz)) {
                    batch.add(new ImmutableXZ(cx, cz));
                }
            }
        }

        if (!batch.isEmpty()) {
            if (testSupplier != null) {
                submitBatchTest(batch);
            } else {
                submitBatchGpu(batch);
            }
        }

        return computed.get(chunkX, chunkZ);
    }

    private void submitBatchTest(List<XZAddressable> columns) {
        for (XZAddressable col : columns) {
            computed.put(col, testSupplier.apply(col.getX(), col.getZ()));
        }
    }

    private void submitBatchGpu(List<XZAddressable> columns) {
        List<ComputePlan> plans = new ArrayList<>(columns.size());

        for (XZAddressable col : columns) {
            float[][] densities = new float[16][4096];
            computed.put(col, densities);

            Map<Integer, Consumer<FloatBuffer>> consumers = new HashMap<>();
            for (int y = 0; y < 16; y++) {
                float[] section = densities[y];
                // buf.get(array) copies from the current position (0) into section.
                consumers.put(y, buf -> buf.get(section));
            }

            plans.add(planBuilder.createPlan(col.getX(), col.getZ(), consumers));
        }

        // Blocking: spins until all GPU work and terminal callbacks complete.
        scheduler.submit(plans);
    }
}
