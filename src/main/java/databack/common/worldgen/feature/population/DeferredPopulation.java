package databack.common.worldgen.feature.population;

import java.util.ArrayDeque;

import org.jetbrains.annotations.NotNull;

import it.unimi.dsi.fastutil.longs.LongSet;

/// Decorates each generated chunk once all 8 of its neighbours are generated, as modern does: features may write
/// anywhere in that 3×3.
///
/// 1.7 populates a 2×2 window of chunks as soon as it is loaded; [#onWindowComplete] is that signal. The last chunk
/// of any 3×3 to be generated completes such a window with chunks of that 3×3, so checking the chunks around each
/// completed window finds every chunk ready to decorate. Server thread only.
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

    /// The generated chunks not yet decorated, as [#key]s; persisted by the caller.
    @NotNull
    private final LongSet pending;
    /// Called whenever [#pending] changes.
    @NotNull
    private final Runnable changed;
    @NotNull
    private final ArrayDeque<long[]> queue = new ArrayDeque<>();
    private boolean draining;

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

    /// Decorates every pending chunk near the window of chunks `chunkX..chunkX + 1` by `chunkZ..chunkZ + 1` whose 3×3
    /// is generated. Decorating may load chunks and so complete more windows; those calls are queued and handled
    /// after the current one, never nested.
    public void onWindowComplete(int chunkX, int chunkZ, @NotNull Chunks chunks, @NotNull Decorator decorator) {
        queue.add(new long[] { chunkX, chunkZ });
        if (draining) return;

        draining = true;
        try {
            while (!queue.isEmpty()) {
                long[] window = queue.poll();
                decorateAround((int) window[0], (int) window[1], chunks, decorator);
            }
        } finally {
            draining = false;
        }
    }

    private void decorateAround(int windowX, int windowZ, Chunks chunks, Decorator decorator) {
        for (int x = windowX - 1; x <= windowX + 2; x++) {
            for (int z = windowZ - 1; z <= windowZ + 2; z++) {
                if (!isPending(x, z) || !isSurrounded(x, z, chunks)) continue;

                // Removed first: a failing decoration is not retried forever.
                pending.remove(key(x, z));
                changed.run();
                decorator.decorate(x, z);
            }
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
