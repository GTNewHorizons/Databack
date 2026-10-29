package databack.common.worldgen.carver.standin;

import java.util.function.Function;

import org.jetbrains.annotations.ApiStatus;

/// Added to vanilla's carvers when they are rewritten, so they can be called without reflection.
@ApiStatus.Internal
public interface CvCarver {

    /// The chunks around a start chunk its carvings can reach, in chunks.
    int getRange();

    boolean isStartChunk(CvCarverConfiguration config, CvRandom random);

    /// Carves the part of a carving started at `start` that lies in `chunk`. `biomes` may be null ([CvContext]).
    boolean carve(
        CvContext context, CvCarverConfiguration config, CvChunk chunk, Function<?, ?> biomes, CvRandom random,
        CvAquifer aquifer, CvChunkPos start, CvCarvingMask mask
    );
}
