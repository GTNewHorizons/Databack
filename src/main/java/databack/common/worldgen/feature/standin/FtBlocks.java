package databack.common.worldgen.feature.standin;

import org.jetbrains.annotations.ApiStatus;

/// Stand-in for the block constants the feature classes name.
@ApiStatus.Internal
public final class FtBlocks {

    public static final FtBlock BEDROCK = FtBlock.of("minecraft:bedrock");
    public static final FtBlock WATER = FtBlock.of("minecraft:water");
    public static final FtBlock LAVA = FtBlock.of("minecraft:lava");

    private FtBlocks() {}
}
