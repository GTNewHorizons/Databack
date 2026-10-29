package databack.common.worldgen.carver.standin;

import org.jetbrains.annotations.ApiStatus;

/// Stand-in for the block constants the carvers name. The fluid blocks are their fluids' ([CvFluids]).
@ApiStatus.Internal
public final class CvBlocks {

    public static final CvBlock AIR = new CvBlock("minecraft:air", null);
    public static final CvBlock CAVE_AIR = new CvBlock("minecraft:cave_air", null);
    public static final CvBlock DIRT = new CvBlock("minecraft:dirt", null);
    public static final CvBlock GRASS_BLOCK = new CvBlock("minecraft:grass_block", null);
    public static final CvBlock MYCELIUM = new CvBlock("minecraft:mycelium", null);
    public static final CvBlock WATER = CvFluids.WATER.defaultFluidState().createLegacyBlock().block;
    public static final CvBlock LAVA = CvFluids.LAVA.defaultFluidState().createLegacyBlock().block;

    private CvBlocks() {}
}
