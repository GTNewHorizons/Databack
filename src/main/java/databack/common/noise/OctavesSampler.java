package databack.common.noise;

import java.util.Random;
import java.util.function.Supplier;

/// Layers several samplers on top of each other.
/// More octaves increase the CPU cost linearly, but increase the complexity and detail of the returned noise.
/// Each octave has an increasing scale (smaller features) and a decreasing amplitude (smaller effect).
public class OctavesSampler implements NoiseSampler {

    private final NoiseSampler[] octaves;
    private final float[] amplitudes, scales;
    private final float norm;

    public OctavesSampler(Supplier<NoiseSampler> samplers, int octaves) {
        this.octaves = new NoiseSampler[octaves];
        this.amplitudes = new float[octaves];
        this.scales = new float[octaves];

        for (int i = 0; i < octaves; i++) {
            this.octaves[i] = samplers.get();
            this.amplitudes[i] = (float) (1d / Math.pow(2d, i));
            this.scales[i] = (float) Math.pow(2d, i);
        }

        float sum = 0;

        for (float amp : amplitudes) {
            sum += amp;
        }

        this.norm = 1f / sum;
    }

    public OctavesSampler(Random rng, int octaves) {
        this(() -> new SimplexSampler(rng), octaves);
    }

    @Override
    public float sample(float x, float y) {
        float value = 0;

        for (int i = 0, octavesLength = octaves.length; i < octavesLength; i++) {
            NoiseSampler sampler = octaves[i];
            float scale = scales[i];

            value += sampler.sample(x * scale, y * scale) * amplitudes[i];
        }

        return value * norm;
    }

    @Override
    public float sample(float x, float y, float z) {
        float value = 0;

        for (int i = 0, octavesLength = octaves.length; i < octavesLength; i++) {
            NoiseSampler sampler = octaves[i];
            float scale = scales[i];

            value += sampler.sample(x * scale, y * scale, z * scale) * amplitudes[i];
        }

        return value * norm;
    }
}
