package databack.common.worldgen.density;

/// The arithmetic primitives of the spec (section 1). Every formula is written in evaluation order; callers must not
/// rearrange them, since rounding depends on the order.
public final class DensityMath {

    private DensityMath() {}

    public static double lerp(double t, double a, double b) {
        return a + t * (b - a);
    }

    public static float lerp(float t, float a, float b) {
        return a + t * (b - a);
    }

    /// `lo` below the range, otherwise `min(v, hi)`; NaN stays NaN.
    public static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : Math.min(v, hi);
    }

    public static double clampedLerp(double t, double a, double b) {
        if (t < 0.0) return a;
        if (t > 1.0) return b;
        return lerp(t, a, b);
    }

    public static double clampedMap(double v, double fromLo, double fromHi, double toLo, double toHi) {
        return clampedLerp((v - fromLo) / (fromHi - fromLo), toLo, toHi);
    }

    public static int floor(double v) {
        return (int) Math.floor(v);
    }

    /// Trilinear interpolation, interpolating along x first, then y, then z. This is the order used for every read of
    /// an interpolator during a cell fill (spec 5.3).
    public static double trilinearXYZ(
        double fx, double fy, double fz, double c000, double c100, double c010, double c110, double c001, double c101,
        double c011, double c111
    ) {
        return lerp(
            fz,
            lerp(fy, lerp(fx, c000, c100), lerp(fx, c010, c110)),
            lerp(fy, lerp(fx, c001, c101), lerp(fx, c011, c111)));
    }

    /// Trilinear interpolation, interpolating along y first, then x, then z: an interpolator's per-block value
    /// (spec 5.3). Rounds differently from [#trilinearXYZ].
    public static double trilinearYXZ(
        double fx, double fy, double fz, double c000, double c100, double c010, double c110, double c001, double c101,
        double c011, double c111
    ) {
        return lerp(
            fz,
            lerp(fx, lerp(fy, c000, c010), lerp(fy, c100, c110)),
            lerp(fx, lerp(fy, c001, c011), lerp(fy, c101, c111)));
    }

    /// Bitwise equality, so that NaN equals NaN and 0.0 differs from -0.0.
    static boolean same(double a, double b) {
        return Double.doubleToLongBits(a) == Double.doubleToLongBits(b);
    }
}
