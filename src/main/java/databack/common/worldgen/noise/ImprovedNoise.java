package databack.common.worldgen.noise;

import java.util.Random;

/**
 * Port of net.minecraft.world.level.levelgen.synth.ImprovedNoise (legacy sampling path only).
 * Gradient table sourced from SimplexNoise.GRADIENT.
 */
public final class ImprovedNoise {

    private static final int[][] GRADIENT = {
        { 1,  1,  0}, {-1,  1,  0}, { 1, -1,  0}, {-1, -1,  0},
        { 1,  0,  1}, {-1,  0,  1}, { 1,  0, -1}, {-1,  0, -1},
        { 0,  1,  1}, { 0, -1,  1}, { 0,  1, -1}, { 0, -1, -1},
        { 1,  1,  0}, { 0, -1,  1}, {-1,  1,  0}, { 0, -1, -1}
    };

    final byte[] p;
    public final double xo;
    public final double yo;
    public final double zo;

    public ImprovedNoise(Random random) {
        this.xo = random.nextDouble() * 256.0;
        this.yo = random.nextDouble() * 256.0;
        this.zo = random.nextDouble() * 256.0;
        this.p = new byte[256];
        for (int i = 0; i < 256; i++) {
            this.p[i] = (byte) i;
        }
        for (int i = 0; i < 256; i++) {
            int offset = random.nextInt(256 - i);
            byte tmp = this.p[i];
            this.p[i] = this.p[i + offset];
            this.p[i + offset] = tmp;
        }
    }

    public double noise(double _x, double _y, double _z, double yScale, double yFudge) {
        double x = _x + this.xo;
        double y = _y + this.yo;
        double z = _z + this.zo;
        int xf = floor(x);
        int yf = floor(y);
        int zf = floor(z);
        double xr = x - xf;
        double yr = y - yf;
        double zr = z - zf;
        double yrFudge;
        if (yScale != 0.0) {
            double fudgeLimit = (yFudge >= 0.0 && yFudge < yr) ? yFudge : yr;
            yrFudge = (double) floor(fudgeLimit / yScale + 1.0E-7) * yScale;
        } else {
            yrFudge = 0.0;
        }
        return sampleAndLerp(xf, yf, zf, xr, yr - yrFudge, zr, yr);
    }

    private int p(int x) {
        return this.p[x & 0xFF] & 0xFF;
    }

    private static double gradDot(int hash, double x, double y, double z) {
        int[] g = GRADIENT[hash & 0xF];
        return g[0] * x + g[1] * y + g[2] * z;
    }

    private double sampleAndLerp(int x, int y, int z, double xr, double yr, double zr, double yrOriginal) {
        int x0  = p(x);
        int x1  = p(x + 1);
        int xy00 = p(x0 + y);
        int xy01 = p(x0 + y + 1);
        int xy10 = p(x1 + y);
        int xy11 = p(x1 + y + 1);
        double d000 = gradDot(p(xy00 + z),     xr,        yr,        zr);
        double d100 = gradDot(p(xy10 + z),     xr - 1.0,  yr,        zr);
        double d010 = gradDot(p(xy01 + z),     xr,        yr - 1.0,  zr);
        double d110 = gradDot(p(xy11 + z),     xr - 1.0,  yr - 1.0,  zr);
        double d001 = gradDot(p(xy00 + z + 1), xr,        yr,        zr - 1.0);
        double d101 = gradDot(p(xy10 + z + 1), xr - 1.0,  yr,        zr - 1.0);
        double d011 = gradDot(p(xy01 + z + 1), xr,        yr - 1.0,  zr - 1.0);
        double d111 = gradDot(p(xy11 + z + 1), xr - 1.0,  yr - 1.0,  zr - 1.0);
        double xA = smoothstep(xr);
        double yA = smoothstep(yrOriginal);
        double zA = smoothstep(zr);
        return lerp3(xA, yA, zA, d000, d100, d010, d110, d001, d101, d011, d111);
    }

    static int floor(double x) {
        int xi = (int) x;
        return x < xi ? xi - 1 : xi;
    }

    private static double smoothstep(double x) {
        return x * x * x * (x * (x * 6.0 - 15.0) + 10.0);
    }

    private static double lerp(double t, double a, double b) {
        return a + t * (b - a);
    }

    private static double lerp2(double t, double s, double v00, double v10, double v01, double v11) {
        return lerp(s, lerp(t, v00, v10), lerp(t, v01, v11));
    }

    private static double lerp3(double t, double s, double u,
        double v000, double v100, double v010, double v110,
        double v001, double v101, double v011, double v111) {
        return lerp(u, lerp2(t, s, v000, v100, v010, v110),
                       lerp2(t, s, v001, v101, v011, v111));
    }
}
