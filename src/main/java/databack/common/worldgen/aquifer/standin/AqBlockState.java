package databack.common.worldgen.aquifer.standin;

import org.jetbrains.annotations.ApiStatus;

/// Stand-in for a block state; one per [AqBlock].
@ApiStatus.Internal
public final class AqBlockState {

    private final AqBlock block;

    AqBlockState(AqBlock block) {
        this.block = block;
    }

    public boolean is(Object other) {
        return block == other;
    }

    public boolean isAir() {
        return block == AqBlocks.AIR;
    }
}
