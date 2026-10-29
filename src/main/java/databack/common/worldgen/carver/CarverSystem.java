package databack.common.worldgen.carver;

import java.util.List;

import org.jetbrains.annotations.NotNull;

import databack.common.annotation.ThreadSafe;
import databack.common.worldgen.carver.standin.CvAquifer;
import databack.common.worldgen.carver.standin.CvCarvingMask;
import databack.common.worldgen.carver.standin.CvChunk;
import databack.common.worldgen.carver.standin.CvChunkPos;
import databack.common.worldgen.carver.standin.CvContext;
import databack.common.worldgen.carver.standin.CvRandom;
import databack.common.worldgen.rng.SourceBits;
import databack.common.worldgen.rng.StandardRandom;

/// The carver step (docs/worldgen/carvers.md): every carving that starts within [#RANGE] chunks of a chunk carves its
/// part of it, with vanilla's carvers ([VanillaCarvers]). Thread-safe: each call has its own state.
@ThreadSafe
public final class CarverSystem {

    /// How far from a chunk, in chunks, carvings that reach it may start.
    static final int RANGE = 8;

    /// The carvers of the biome a carving starting in a chunk uses.
    @FunctionalInterface
    public interface StartCarvers {

        @NotNull
        List<ConfiguredCarver> at(int chunkX, int chunkZ);
    }

    private final long seed;
    @NotNull
    private final StartCarvers carvers;

    public CarverSystem(long seed, @NotNull StartCarvers carvers) {
        this.seed = seed;
        this.carvers = carvers;
    }

    /// Carves `chunk` in place.
    public void carve(@NotNull CvChunk chunk, @NotNull CvContext context, @NotNull CvAquifer aquifer) {
        CvChunkPos pos = chunk.getPos();
        CvCarvingMask mask = new CvCarvingMask();
        CvRandom random = new CvRandom(new SourceBits(new StandardRandom(0)));

        for (int dx = -RANGE; dx <= RANGE; dx++) {
            for (int dz = -RANGE; dz <= RANGE; dz++) {
                int startX = pos.x + dx, startZ = pos.z + dz;
                CvChunkPos start = new CvChunkPos(startX, startZ);
                List<ConfiguredCarver> starting = carvers.at(startX, startZ);

                for (int index = 0; index < starting.size(); index++) {
                    ConfiguredCarver carver = starting.get(index);
                    CarverSeeds.largeFeatureSeed(random.source(), seed + index, startX, startZ);
                    if (carver.carver.isStartChunk(carver.config, random)) {
                        carver.carver.carve(context, carver.config, chunk, null, random, aquifer, start, mask);
                    }
                }
            }
        }
    }
}
