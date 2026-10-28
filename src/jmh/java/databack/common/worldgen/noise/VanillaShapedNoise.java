package databack.common.worldgen.noise;

/// Evaluates an existing [NormalNoise] the way vanilla's code is laid out (`int[][]` gradients, `byte` permutation
/// table masked on every lookup, octave loop with null checks), as a baseline for the optimized sampler.
final class VanillaShapedNoise {

    private static final int[][] GRADIENT = {
        { 1, 1, 0 }, { -1, 1, 0 }, { 1, -1, 0 }, { -1, -1, 0 }, { 1, 0, 1 }, { -1, 0, 1 }, { 1, 0, -1 }, { -1, 0, -1 },
        { 0, 1, 1 }, { 0, -1, 1 }, { 0, 1, -1 }, { 0, -1, -1 }, { 1, 1, 0 }, { 0, -1, 1 }, { -1, 1, 0 }, { 0, -1, -1 } };

    private final NormalNoise noise;

    VanillaShapedNoise(NormalNoise noise) {
        this.noise = noise;
    }

    double getValue(double x, double y, double z) {
        double x2 = x * 1.0181268882175227, y2 = y * 1.0181268882175227, z2 = z * 1.0181268882175227;
        return (perlin(noise.first, x, y, z) + perlin(noise.second, x2, y2, z2)) * noise.valueFactor;
    }

    private static double perlin(PerlinNoise p, double x, double y, double z) {
        double value = 0.0;
        double factor = p.lowestFreqInputFactor;
        double valueFactor = p.lowestFreqValueFactor;
        for (int i = 0; i < p.noiseLevels.length; i++) {
            ImprovedNoise n = p.noiseLevels[i];
            if (n != null) {
                double v = improved(n, PerlinNoise.wrap(x * factor), PerlinNoise.wrap(y * factor), PerlinNoise.wrap(z * factor), 0, 0);
                value += p.amplitudes[i] * v * valueFactor;
            }
            factor *= 2.0;
            valueFactor /= 2.0;
        }
        return value;
    }

    private static int p(ImprovedNoise n, int x) {
        return n.p[x & 255] & 255;
    }

    private static double gradDot(int hash, double x, double y, double z) {
        int[] g = GRADIENT[hash & 15];
        return (double) g[0] * x + (double) g[1] * y + (double) g[2] * z;
    }

    private static double improved(ImprovedNoise n, double _x, double _y, double _z, double yScale, double yFudge) {
        double x = _x + n.xo, y = _y + n.yo, z = _z + n.zo;
        int xf = (int) Math.floor(x), yf = (int) Math.floor(y), zf = (int) Math.floor(z);
        double xr = x - xf, yr = y - yf, zr = z - zf;
        double yrFudge;
        if (yScale != 0.0) {
            double fudgeLimit = yFudge >= 0.0 && yFudge < yr ? yFudge : yr;
            yrFudge = Math.floor(fudgeLimit / yScale + 1.0E-7F) * yScale;
        } else {
            yrFudge = 0.0;
        }
        double yrOriginal = yr;
        yr -= yrFudge;
        int x0 = p(n, xf), x1 = p(n, xf + 1);
        int xy00 = p(n, x0 + yf), xy01 = p(n, x0 + yf + 1), xy10 = p(n, x1 + yf), xy11 = p(n, x1 + yf + 1);
        double d000 = gradDot(p(n, xy00 + zf), xr, yr, zr);
        double d100 = gradDot(p(n, xy10 + zf), xr - 1.0, yr, zr);
        double d010 = gradDot(p(n, xy01 + zf), xr, yr - 1.0, zr);
        double d110 = gradDot(p(n, xy11 + zf), xr - 1.0, yr - 1.0, zr);
        double d001 = gradDot(p(n, xy00 + zf + 1), xr, yr, zr - 1.0);
        double d101 = gradDot(p(n, xy10 + zf + 1), xr - 1.0, yr, zr - 1.0);
        double d011 = gradDot(p(n, xy01 + zf + 1), xr, yr - 1.0, zr - 1.0);
        double d111 = gradDot(p(n, xy11 + zf + 1), xr - 1.0, yr - 1.0, zr - 1.0);
        double xa = smooth(xr), ya = smooth(yrOriginal), za = smooth(zr);
        return lerp(za, lerp(ya, lerp(xa, d000, d100), lerp(xa, d010, d110)), lerp(ya, lerp(xa, d001, d101), lerp(xa, d011, d111)));
    }

    private static double smooth(double x) {
        return x * x * x * (x * (x * 6.0 - 15.0) + 10.0);
    }

    private static double lerp(double t, double a, double b) {
        return a + t * (b - a);
    }
}
