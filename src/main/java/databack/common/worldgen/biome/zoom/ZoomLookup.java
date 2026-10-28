package databack.common.worldgen.biome.zoom;

import org.jetbrains.annotations.ApiStatus;

/// Added to vanilla's biome manager when it is rewritten, so its block lookup can be called without reflection. The
/// method matches the rewritten class's lookup exactly.
@ApiStatus.Internal
public interface ZoomLookup {

    /// The biome the zoom source returns for the quart cell this block reads.
    Object getBiome(ZoomPos pos);
}
