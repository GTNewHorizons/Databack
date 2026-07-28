package databack.common.handlers;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import com.gtnewhorizon.gtnhlib.noise.NoiseSampler;
import databack.common.dto.worldgen.noise.DatapackNoise;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

public class DatapackNoiseList extends JsonDatapackTypeHandler<DatapackNoise> {

    public static final ResourceType<DatapackNoiseList> RT = ResourceType.withPath("worldgen/noise");

    private final Map<String, Long2ObjectOpenHashMap<NoiseSampler>> samplerCache = new HashMap<>();

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

            long noiseSeed = hashNoiseName(name) ^ dimensionSeed;
            sampler = data.createSampler(noiseSeed);

            cache.put(dimensionSeed, sampler);
        }

        return sampler;
    }

    private static long hashNoiseName(String name) {
        try {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            byte[] digest = md5.digest(name.getBytes(StandardCharsets.UTF_8));
            long seed = 0L;
            for (int i = 0; i < 8; i++) {
                seed |= (long) (digest[i] & 0xFF) << (i * 8);
            }
            return seed;
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError("MD5 not available", e);
        }
    }
}
