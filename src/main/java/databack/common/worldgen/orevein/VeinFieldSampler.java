package databack.common.worldgen.orevein;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import databack.common.annotation.ThreadSafe;
import databack.common.worldgen.density.ChunkDensitySampler;
import databack.common.worldgen.density.DensityNode;
import databack.common.worldgen.density.DensityRouter;
import databack.common.worldgen.density.MarkerNode;

/// Samples a router's vein fields for whole chunks, with the same cell interpolation as `final_density`, over the part
/// of the generated range veins can occupy. Thread-safe like [ChunkDensitySampler].
@ThreadSafe
public final class VeinFieldSampler {

    /// Corner columns kept for neighbouring chunks, as for `final_density`.
    private static final int COLUMN_CACHE_SIZE = 4096;

    @NotNull
    private final ChunkDensitySampler toggle, ridged;
    /// `vein_gap` for whole chunks; null when it is read point by point ([#isPointReadable]).
    @Nullable
    private final ChunkDensitySampler gap;
    @NotNull
    private final DensityNode gapField;

    private VeinFieldSampler(
        @NotNull ChunkDensitySampler toggle, @NotNull ChunkDensitySampler ridged, @Nullable ChunkDensitySampler gap,
        @NotNull DensityNode gapField
    ) {
        this.toggle = toggle;
        this.ridged = ridged;
        this.gap = gap;
        this.gapField = gapField;
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
            isPointReadable(router.veinGap) ? null
                : sampler(router.veinGap, cellWidth, cellHeight, bottom, top, dimensionMinY, dimensionMaxY),
            router.veinGap);
    }

    /// Whether a field reads the same at a single block as inside a chunk: it holds no caching markers, whose chunk
    /// state (interpolation, flat caches) would change its values. Vanilla's `vein_gap` is a bare noise.
    private static boolean isPointReadable(DensityNode field) {
        return !hasMarker(field, Collections.newSetFromMap(new IdentityHashMap<>()));
    }

    private static boolean hasMarker(DensityNode node, Set<DensityNode> visited) {
        if (!visited.add(node)) return false;
        if (node instanceof MarkerNode) return true;
        for (DensityNode input : node.inputs()) {
            if (hasMarker(input, visited)) return true;
        }
        return false;
    }

    private static ChunkDensitySampler sampler(
        DensityNode field, int cellWidth, int cellHeight, int minY, int maxY, int dimensionMinY, int dimensionMaxY
    ) {
        return new ChunkDensitySampler(field, cellWidth, cellHeight, minY, maxY, dimensionMinY, dimensionMaxY,
            COLUMN_CACHE_SIZE, false);
    }

    /// The fields of chunk `(chunkX, chunkZ)`.
    @NotNull
    public VeinFields sample(int chunkX, int chunkZ) {
        double[] toggleValues = sample(toggle, chunkX, chunkZ), ridgedValues = sample(ridged, chunkX, chunkZ);
        if (gap != null) {
            return new VeinFields(toggle.minY(), toggle.height(), toggleValues, ridgedValues,
                sample(gap, chunkX, chunkZ));
        }

        int originX = chunkX * 16, originZ = chunkZ * 16;
        return new VeinFields(toggle.minY(), toggle.height(), toggleValues, ridgedValues,
            (x, y, z) -> gapField.sample(originX + x, y, originZ + z));
    }

    private static double[] sample(ChunkDensitySampler sampler, int chunkX, int chunkZ) {
        double[] values = sampler.newBuffer();
        sampler.sample(chunkX, chunkZ, values);
        return values;
    }
}
