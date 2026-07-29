package databack.common.context;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import net.minecraft.world.World;

import databack.common.dto.worldgen.density_function.DensityBuffer.CubeBuffer;
import databack.common.dto.worldgen.density_function.DensityMask;
import databack.common.worldgen.rng.RandomFactory;
import databack.common.worldgen.rng.RandomSource;

@SuppressWarnings("unchecked")
public class WorldContextImpl implements WorldContext {

    private static final AtomicInteger STATE_COUNTER = new AtomicInteger(0);
    private static final AtomicInteger CACHE_COUNTER = new AtomicInteger(0);

    @SuppressWarnings("rawtypes")
    private static final CopyOnWriteArrayList<Consumer> CACHE_RESETTERS = new CopyOnWriteArrayList<>();

    private final World world;

    @SuppressWarnings("rawtypes")
    private final ArrayList state = new ArrayList();

    @SuppressWarnings("rawtypes")
    private final ArrayList cache = new ArrayList();

    private RandomFactory rng;

    public WorldContextImpl(World world) {
        this.world = world;
    }

    @Override
    public World getWorld() {
        return world;
    }

    public void setRandom(RandomFactory rng) {
        this.rng = rng;
    }

    @Override
    public RandomFactory getRandom() {
        return rng;
    }

    @Override
    public <T> StateSlot<T> createStateSlot() {
        return new Slot<>(STATE_COUNTER.incrementAndGet());
    }

    @Override
    public <T> void setState(StateSlot<T> slot, T value) {
        int index = ((Slot<T>) slot).index;

        while (state.size() <= index) {
            state.add(null);
        }

        state.set(index, value);
    }

    @Override
    public <T> T getState(StateSlot<T> slot) {
        int index = ((Slot<T>) slot).index;

        return index >= state.size() ? null : (T) state.get(index);
    }

    @Override
    public <T> CacheSlot<T> createCacheSlot() {
        return new Slot<>(CACHE_COUNTER.incrementAndGet());
    }

    @Override
    public <T> CacheSlot<T> createCacheSlot(Consumer<T> resetter) {
        int index = CACHE_COUNTER.incrementAndGet();

        while (CACHE_RESETTERS.size() <= index) {
            CACHE_RESETTERS.add(null);
        }

        CACHE_RESETTERS.set(index, resetter);

        return new Slot<>(index);
    }

    @Override
    public void resetCache() {
        int cacheLen = cache.size();
        int resetterLen = CACHE_RESETTERS.size();

        for (int i = 0; i < cacheLen; i++) {
            @SuppressWarnings("rawtypes")
            Consumer reset = i < resetterLen ? CACHE_RESETTERS.get(i) : null;

            Object value = cache.get(i);

            if (value == null) continue;

            if (reset != null) {
                reset.accept(value);
            } else {
                cache.set(i, null);
            }
        }
    }

    @Override
    public <T> void setCache(CacheSlot<T> slot, T value) {
        int index = ((Slot<T>) slot).index;

        while (cache.size() <= index) {
            cache.add(null);
        }

        cache.set(index, value);
    }

    @Override
    public <T> T getCache(CacheSlot<T> slot) {
        int index = ((Slot<T>) slot).index;

        return index >= cache.size() ? null : (T) cache.get(index);
    }

    /** Allocates a {@link StateSlot} without needing a {@code WorldContext} instance.
     *  The resulting slot is valid across all {@code WorldContextImpl} instances because
     *  {@code STATE_COUNTER} is global. */
    public static <T> StateSlot<T> allocateSlot() {
        return new Slot<>(STATE_COUNTER.incrementAndGet());
    }

    /** Allocates a {@link CacheSlot} without needing a {@code WorldContext} instance.
     *  Equivalent to {@code createCacheSlot(resetter)} but callable at compile time. */
    public static <T> CacheSlot<T> allocateCacheSlot(Consumer<T> resetter) {
        int index = CACHE_COUNTER.incrementAndGet();

        while (CACHE_RESETTERS.size() <= index) {
            CACHE_RESETTERS.add(null);
        }

        CACHE_RESETTERS.set(index, resetter);

        return new Slot<>(index);
    }

    private final ArrayDeque<DensityMask> maskPool = new ArrayDeque<>();
    private final ArrayDeque<CubeBuffer> bufferPool = new ArrayDeque<>();

    @Override
    public DensityMask getMask() {
        DensityMask mask = maskPool.poll();
        return mask != null ? mask : new DensityMask();
    }

    @Override
    public void releaseMask(DensityMask mask) {
        mask.clear();
        maskPool.push(mask);
    }

    @Override
    public CubeBuffer getCubeBuffer() {
        CubeBuffer buf = bufferPool.poll();
        return buf != null ? buf : new CubeBuffer(this::releaseCubeBuffer);
    }

    @Override
    public void releaseCubeBuffer(CubeBuffer buffer) {
        bufferPool.push(buffer);
    }

    private static class Slot<T> implements StateSlot<T>, CacheSlot<T> {
        public final int index;

        public Slot(int index) {
            this.index = index;
        }
    }
}
