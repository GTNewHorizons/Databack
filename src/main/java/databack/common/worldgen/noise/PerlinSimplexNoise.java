package databack.common.worldgen.noise;

import java.util.Collection;
import java.util.Collections;

import org.jetbrains.annotations.NotNull;

import databack.common.worldgen.rng.RandomSource;

/// Simplex octaves summed in two dimensions, all drawn from one random (`feature/probes/simplex_octaves.tsv`).
///
/// Octave `o` samples at `2^o`. The lowest listed octave weighs `2^(n-1) / (2^n - 1)`, where `n` spans the lowest to
/// the highest listed octave, and each octave above it weighs half the one below. Octaves are drawn from 0 down;
/// unlisted ones skip their draws. Only octaves at or below 0 are supported.
public final class PerlinSimplexNoise {

    /// The ints one simplex noise draws.
    private static final int DRAWS_PER_OCTAVE = 262;

    /// By octave, from the lowest; null where an octave isn't listed.
    @NotNull
    private final SimplexNoise[] noises;
    private final int lowest;
    private final double lowestWeight;

    /// Throws [IllegalArgumentException] for no octaves or one above 0.
    public PerlinSimplexNoise(@NotNull RandomSource random, @NotNull Collection<Integer> octaves) {
        if (octaves.isEmpty()) throw new IllegalArgumentException("No octaves");
        int highest = Collections.max(octaves);
        if (highest > 0) throw new IllegalArgumentException("Octaves above 0 are not supported: " + octaves);

        this.lowest = Collections.min(octaves);
        this.noises = new SimplexNoise[highest - lowest + 1];

        for (int octave = 0; octave >= lowest; octave--) {
            if (octaves.contains(octave)) {
                noises[octave - lowest] = new SimplexNoise(random);
            } else {
                for (int i = 0; i < DRAWS_PER_OCTAVE; i++) random.nextInt();
            }
        }

        int span = noises.length;
        this.lowestWeight = Math.pow(2, span - 1) / (Math.pow(2, span) - 1);
    }

    public double getValue(double x, double z) {
        double value = 0;
        double weight = lowestWeight;
        double frequency = Math.pow(2, lowest);

        for (SimplexNoise noise : noises) {
            if (noise != null) value += noise.getValue(x * frequency, z * frequency) * weight;
            weight /= 2;
            frequency *= 2;
        }
        return value;
    }
}
