package databack.common.context;

import net.minecraft.world.World;

import com.gtnewhorizon.gtnhlib.hash.Fnv1a64;
import databack.common.annotation.ThreadSafe;
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

    /// Creates a slot within ALL world contexts, which allows world-specific code to retrieve references without a map
    /// lookup (via [#getState(StateSlot)]). Internally, this just allocates an index within a list.
    @ThreadSafe
    <T> StateSlot<T> createStateSlot();

    <T> void setState(StateSlot<T> slot, T value);

    <T> T getState(StateSlot<T> slot);

    static WorldContext getContext(World world) {
        return ((WorldExt) world).db$getContext();
    }
}
