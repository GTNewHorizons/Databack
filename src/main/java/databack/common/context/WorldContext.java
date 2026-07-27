package databack.common.context;

import java.util.function.Consumer;

import net.minecraft.world.World;

import com.gtnewhorizon.gtnhlib.hash.Fnv1a64;
import databack.common.annotation.ThreadSafe;
import databack.common.dto.worldgen.density_function.DensityBuffer;
import databack.common.dto.worldgen.density_function.DensityBuffer.CubeBuffer;
import databack.common.dto.worldgen.density_function.DensityMask;
import databack.common.mixinext.WorldExt;

/// A context object that contains all useful information about a world. Can also store arbitrary data for density
/// functions, similar to a [ThreadLocal].
public interface WorldContext {

    World getWorld();

    long getSeed();

    default long getDimensionSeed() {
        long seed = Fnv1a64.initialState();
        seed = Fnv1a64.hashStep(seed, getSeed());
        return Fnv1a64.hashStep(seed, getWorld().provider.dimensionId);
    }

    /// Creates a state slot within ALL world contexts, which allows world-specific code to retrieve references without a map
    /// lookup (via [#getState(StateSlot)]). Internally, this just allocates an index within a list.
    /// State references are kept around forever, until the [World] garbage collects.
    @ThreadSafe
    <T> StateSlot<T> createStateSlot();

    <T> void setState(StateSlot<T> slot, T value);

    <T> T getState(StateSlot<T> slot);

    /// Creates a cache slot within ALL world contexts, which allows world-specific code to retrieve references without
    /// a map lookup (via [#getCache(CacheSlot)(StateSlot)]). Internally, this just allocates an index within a list.
    /// Cache references are reset to null each time a new chunk is generated/populated.
    @ThreadSafe
    <T> CacheSlot<T> createCacheSlot();

    /// Same as [#createCacheSlot()], except the reference is reset with the given lambda instead of set to null.
    @ThreadSafe
    <T> CacheSlot<T> createCacheSlot(Consumer<T> resetter);

    void resetCache();

    <T> void setCache(CacheSlot<T> slot, T value);

    <T> T getCache(CacheSlot<T> slot);

    DensityMask getMask();
    void releaseMask(DensityMask mask);

    /// Gets a 16x16x16 float array from the pool. The contained values are undefined.
    CubeBuffer getCubeBuffer();
    void releaseCubeBuffer(CubeBuffer buffer);

    static WorldContext getContext(World world) {
        return ((WorldExt) world).db$getContext();
    }
}
