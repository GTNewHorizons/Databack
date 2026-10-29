package databack.common.worldgen.carver.standin;

import org.jetbrains.annotations.ApiStatus;

/// Stand-in for the carvers' debug settings and switches: debugging is always off.
@ApiStatus.Internal
public final class CvDebug {

    public static final CvDebug DEFAULT = new CvDebug();

    private CvDebug() {}

    public boolean isDebugMode() {
        return false;
    }

    /// Unreachable: debugging is off.
    public CvBlockState getAirState() {
        return CvBlocks.AIR.defaultBlockState();
    }

    /// Unreachable: debugging is off.
    public CvBlockState getWaterState() {
        return CvBlocks.WATER.defaultBlockState();
    }

    /// Unreachable: debugging is off.
    public CvBlockState getLavaState() {
        return CvBlocks.LAVA.defaultBlockState();
    }

    /// Unreachable: debugging is off.
    public CvBlockState getBarrierState() {
        return CvBlocks.AIR.defaultBlockState();
    }

    public static boolean debugVoidTerrain(CvChunkPos pos) {
        return false;
    }
}
