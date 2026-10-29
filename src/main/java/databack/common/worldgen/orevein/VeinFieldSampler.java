package databack.common.worldgen.orevein;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import databack.common.annotation.ThreadSafe;
import databack.common.worldgen.density.ChunkDensitySampler;
import databack.common.worldgen.density.DensityNode;
import databack.common.worldgen.density.DensityRouter;

/// Samples a router's vein fields for whole chunks, with the same cell interpolation as `final_density`, over the part
/// of the generated range veins can occupy. Thread-safe like [ChunkDensitySampler].
@ThreadSafe
public final class VeinFieldSampler {

    @NotNull
    private final ChunkDensitySampler toggle, ridged, gap;

    private VeinFieldSampler(
        @NotNull ChunkDensitySampler toggle, @NotNull ChunkDensitySampler ridged, @NotNull ChunkDensitySampler gap
    ) {
        this.toggle = toggle;
        this.ridged = ridged;
        this.gap = gap;
    }

    /// Null if the generated range `[minY, maxY)` holds no vein heights. Cell sizes and the dimension's range are as
    /// for [ChunkDensitySampler].
    @Nullable
    public static VeinFieldSampler create(
        @NotNull DensityRouter router, int cellWidth, int cellHeight, int minY, int maxY, int dimensionMinY,
        int dimensionMaxY
    ) {
        int bottom = Math.max(minY, OreVeinSystem.MIN_Y), top = Math.min(maxY, OreVeinSystem.MAX_Y + 1);
        if (bottom >= top) return null;

        return new VeinFieldSampler(
            sampler(router.veinToggle, cellWidth, cellHeight, bottom, top, dimensionMinY, dimensionMaxY),
            sampler(router.veinRidged, cellWidth, cellHeight, bottom, top, dimensionMinY, dimensionMaxY),
            sampler(router.veinGap, cellWidth, cellHeight, bottom, top, dimensionMinY, dimensionMaxY));
    }

    private static ChunkDensitySampler sampler(
        DensityNode field, int cellWidth, int cellHeight, int minY, int maxY, int dimensionMinY, int dimensionMaxY
    ) {
        return new ChunkDensitySampler(field, cellWidth, cellHeight, minY, maxY, dimensionMinY, dimensionMaxY, 0,
            false);
    }

    /// The fields of chunk `(chunkX, chunkZ)`.
    @NotNull
    public VeinFields sample(int chunkX, int chunkZ) {
        return new VeinFields(toggle.minY(), toggle.height(), sample(toggle, chunkX, chunkZ),
            sample(ridged, chunkX, chunkZ), sample(gap, chunkX, chunkZ));
    }

    private static double[] sample(ChunkDensitySampler sampler, int chunkX, int chunkZ) {
        double[] values = sampler.newBuffer();
        sampler.sample(chunkX, chunkZ, values);
        return values;
    }
}
