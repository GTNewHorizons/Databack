package databack.common.worldgen.biome;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import databack.common.annotation.ThreadSafe;
import databack.common.worldgen.density.DensityMemo;
import databack.common.worldgen.density.DensityNode;
import databack.common.worldgen.density.DensityRouter;

/// Samples a quart cell's climate target point from a noise router (spec section 3).
@ThreadSafe
public final class ClimateSampler {

    /// Indexed like [ClimateEntry]'s parameters.
    @NotNull
    private final DensityNode[] fields;
    /// Each thread's copy of [#fields] that remembers shared subtrees and columns ([DensityMemo]): biome lookups
    /// sample neighbouring cells, and the same columns at several heights.
    private final ThreadLocal<DensityNode[]> memoized;

    private ClimateSampler(@NotNull DensityNode[] fields) {
        this.fields = fields;
        this.memoized = ThreadLocal.withInitial(() -> DensityMemo.memoize(fields));
    }

    @NotNull
    @Contract("_ -> new")
    public static ClimateSampler of(@NotNull DensityRouter router) {
        DensityNode[] fields = new DensityNode[ClimateEntry.PARAMETER_COUNT];
        fields[ClimateEntry.TEMPERATURE] = router.temperature;
        fields[ClimateEntry.HUMIDITY] = router.vegetation;
        fields[ClimateEntry.CONTINENTALNESS] = router.continents;
        fields[ClimateEntry.EROSION] = router.erosion;
        fields[ClimateEntry.DEPTH] = router.depth;
        fields[ClimateEntry.WEIRDNESS] = router.ridges;
        return new ClimateSampler(fields);
    }

    /// Writes the target point of quart cell `(quartX, quartY, quartZ)` into `target`, which must hold
    /// [ClimateEntry#PARAMETER_COUNT] values. Each field is sampled at the cell's minimum corner block and narrowed to
    /// `float` before quantizing.
    public void sample(int quartX, int quartY, int quartZ, long @NotNull [] target) {
        int x = quartX * 4, y = quartY * 4, z = quartZ * 4;
        DensityNode[] fields = memoized.get();
        for (int i = 0; i < ClimateEntry.PARAMETER_COUNT; i++) {
            target[i] = ClimateEntry.quantize((float) fields[i].sample(x, y, z));
        }
    }
}
