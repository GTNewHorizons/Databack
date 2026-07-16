package databack.common.handlers;

import java.util.HashMap;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import com.gtnewhorizon.gtnhlib.noise.NoiseSampler;
import databack.common.dto.worldgen.noise.DatapackNoise;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

public class DatapackNoiseList extends JsonDatapackTypeHandler<DatapackNoise> {

    public static final DatapackNoiseList INSTANCE = new DatapackNoiseList();

    private final Map<String, Long2ObjectOpenHashMap<NoiseSampler>> samplerCache = new HashMap<>();

    public DatapackNoiseList() {
        super("worldgen/noise", DatapackNoise.class);
    }

    @Nullable
    public DatapackNoise getNoise(String name) {
        return super.getObject(name);
    }

    @Override
    public void onLoadStart() {
        super.onLoadStart();
        samplerCache.clear();
    }

    @Override
    public void onWorldUnload() {
        super.onWorldUnload();
        samplerCache.clear();
    }

    public NoiseSampler getSampler(long dimensionSeed, String name) {
        var cache = samplerCache.computeIfAbsent(name, $ -> new Long2ObjectOpenHashMap<>());

        var sampler = cache.get(dimensionSeed);

        if (sampler == null) {
            DatapackNoise data = getNoise(name);

            if (data == null) throw new IllegalStateException("Unknown noise: " + name);

            sampler = data.createSampler(dimensionSeed);

            cache.put(dimensionSeed, sampler);
        }

        return sampler;
    }
}
