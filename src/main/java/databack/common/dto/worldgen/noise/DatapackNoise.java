package databack.common.dto.worldgen.noise;

import java.util.Random;

import com.gtnewhorizon.gtnhlib.noise.NoiseSampler;

import databack.common.worldgen.noise.NormalNoise;

public class DatapackNoise {

    public int firstOctave;
    public double[] amplitudes;

    /**
     * Creates a {@link NoiseSampler} backed by a {@link NormalNoise} for the given seed.
     * <p>
     * The seed should already incorporate the noise name (mixed in by
     * {@link databack.common.handlers.DatapackNoiseList#getSampler}) so that distinct
     * noise entries produce distinct samplers even for the same world seed.
     */
    public NoiseSampler createSampler(long seed) {
        NormalNoise noise = NormalNoise.create(
            new Random(seed),
            new NormalNoise.NoiseParameters(firstOctave, amplitudes)
        );
        return new NoiseSampler() {
            @Override
            public double sample(double x, double y) {
                return noise.getValue(x, 0.0, y);
            }

            @Override
            public double sample(double x, double y, double z) {
                return noise.getValue(x, y, z);
            }
        };
    }
}
