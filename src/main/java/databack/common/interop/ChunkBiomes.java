package databack.common.interop;

import net.minecraft.world.chunk.Chunk;

import org.jetbrains.annotations.NotNull;

import com.falsepattern.endlessids.mixin.helpers.ChunkBiomeHook;

import cpw.mods.fml.common.Loader;

/// How chunks store their 2D biome ids: 1.7's 8-bit storage, or EndlessIDs' 16-bit storage when it is installed.
/// EndlessIDs is optional: its classes are only touched by [#ENDLESS_IDS], which is only used when it is loaded.
public enum ChunkBiomes {

    VANILLA(1 << 8) {

        @Override
        public void write(@NotNull Chunk chunk, int @NotNull [] ids) {
            byte[] biomes = chunk.getBiomeArray();
            for (int i = 0; i < 256; i++) biomes[i] = (byte) ids[i];
        }
    },

    ENDLESS_IDS(1 << 16) {

        @Override
        public void write(@NotNull Chunk chunk, int @NotNull [] ids) {
            short[] biomes = ((ChunkBiomeHook) chunk).getBiomeShortArray();
            for (int i = 0; i < 256; i++) biomes[i] = (short) ids[i];
        }
    };

    private final int idCount;

    ChunkBiomes(int idCount) {
        this.idCount = idCount;
    }

    /// The storage in use. Valid from pre-init on.
    @NotNull
    public static ChunkBiomes current() {
        return Current.STORAGE;
    }

    /// The number of biome ids chunks can store. The highest one marks a column as not yet generated, so the usable ids
    /// are `0 .. idCount() - 2`.
    public int idCount() {
        return idCount;
    }

    /// Stores `ids` (indexed `z * 16 + x`, each below `idCount() - 1`) as the chunk's biomes.
    public abstract void write(@NotNull Chunk chunk, int @NotNull [] ids);

    /// Checked on first use, once mods are known.
    private static final class Current {

        static final ChunkBiomes STORAGE = Loader.isModLoaded("endlessids") ? ENDLESS_IDS : VANILLA;
    }
}
