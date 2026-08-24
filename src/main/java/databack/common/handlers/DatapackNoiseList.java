package databack.common.handlers;

import java.util.HashMap;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import com.gtnewhorizon.gtnhlib.noise.NoiseSampler;
import databack.common.dto.worldgen.noise.DatapackNoise;
import databack.common.worldgen.noise.NormalNoise;
import databack.common.worldgen.rng.RandomFactory;
import databack.common.worldgen.rng.RandomSource;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

public class DatapackNoiseList extends JsonDatapackTypeHandler<DatapackNoise> {

    public static final ResourceType<DatapackNoiseList> RT = ResourceType.withPath("worldgen/noise");


    public DatapackNoiseList() {
        super(RT, DatapackNoise.class);
    }

    @Nullable
    public DatapackNoise getNoise(String name) {
        return super.getObject(name);
    }

    @Override
    public void onLoadStart() {
        super.onLoadStart();
    }

    @Override
    public void onWorldUnload() {
        super.onWorldUnload();
    }

    /**
     * Creates a {@link NormalNoise} for the given noise name and dimension seed,
     * using the same seeding as {@link #getSampler}.
     */
    public NormalNoise getNormalNoise(RandomSource rng, String name) {
        DatapackNoise data = getNoise(name);
        if (data == null) throw new IllegalStateException("Unknown noise: " + name);
        return NormalNoise.create(
            rng,
            new NormalNoise.NoiseParameters(data.firstOctave, data.amplitudes));
    }

    public NoiseSampler getSampler(RandomFactory rng, String name) {
        NormalNoise noise = getNormalNoise(rng.fromHashOf(name), name);

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
