package databack.common.worldgen.density;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiPredicate;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/// Computes chunk densities on background threads ahead of time.
///
/// When the game asks for a chunk, its neighbours are usually next, so after each request the (not yet generated)
/// neighbours are queued on a worker pool. A later request for one of them then only has to wait for (or just take)
/// the finished result. Results are exactly what [ChunkDensitySampler#sample] returns on the calling thread.
///
/// The number of queued/finished chunks is bounded; the oldest are dropped first.
public final class DensityPrefetcher {

    private static final Logger LOGGER = LogManager.getLogger("Databack/DensityPrefetcher");

    /// Worker threads shared by every dimension. `-Ddataback.densityThreads=0` disables prefetching.
    public static final int THREADS = Integer.getInteger(
        "databack.densityThreads",
        Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors() / 2 - 1)));

    private static ExecutorService pool;

    private static synchronized ExecutorService pool() {
        if (pool == null) {
            AtomicInteger counter = new AtomicInteger();
            ThreadFactory factory = r -> {
                Thread t = new Thread(r, "Databack density worker #" + counter.incrementAndGet());
                t.setDaemon(true);
                t.setPriority(Thread.NORM_PRIORITY - 1);
                return t;
            };
            pool = Executors.newFixedThreadPool(THREADS, factory);
        }
        return pool;
    }

    private final ChunkDensitySampler sampler;
    private final int radius;
    private final int capacity;
    private final Map<Long, Future<double[]>> pending;

    /// @param radius   how far around a requested chunk to prefetch
    /// @param capacity how many chunks may be queued or waiting to be picked up; each holds `height * 256` doubles
    public DensityPrefetcher(ChunkDensitySampler sampler, int radius, int capacity) {
        this.sampler = sampler;
        this.radius = radius;
        this.capacity = capacity;
        this.pending = new LinkedHashMap<>(capacity * 2, 0.75f, false);
    }

    public ChunkDensitySampler sampler() {
        return sampler;
    }

    private static long key(int x, int z) {
        return (long) x & 0xFFFFFFFFL | ((long) z & 0xFFFFFFFFL) << 32;
    }

    /// Returns the densities for a chunk, taking a prefetched result if there is one, and queues its neighbours.
    ///
    /// @param needsGeneration filters neighbours that should be prefetched (e.g. skip ones that already exist)
    /// @return a new array the caller owns, laid out like [ChunkDensitySampler#sample]
    public double[] get(int chunkX, int chunkZ, BiPredicate<Integer, Integer> needsGeneration) {
        if (THREADS <= 0) {
            double[] out = sampler.newBuffer();
            sampler.sample(chunkX, chunkZ, out);
            return out;
        }

        Future<double[]> future;
        synchronized (pending) {
            future = pending.remove(key(chunkX, chunkZ));
        }

        double[] result = null;

        if (future != null) {
            try {
                result = future.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (ExecutionException | CancellationException e) {
                LOGGER.error("Prefetching chunk ({}, {}) failed; computing it again", chunkX, chunkZ, e);
            }
        }

        if (result == null) {
            result = sampler.newBuffer();
            sampler.sample(chunkX, chunkZ, result);
        }

        prefetchAround(chunkX, chunkZ, needsGeneration);

        return result;
    }

    private void prefetchAround(int chunkX, int chunkZ, BiPredicate<Integer, Integer> needsGeneration) {
        ExecutorService pool = pool();

        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                if (dx == 0 && dz == 0) continue;

                int x = chunkX + dx, z = chunkZ + dz;
                long key = key(x, z);

                synchronized (pending) {
                    if (pending.containsKey(key)) continue;
                }

                if (!needsGeneration.test(x, z)) continue;

                Future<double[]> future = pool.submit(() -> {
                    double[] out = sampler.newBuffer();
                    sampler.sample(x, z, out);
                    return out;
                });

                synchronized (pending) {
                    pending.put(key, future);

                    // Drop the oldest entries; they're the furthest from where generation is happening now.
                    Iterator<Future<double[]>> iter = pending.values().iterator();
                    while (pending.size() > capacity && iter.hasNext()) {
                        iter.next().cancel(false);
                        iter.remove();
                    }
                }
            }
        }
    }
}
