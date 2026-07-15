package databack.common.handlers;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import org.jetbrains.annotations.Nullable;

import databack.common.dto.worldgen.noise.DatapackNoise;
import databack.common.noise.NoiseSampler;
import databack.common.noise.OctavesSampler;

public class DatapackNoiseList extends JsonDatapackTypeHandler<DatapackNoise> {

    public static final DatapackNoiseList INSTANCE = new DatapackNoiseList();

    private final Map<String, NoiseSampler> samplerCache = new HashMap<>();

    public DatapackNoiseList() {
        super("worldgen/noise", DatapackNoise.class);
    }

    @Nullable
    public DatapackNoise getNoise(String name) {
        return super.getObject(name);
    }

    /**
     * Creates a NoiseSampler for the given noise resource location.
     * Uses amplitudes.length as octave count with a deterministic seed.
     * Note: firstOctave and per-octave amplitude weights are not respected — approximation only.
     */
    public NoiseSampler createSampler(String name) {
        return samplerCache.computeIfAbsent(name, n -> {
            DatapackNoise data = getNoise(n);
            if (data == null) throw new IllegalStateException("Unknown noise: " + n);
            return new OctavesSampler(new Random(n.hashCode()), Math.max(1, data.amplitudes.length));
        });
    }

    @Override
    public void onWorldUnload() {
        super.onWorldUnload();
        samplerCache.clear();
    }

}
