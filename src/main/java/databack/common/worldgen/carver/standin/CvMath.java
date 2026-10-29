package databack.common.worldgen.carver.standin;

import org.jetbrains.annotations.ApiStatus;

/// Stand-ins for the math helpers the carvers use. Behaviour observed black-box (`carver/probes/mth.tsv`,
/// `providers.tsv`).
@ApiStatus.Internal
public final class CvMath {

    private static final int SIN_STEPS = 65536;
    private static final float[] SIN = new float[SIN_STEPS];

    static {
        for (int i = 0; i < SIN_STEPS; i++) SIN[i] = (float) Math.sin(i * Math.PI * 2 / SIN_STEPS);
    }

    private CvMath() {}

    /// A table lookup: the angle is quantized to 1/65536 of a turn (truncated towards zero, wrapping).
    public static float sin(double angle) {
        return SIN[(int) ((long) (angle * (SIN_STEPS / (Math.PI * 2))) & (SIN_STEPS - 1))];
    }

    /// [#sin] a quarter turn later.
    public static float cos(double angle) {
        return SIN[(int) ((long) (angle * (SIN_STEPS / (Math.PI * 2)) + SIN_STEPS / 4) & (SIN_STEPS - 1))];
    }

    public static int floor(double value) {
        return (int) Math.floor(value);
    }

    public static float abs(float value) {
        return Math.abs(value);
    }

    public static int sectionToBlockCoord(int section) {
        return section << 4;
    }

    /// One float draw scaled to `[min, max)`.
    public static float randomBetween(CvRandom random, float min, float max) {
        return min + random.nextFloat() * (max - min);
    }
}
