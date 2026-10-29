package databack.common.worldgen.feature.standin;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import databack.common.worldgen.vanilla.standin.VnBlockPos;

/// Stand-in for a chunk, as features use it: only to have blocks reshaped after generation, which 1.7 does not do.
@ApiStatus.Internal
public final class FtChunk {

    static final FtChunk INSTANCE = new FtChunk();

    private FtChunk() {}

    /// No-op: 1.7 blocks take their shape from their neighbours when drawn.
    public void markPosForPostprocessing(@NotNull VnBlockPos pos) {}
}
