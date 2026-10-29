package databack.common.worldgen.feature.population;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;

import org.jetbrains.annotations.NotNull;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;

/// The chunks of a dimension waiting for decoration ([DeferredPopulation]), saved with the dimension.
public final class PendingPopulationData extends WorldSavedData {

    public static final String NAME = "databack_pending_population";

    private final LongOpenHashSet chunks = new LongOpenHashSet();

    /// Called by [net.minecraft.world.storage.MapStorage] when loading.
    public PendingPopulationData(String name) {
        super(name);
    }

    /// The dimension's data, created if it has none.
    @NotNull
    public static PendingPopulationData get(@NotNull World world) {
        PendingPopulationData data = (PendingPopulationData) world.perWorldStorage.loadData(PendingPopulationData.class,
            NAME);
        if (data == null) {
            data = new PendingPopulationData(NAME);
            world.perWorldStorage.setData(NAME, data);
        }
        return data;
    }

    /// Live: changes must be followed by [#markDirty].
    @NotNull
    public LongSet chunks() {
        return chunks;
    }

    /// Stored as x, z pairs: 1.7's NBT has no long arrays.
    @Override
    public void readFromNBT(NBTTagCompound tag) {
        chunks.clear();
        int[] pairs = tag.getIntArray("chunks");
        for (int i = 0; i + 1 < pairs.length; i += 2) chunks.add(DeferredPopulation.key(pairs[i], pairs[i + 1]));
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        int[] pairs = new int[chunks.size() * 2];
        int i = 0;
        for (long key : chunks) {
            pairs[i++] = DeferredPopulation.keyX(key);
            pairs[i++] = DeferredPopulation.keyZ(key);
        }
        tag.setIntArray("chunks", pairs);
    }
}
