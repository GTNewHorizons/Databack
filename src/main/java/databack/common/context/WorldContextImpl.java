package databack.common.context;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import net.minecraft.world.World;

@SuppressWarnings("unchecked")
public class WorldContextImpl implements WorldContext {

    private static final AtomicInteger COUNTER = new AtomicInteger(0);

    private final World world;

    @SuppressWarnings("rawtypes")
    private final ArrayList state = new ArrayList();

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
        return new Slot<>(COUNTER.incrementAndGet());
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

    private static class Slot<T> implements StateSlot<T> {
        public final int index;

        public Slot(int index) {
            this.index = index;
        }
    }
}
