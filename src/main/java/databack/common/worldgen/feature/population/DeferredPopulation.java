package databack.common.worldgen.feature.population;

import javax.annotation.Nonnegative;

import org.jetbrains.annotations.NotNull;

import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;

/// Decorates each generated chunk once all 8 of its neighbours are generated, as modern does: features may write
/// anywhere in that 3×3.
///
/// 1.7 populates a 2×2 window of chunks as soon as it is loaded; [#onWindowComplete] is that signal. The last chunk
/// of any 3×3 to be generated completes such a window with chunks of that 3×3, so checking the chunks around each
/// completed window finds every chunk ready to decorate. Ready chunks are queued, and [#tick] decorates a few at a
/// time, so a burst of generation doesn't decorate everything in one tick. Server thread only.
public final class DeferredPopulation {

    /// Which chunks exist, loaded or saved.
    @FunctionalInterface
    public interface Chunks {

        boolean isGenerated(int chunkX, int chunkZ);
    }

    /// Decorates a chunk whose 3×3 is generated.
    @FunctionalInterface
    public interface Decorator {

        void decorate(int chunkX, int chunkZ);
    }

    /// The generated chunks not yet decorated, as [#key]s; persisted by the caller. Queued chunks stay here until
    /// they are decorated, so a restart loses none.
    @NotNull
    private final LongSet pending;
    /// Called whenever [#pending] changes.
    @NotNull
    private final Runnable changed;
    /// The pending chunks found ready, oldest first.
    private final LongLinkedOpenHashSet ready = new LongLinkedOpenHashSet();
    /// Whether the chunks saved ready have been queued; see [#tick].
    private boolean queuedSaved;

    public DeferredPopulation(@NotNull LongSet pending, @NotNull Runnable changed) {
        this.pending = pending;
        this.changed = changed;
    }

    public static long key(int chunkX, int chunkZ) {
        return (long) chunkX << 32 | chunkZ & 0xFFFFFFFFL;
    }

    public static int keyX(long key) {
        return (int) (key >> 32);
    }

    public static int keyZ(long key) {
        return (int) key;
    }

    /// Records a freshly generated chunk as waiting for decoration.
    public void markGenerated(int chunkX, int chunkZ) {
        if (pending.add(key(chunkX, chunkZ))) changed.run();
    }

    public boolean isPending(int chunkX, int chunkZ) {
        return pending.contains(key(chunkX, chunkZ));
    }

    /// How many chunks wait for [#tick].
    public int queued() {
        return ready.size();
    }

    /// Queues every pending chunk near the window of chunks `chunkX..chunkX + 1` by `chunkZ..chunkZ + 1` whose 3×3 is
    /// generated.
    public void onWindowComplete(int chunkX, int chunkZ, @NotNull Chunks chunks) {
        for (int x = chunkX - 1; x <= chunkX + 2; x++) {
            for (int z = chunkZ - 1; z <= chunkZ + 2; z++) {
                if (isPending(x, z) && isSurrounded(x, z, chunks)) ready.add(key(x, z));
            }
        }
    }

    /// Decorates up to `limit` queued chunks, oldest first, and returns how many. The first call also queues the
    /// chunks that were saved ready. Decorating may load chunks and so complete more windows; their chunks wait for
    /// a later call.
    public int tick(@Nonnegative int limit, @NotNull Chunks chunks, @NotNull Decorator decorator) {
        if (!queuedSaved) {
            queuedSaved = true;
            queueSaved(chunks);
        }

        int decorated = 0;
        while (decorated < limit && !ready.isEmpty()) {
            long key = ready.removeFirstLong();
            // Removed first: a failing decoration is not retried forever.
            if (!pending.remove(key)) continue;
            changed.run();

            decorated++;
            decorator.decorate(keyX(key), keyZ(key));
        }
        return decorated;
    }

    private void queueSaved(Chunks chunks) {
        for (LongIterator it = pending.iterator(); it.hasNext();) {
            long key = it.nextLong();
            if (isSurrounded(keyX(key), keyZ(key), chunks)) ready.add(key);
        }
    }

    private static boolean isSurrounded(int chunkX, int chunkZ, Chunks chunks) {
        for (int x = chunkX - 1; x <= chunkX + 1; x++) {
            for (int z = chunkZ - 1; z <= chunkZ + 1; z++) {
                if (!chunks.isGenerated(x, z)) return false;
            }
        }
        return true;
    }
}
