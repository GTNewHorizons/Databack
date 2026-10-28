package databack.common.worldgen.aquifer.standin;

import org.jetbrains.annotations.ApiStatus;

/// Stand-in for the three blocks the aquifer places: [AqBlocks].
@ApiStatus.Internal
public final class AqBlock {

    private final AqBlockState state = new AqBlockState(this);

    AqBlock() {}

    public AqBlockState defaultBlockState() {
        return state;
    }
}
