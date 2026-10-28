package databack.common.worldgen.noise;

import databack.common.worldgen.rng.RandomSource;

/**
 * Port of net.minecraft.world.level.levelgen.synth.ImprovedNoise (legacy sampling path only).
 * Gradient table sourced from SimplexNoise.GRADIENT.
 * <p>
 * The arithmetic is kept in exactly the same order as vanilla so results are bit-identical. The speedups come from
 * the data layout only: the permutation table is widened to ints (no {@code & 0xFF} per lookup) and the gradient
 * table is split into three flat double arrays (no {@code int[][]} pointer chase or int-to-double conversion).
 */
public final class ImprovedNoise {

    private static final int[][] GRADIENT = {
        { 1,  1,  0}, {-1,  1,  0}, { 1, -1,  0}, {-1, -1,  0},
        { 1,  0,  1}, {-1,  0,  1}, { 1,  0, -1}, {-1,  0, -1},
        { 0,  1,  1}, { 0, -1,  1}, { 0,  1, -1}, { 0, -1, -1},
        { 1,  1,  0}, { 0, -1,  1}, {-1,  1,  0}, { 0, -1, -1}
    };

    private static final double[] GX = new double[16], GY = new double[16], GZ = new double[16];

    static {
        for (int i = 0; i < 16; i++) {
            GX[i] = GRADIENT[i][0];
            GY[i] = GRADIENT[i][1];
            GZ[i] = GRADIENT[i][2];
        }
    }

    final byte[] p;
    /** {@link #p} widened to unsigned ints. */
    private final int[] perm;
    public final double xo;
    public final double yo;
    public final double zo;

    public ImprovedNoise(RandomSource random) {
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
        this.perm = new int[256];
        for (int i = 0; i < 256; i++) {
            this.perm[i] = this.p[i] & 0xFF;
        }
    }

    /** Equivalent to {@code noise(x, y, z, 0, 0)}, minus the y-fudge branch. */
    public double noise(double _x, double _y, double _z) {
        double x = _x + this.xo;
        double y = _y + this.yo;
        double z = _z + this.zo;
        int xf = floor(x);
        int yf = floor(y);
        int zf = floor(z);
        double xr = x - xf;
        double yr = y - yf;
        double zr = z - zf;
        return sampleAndLerp(xf, yf, zf, xr, yr, zr, yr);
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
            yrFudge = (double) floor(fudgeLimit / yScale + (double) 1.0E-7F) * yScale;
        } else {
            yrFudge = 0.0;
        }
        return sampleAndLerp(xf, yf, zf, xr, yr - yrFudge, zr, yr);
    }

    private static double gradDot(int hash, double x, double y, double z) {
        int h = hash & 0xF;
        return GX[h] * x + GY[h] * y + GZ[h] * z;
    }

    private double sampleAndLerp(int x, int y, int z, double xr, double yr, double zr, double yrOriginal) {
        final int[] perm = this.perm;
        int x0 = perm[x & 0xFF];
        int x1 = perm[(x + 1) & 0xFF];
        int xy00 = perm[(x0 + y) & 0xFF];
        int xy01 = perm[(x0 + y + 1) & 0xFF];
        int xy10 = perm[(x1 + y) & 0xFF];
        int xy11 = perm[(x1 + y + 1) & 0xFF];
        double d000 = gradDot(perm[(xy00 + z) & 0xFF],     xr,        yr,        zr);
        double d100 = gradDot(perm[(xy10 + z) & 0xFF],     xr - 1.0,  yr,        zr);
        double d010 = gradDot(perm[(xy01 + z) & 0xFF],     xr,        yr - 1.0,  zr);
        double d110 = gradDot(perm[(xy11 + z) & 0xFF],     xr - 1.0,  yr - 1.0,  zr);
        double d001 = gradDot(perm[(xy00 + z + 1) & 0xFF], xr,        yr,        zr - 1.0);
        double d101 = gradDot(perm[(xy10 + z + 1) & 0xFF], xr - 1.0,  yr,        zr - 1.0);
        double d011 = gradDot(perm[(xy01 + z + 1) & 0xFF], xr,        yr - 1.0,  zr - 1.0);
        double d111 = gradDot(perm[(xy11 + z + 1) & 0xFF], xr - 1.0,  yr - 1.0,  zr - 1.0);
        double xA = smoothstep(xr);
        double yA = smoothstep(yrOriginal);
        double zA = smoothstep(zr);
        return lerp3(xA, yA, zA, d000, d100, d010, d110, d001, d101, d011, d111);
    }

    /** Matches vanilla's {@code Mth.floor(double)}. */
    static int floor(double x) {
        return (int) Math.floor(x);
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
