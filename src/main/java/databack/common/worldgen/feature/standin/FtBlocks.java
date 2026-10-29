package databack.common.worldgen.feature.standin;

import org.jetbrains.annotations.ApiStatus;

/// Stand-in for the block constants the feature classes name.
@ApiStatus.Internal
public final class FtBlocks {

    public static final FtBlock BEDROCK = FtBlock.of("minecraft:bedrock");
    public static final FtBlock WATER = FtBlock.of("minecraft:water");
    public static final FtBlock LAVA = FtBlock.of("minecraft:lava");
    public static final FtBlock DIRT = FtBlock.of("minecraft:dirt");
    public static final FtBlock VINE = FtBlock.of("minecraft:vine");
    public static final FtBlock COCOA = FtBlock.of("minecraft:cocoa");
    public static final FtBlock BEE_NEST = FtBlock.of("minecraft:bee_nest");
    public static final FtBlock CREAKING_HEART = FtBlock.of("minecraft:creaking_heart");
    public static final FtBlock PALE_HANGING_MOSS = FtBlock.of("minecraft:pale_hanging_moss");

    private FtBlocks() {}
}
