package databack.common.worldgen.aquifer.standin;

import org.jetbrains.annotations.ApiStatus;

import com.gtnewhorizon.gtnhlib.util.CoordinatePacker;

/// Stand-ins for the math, section and block position helpers the aquifer uses. Behaviour observed black-box.
@ApiStatus.Internal
public final class AqMath {

    private AqMath() {}

    public static double clamp(double value, double min, double max) {
        return value < min ? min : Math.min(value, max);
    }

    public static double map(double value, double fromMin, double fromMax, double toMin, double toMax) {
        return toMin + (value - fromMin) / (fromMax - fromMin) * (toMax - toMin);
    }

    public static double clampedMap(double value, double fromMin, double fromMax, double toMin, double toMax) {
        double t = (value - fromMin) / (fromMax - fromMin);
        return t < 0 ? toMin : t > 1 ? toMax : toMin + t * (toMax - toMin);
    }

    public static int quantize(double value, int step) {
        return (int) Math.floor(value / step) * step;
    }

    public static int square(int value) {
        return value * value;
    }

    public static int sectionToBlockCoord(int section) {
        return section << 4;
    }

    /// Block position packing; the aquifer only needs it to round-trip. GTNHLib's packer, whose layout Cubic Chunks
    /// widens for its Y range.
    public static long asLong(int x, int y, int z) {
        return CoordinatePacker.pack(x, y, z);
    }

    public static int getX(long packed) {
        return CoordinatePacker.unpackX(packed);
    }

    public static int getY(long packed) {
        return CoordinatePacker.unpackY(packed);
    }

    public static int getZ(long packed) {
        return CoordinatePacker.unpackZ(packed);
    }

    /// Deep dark caves stay dry: erosion below -0.225 and depth above 0.9.
    public static boolean isDeepDarkRegion(AqFunction erosion, AqFunction depth, AqContext ctx) {
        return erosion.compute(ctx) < -0.225 && depth.compute(ctx) > 0.9;
    }
}
