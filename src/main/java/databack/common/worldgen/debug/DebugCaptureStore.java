package databack.common.worldgen.debug;

import databack.common.worldgen.GpuChunkCache;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Singleton store for {@link ChunkDebugCapture} snapshots.
 * <p>
 * Enable via the {@code -Ddataback.gpu.debug=true} JVM property, or at runtime via
 * {@link #setEnabled(boolean)}.
 * <p>
 * Thread safety: {@link #isEnabled()} reads a volatile field (cheap, no lock).
 * All mutating methods are synchronized.  The Swing UI reads captures via
 * {@link #getCaptures()} which returns a defensive snapshot.
 */
public final class DebugCaptureStore {

    private static final DebugCaptureStore INSTANCE = new DebugCaptureStore();

    public static DebugCaptureStore getInstance() {
        return INSTANCE;
    }

    private static final int MAX_CAPTURES = 20;

    /**
     * Volatile so {@code DFPlanBuilder.createPlan} can read it without acquiring a lock
     * on every chunk column.
     */
    private volatile boolean enabled = Boolean.getBoolean("databack.gpu.debug");

    private final Deque<ChunkDebugCapture> captures = new ArrayDeque<>(MAX_CAPTURES);

    /**
     * Registered by {@code ModernWorldGenerator} after it constructs {@link GpuChunkCache}.
     * May be null if GPU compute is disabled.
     */
    private GpuChunkCache cache;

    private DebugCaptureStore() {}

    // -------------------------------------------------------------------------
    // Enable / disable
    // -------------------------------------------------------------------------

    public boolean isEnabled() {
        return enabled;
    }

    public synchronized void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    // -------------------------------------------------------------------------
    // Cache registration
    // -------------------------------------------------------------------------

    /** Called by {@code ModernWorldGenerator} after constructing the {@link GpuChunkCache}. */
    public synchronized void registerCache(GpuChunkCache cache) {
        this.cache = cache;
    }

    // -------------------------------------------------------------------------
    // Capture storage
    // -------------------------------------------------------------------------

    /**
     * Stores a completed capture, evicting the oldest if the ring buffer is full.
     * Called from the server thread (inside {@code GpuChunkCache.submitBatchGpu}) after
     * {@code KernelScheduler.submit()} returns.
     */
    public synchronized void store(ChunkDebugCapture capture) {
        if (captures.size() >= MAX_CAPTURES) {
            captures.pollFirst();
        }
        captures.addLast(capture);
    }

    /**
     * Returns a snapshot of all stored captures, safe for EDT consumption.
     * Ordered oldest-first.
     */
    public synchronized List<ChunkDebugCapture> getCaptures() {
        return new ArrayList<>(captures);
    }

    public synchronized void clear() {
        captures.clear();
    }

    // -------------------------------------------------------------------------
    // Replay
    // -------------------------------------------------------------------------

    /**
     * Forces GPU re-evaluation of the given chunk column by bypassing the cache.
     * <p>
     * Must be called from a thread that is allowed to call {@link GpuChunkCache} (i.e.
     * the server thread, or a dedicated replay thread that is not concurrent with the
     * server thread). This is a debug tool; correctness over threading ergonomics.
     *
     * @throws IllegalStateException if no {@link GpuChunkCache} has been registered
     */
    public void replay(int chunkX, int chunkZ) {
        GpuChunkCache c;
        synchronized (this) {
            c = cache;
        }
        if (c == null) throw new IllegalStateException(
            "No GpuChunkCache registered — GPU compute may be disabled");
        c.forceRecompute(chunkX, chunkZ);
    }
}
