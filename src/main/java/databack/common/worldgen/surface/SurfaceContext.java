package databack.common.worldgen.surface;

import java.util.IdentityHashMap;
import java.util.Map;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import databack.common.annotation.NotThreadSafe;

/// What surface conditions read about the block being decided (docs/worldgen/surface-rules.md section 1). One
/// instance per chunk being processed; [SurfaceSystem] updates it per column and per block.
@NotThreadSafe
public final class SurfaceContext {

    /// Marks "no fluid above" for [#fluidLevel].
    public static final int NO_FLUID = Integer.MIN_VALUE;

    /// The dimension's vertical range, for anchors.
    public final int minY;
    public final int height;
    public final long seed;

    @NotNull
    private final BlockBiomes biomes;

    int x, y, z;
    int floorDepth;
    int ceilingDepth;
    int fluidLevel = NO_FLUID;

    int surfaceDepth;
    double secondaryNoise;
    int preliminarySurface;
    boolean steep;

    /// Per-column values conditions compute once (e.g. noise thresholds), keyed by the condition.
    private final Map<Object, Object> columnCache = new IdentityHashMap<>();
    @Nullable
    private String biome;

    SurfaceContext(int minY, int height, long seed, @NotNull BlockBiomes biomes) {
        this.minY = minY;
        this.height = height;
        this.seed = seed;
        this.biomes = biomes;
    }

    void startColumn(int x, int z, int surfaceDepth, double secondaryNoise, int preliminarySurface, boolean steep) {
        this.x = x;
        this.z = z;
        this.surfaceDepth = surfaceDepth;
        this.secondaryNoise = secondaryNoise;
        this.preliminarySurface = preliminarySurface;
        this.steep = steep;
        this.fluidLevel = NO_FLUID;
        columnCache.clear();
    }

    void startBlock(int y, int floorDepth, int ceilingDepth) {
        this.y = y;
        this.floorDepth = floorDepth;
        this.ceilingDepth = ceilingDepth;
        this.biome = null;
    }

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    public int z() {
        return z;
    }

    /// 1 for the topmost solid block under an open block (section 1.1).
    public int floorDepth() {
        return floorDepth;
    }

    public int ceilingDepth() {
        return ceilingDepth;
    }

    /// One above the top of the first fluid above the block, or [#NO_FLUID].
    public int fluidLevel() {
        return fluidLevel;
    }

    public int surfaceDepth() {
        return surfaceDepth;
    }

    /// The secondary depth for range `range` (section 1.2).
    public int secondaryDepth(int range) {
        return (int) Math.floor((secondaryNoise + 1) / 2 * range);
    }

    public int preliminarySurface() {
        return preliminarySurface;
    }

    public boolean steep() {
        return steep;
    }

    /// The block's (zoomed) biome id; computed once per block.
    @Nullable
    public String biome() {
        if (biome == null) biome = biomes.biome(x, y, z);
        return biome;
    }

    /// A per-column value `owner` computed for this column, or null.
    @Nullable
    public Object columnValue(@NotNull Object owner) {
        return columnCache.get(owner);
    }

    public void setColumnValue(@NotNull Object owner, @NotNull Object value) {
        columnCache.put(owner, value);
    }
}
