package databack.common.noise;

import java.util.Random;

import net.minecraft.util.MathHelper;

/// A standard simplex noise sampler.
public class SimplexSampler implements NoiseSampler {

    protected static final int[][] GRADIENTS = new int[][] { { 1, 1, 0 }, { -1, 1, 0 }, { 1, -1, 0 }, { -1, -1, 0 },
        { 1, 0, 1 }, { -1, 0, 1 }, { 1, 0, -1 }, { -1, 0, -1 }, { 0, 1, 1 }, { 0, -1, 1 }, { 0, 1, -1 }, { 0, -1, -1 },
        { 1, 1, 0 }, { 0, -1, 1 }, { -1, 1, 0 }, { 0, -1, -1 } };
    private static final float SQRT_3 = (float) Math.sqrt(3.0F);
    private static final float SKEW_FACTOR_2D;
    private static final float UNSKEW_FACTOR_2D;
    private final int[] permutations = new int[512];
    public final float originX;
    public final float originY;
    public final float originZ;

    public SimplexSampler(Random random) {
        this.originX = random.nextFloat() * 256.0F;
        this.originY = random.nextFloat() * 256.0F;
        this.originZ = random.nextFloat() * 256.0F;

        for (int i = 0; i < 256; i++) {
            this.permutations[i] = i;
        }

        for (int i = 0; i < 256; ++i) {
            int k = random.nextInt(256 - i);
            int l = this.permutations[i];
            this.permutations[i] = this.permutations[k + i];
            this.permutations[k + i] = l;
        }
    }

    private int getGradient(int hash) {
        return this.permutations[hash & 255];
    }

    protected static float dot(int[] gArr, float x, float y, float z) {
        return (float) gArr[0] * x + (float) gArr[1] * y + (float) gArr[2] * z;
    }

    private float grad(int hash, float x, float y, float z, float distance) {
        float d = distance - x * x - y * y - z * z;
        float f;
        if (d < 0.0F) {
            f = 0.0F;
        } else {
            d *= d;
            f = d * d * dot(GRADIENTS[hash], x, y, z);
        }

        return f;
    }

    @Override
    public float sample(float x, float y) {
        float d = (x + y) * SKEW_FACTOR_2D;
        int i = MathHelper.floor_double(x + d);
        int j = MathHelper.floor_double(y + d);
        float e = (float) (i + j) * UNSKEW_FACTOR_2D;
        float f = (float) i - e;
        float g = (float) j - e;
        float h = x - f;
        float k = y - g;
        byte n;
        byte o;
        if (h > k) {
            n = 1;
            o = 0;
        } else {
            n = 0;
            o = 1;
        }

        float p = h - (float) n + UNSKEW_FACTOR_2D;
        float q = k - (float) o + UNSKEW_FACTOR_2D;
        float r = h - 1.0F + 2.0F * UNSKEW_FACTOR_2D;
        float s = k - 1.0F + 2.0F * UNSKEW_FACTOR_2D;
        int t = i & 255;
        int u = j & 255;
        int v = this.getGradient(t + this.getGradient(u)) % 12;
        int w = this.getGradient(t + n + this.getGradient(u + o)) % 12;
        int z = this.getGradient(t + 1 + this.getGradient(u + 1)) % 12;
        float aa = this.grad(v, h, k, 0.0F, 0.5F);
        float ab = this.grad(w, p, q, 0.0F, 0.5F);
        float ac = this.grad(z, r, s, 0.0F, 0.5F);
        return 70.0F * (aa + ab + ac);
    }

    @Override
    public float sample(float x, float y, float z) {
        float e = (x + y + z) * 0.3333333333333333F;
        int i = MathHelper.floor_double(x + e);
        int j = MathHelper.floor_double(y + e);
        int k = MathHelper.floor_double(z + e);
        float g = (float) (i + j + k) * 0.16666666666666666F;
        float h = (float) i - g;
        float l = (float) j - g;
        float m = (float) k - g;
        float n = x - h;
        float o = y - l;
        float p = z - m;
        byte w;
        byte aa;
        byte ab;
        byte ac;
        byte ad;
        byte bc;
        if (n >= o) {
            if (o >= p) {
                w = 1;
                aa = 0;
                ab = 0;
                ac = 1;
                ad = 1;
                bc = 0;
            } else if (n >= p) {
                w = 1;
                aa = 0;
                ab = 0;
                ac = 1;
                ad = 0;
                bc = 1;
            } else {
                w = 0;
                aa = 0;
                ab = 1;
                ac = 1;
                ad = 0;
                bc = 1;
            }
        } else if (o < p) {
            w = 0;
            aa = 0;
            ab = 1;
            ac = 0;
            ad = 1;
            bc = 1;
        } else if (n < p) {
            w = 0;
            aa = 1;
            ab = 0;
            ac = 0;
            ad = 1;
            bc = 1;
        } else {
            w = 0;
            aa = 1;
            ab = 0;
            ac = 1;
            ad = 1;
            bc = 0;
        }

        float bd = n - (float) w + 0.16666666666666666F;
        float be = o - (float) aa + 0.16666666666666666F;
        float bf = p - (float) ab + 0.16666666666666666F;
        float bg = n - (float) ac + 0.3333333333333333F;
        float bh = o - (float) ad + 0.3333333333333333F;
        float bi = p - (float) bc + 0.3333333333333333F;
        float bj = n - 1.0F + 0.5F;
        float bk = o - 1.0F + 0.5F;
        float bl = p - 1.0F + 0.5F;
        int bm = i & 255;
        int bn = j & 255;
        int bo = k & 255;
        int bp = this.getGradient(bm + this.getGradient(bn + this.getGradient(bo))) % 12;
        int bq = this.getGradient(bm + w + this.getGradient(bn + aa + this.getGradient(bo + ab))) % 12;
        int br = this.getGradient(bm + ac + this.getGradient(bn + ad + this.getGradient(bo + bc))) % 12;
        int bs = this.getGradient(bm + 1 + this.getGradient(bn + 1 + this.getGradient(bo + 1))) % 12;
        float bt = this.grad(bp, n, o, p, 0.6F);
        float bu = this.grad(bq, bd, be, bf, 0.6F);
        float bv = this.grad(br, bg, bh, bi, 0.6F);
        float bw = this.grad(bs, bj, bk, bl, 0.6F);
        return 32.0F * (bt + bu + bv + bw);
    }

    static {
        SKEW_FACTOR_2D = 0.5F * (SQRT_3 - 1.0F);
        UNSKEW_FACTOR_2D = (3.0F - SQRT_3) / 6.0F;
    }
}
