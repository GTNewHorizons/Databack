package databack.common.worldgen.aquifer.standin;

import org.jetbrains.annotations.ApiStatus;

/// Stand-in for the block constants the aquifer uses.
@ApiStatus.Internal
public final class AqBlocks {

    public static final AqBlock AIR = new AqBlock();
    public static final AqBlock WATER = new AqBlock();
    public static final AqBlock LAVA = new AqBlock();

    private AqBlocks() {}
}
