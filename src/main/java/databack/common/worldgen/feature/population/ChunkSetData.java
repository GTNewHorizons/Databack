package databack.common.worldgen.feature.population;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;

import org.jetbrains.annotations.NotNull;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;

/// A set of a dimension's chunks (or other keys packed in a long), saved with the dimension under a name.
public final class ChunkSetData extends WorldSavedData {

    /// The chunks waiting for decoration ([DeferredPopulation]).
    public static final String PENDING_POPULATION = "databack_pending_population";
    /// The cube layers of a Cubic Chunks world's columns whose features are placed, by
    /// `databack.common.worldgen.cubic.CubicModernGenerator#bandKey`.
    public static final String DECORATED_BANDS = "databack_decorated_bands";

    private final LongOpenHashSet chunks = new LongOpenHashSet();

    /// Called by [net.minecraft.world.storage.MapStorage] when loading.
    public ChunkSetData(String name) {
        super(name);
    }

    /// The dimension's set called `name`, created if it has none.
    @NotNull
    public static ChunkSetData get(@NotNull World world, @NotNull String name) {
        ChunkSetData data = (ChunkSetData) world.perWorldStorage.loadData(ChunkSetData.class, name);
        if (data == null) {
            data = new ChunkSetData(name);
            world.perWorldStorage.setData(name, data);
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
