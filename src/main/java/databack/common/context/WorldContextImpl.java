package databack.common.context;

import java.util.ArrayList;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import net.minecraft.world.World;

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

    public WorldContextImpl(World world) {
        this.world = world;
    }

    @Override
    public World getWorld() {
        return world;
    }

    @Override
    public long getSeed() {
        return world.getSeed();
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

    private static class Slot<T> implements StateSlot<T>, CacheSlot<T> {
        public final int index;

        public Slot(int index) {
            this.index = index;
        }
    }
}
