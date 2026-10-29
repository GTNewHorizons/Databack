package databack.common.worldgen.vanilla.standin;

import java.util.Collections;
import java.util.List;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import databack.common.worldgen.carver.standin.CvMath;
import databack.common.worldgen.rng.RandomSource;

/// Stand-in for vanilla's math helpers (`Mth`, `SectionPos`, `Util`). Behaviour observed black-box
/// (`carver/probes/mth.tsv`, `feature/probes/mth.tsv`, `section.tsv`, `util_random.tsv`).
@ApiStatus.Internal
public final class VnMath {

    private VnMath() {}

    public static float sin(double angle) {
        return CvMath.sin(angle);
    }

    public static float cos(double angle) {
        return CvMath.cos(angle);
    }

    public static int floor(double value) {
        return (int) Math.floor(value);
    }

    public static int ceil(float value) {
        return (int) Math.ceil(value);
    }

    public static int ceil(double value) {
        return (int) Math.ceil(value);
    }

    /// `value` limited to `[min, max]`; `min` wins if they cross.
    public static double clamp(double value, double min, double max) {
        return value < min ? min : Math.min(value, max);
    }

    public static int clamp(int value, int min, int max) {
        return Math.min(Math.max(value, min), max);
    }

    public static double lerp(double t, double from, double to) {
        return from + t * (to - from);
    }

    /// `value` mapped linearly from `[fromMin, fromMax]` to `[toMin, toMax]`, limited to the latter.
    public static double clampedMap(double value, double fromMin, double fromMax, double toMin, double toMax) {
        return clampedLerp((value - fromMin) / (fromMax - fromMin), toMin, toMax);
    }

    private static double clampedLerp(double t, double from, double to) {
        if (t < 0) return from;
        if (t > 1) return to;
        return lerp(t, from, to);
    }

    public static int blockToSectionCoord(int block) {
        return block >> 4;
    }

    public static int sectionToBlockCoord(int section) {
        return section << 4;
    }

    /// The position within its 16-block section.
    public static int sectionRelative(int block) {
        return block & 15;
    }

    /// One bounded draw over the list's indices.
    public static <T> T getRandom(@NotNull List<T> list, @NotNull RandomSource random) {
        return list.get(random.nextInt(list.size()));
    }

    public static int abs(int value) {
        return Math.abs(value);
    }

    public static int floor(float value) {
        return (int) Math.floor(value);
    }

    public static float sqrt(float value) {
        return (float) Math.sqrt(value);
    }

    public static float square(float value) {
        return value * value;
    }

    /// Shuffles `list` in place: from the last position down, each swaps with a position drawn at or before it
    /// (`feature/probes/shuffle.tsv`).
    public static <T> void shuffle(@NotNull List<T> list, @NotNull RandomSource random) {
        for (int i = list.size(); i > 1; i--) Collections.swap(list, i - 1, random.nextInt(i));
    }

    /// A shuffled copy of `list` ([#shuffle]); `list` is unchanged.
    @NotNull
    public static <T> List<T> shuffledCopy(@NotNull ObjectArrayList<T> list, @NotNull RandomSource random) {
        List<T> copy = new ObjectArrayList<>(list);
        shuffle(copy, random);
        return copy;
    }
}
