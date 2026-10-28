package databack.common.worldgen.density;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import databack.common.dto.worldgen.density_function.BuiltinDensityFunctions;
import databack.common.dto.worldgen.density_function.IDensityFunctionFactory;
import databack.common.dto.worldgen.noise.DatapackNoise;
import databack.common.dto.worldgen.noise_settings.NoiseGeneratorSettings;
import databack.common.dto.worldgen.surface_rule.BuiltinSurfaceRules;
import databack.common.serde.MiscAdapters;
import databack.common.serde.DatapackSerialization;

/// Test fixture: reads density functions, noises and noise settings straight out of the extracted vanilla 26.1
/// datapack (`build/modernData`, populated by the `downloadVanillaDatapack` task), without the datapack loader.
public final class VanillaWorldgen {

    /// Relative to the project directory; JMH forks pass it in as `databack.projectDir` since they may run elsewhere.
    public static final Path WORLDGEN = Paths.get(System.getProperty("databack.projectDir", "."))
        .resolve("build/modernData/data/minecraft/worldgen");

    private static Gson gson;
    private static final Map<String, IDensityFunctionFactory> FUNCTIONS = new HashMap<>();
    private static final Map<String, DatapackNoise> NOISES = new HashMap<>();
    private static final Map<String, Settings> SETTINGS = new HashMap<>();

    private VanillaWorldgen() {}

    public static boolean available() {
        return Files.isDirectory(WORLDGEN);
    }

    private static synchronized Gson gson() {
        if (gson == null) {
            DatapackSerialization.resetForTesting();
            DatapackSerialization.init();
            BuiltinDensityFunctions.init();
            NoiseGeneratorSettings.init();
            MiscAdapters.init();
            BuiltinSurfaceRules.init();
            DatapackSerialization.finish();
            gson = DatapackSerialization.getGson();
        }
        return gson;
    }

    private static Path file(String dir, String id) {
        String path = id.startsWith("minecraft:") ? id.substring("minecraft:".length()) : id;
        return WORLDGEN.resolve(dir).resolve(path + ".json");
    }

    private static <T> T read(Path file, Class<T> type) {
        try (Reader r = Files.newBufferedReader(file)) {
            return gson().fromJson(r, type);
        } catch (IOException e) {
            throw new RuntimeException("Could not read " + file, e);
        }
    }

    public static synchronized IDensityFunctionFactory function(String id) {
        if (!FUNCTIONS.containsKey(id)) {
            Path f = file("density_function", id);
            FUNCTIONS.put(id, Files.exists(f) ? read(f, IDensityFunctionFactory.class) : null);
        }
        return FUNCTIONS.get(id);
    }

    /// Parses any datapack JSON the way the datapack loader would.
    public static <T> T parse(JsonElement json, Class<T> type) {
        return gson().fromJson(json, type);
    }

    /// Parses a density function the way the datapack loader would.
    public static IDensityFunctionFactory parseFunction(JsonElement json) {
        return gson().fromJson(json, IDensityFunctionFactory.class);
    }

    public static synchronized DatapackNoise noise(String id) {
        if (!NOISES.containsKey(id)) {
            Path f = file("noise", id);
            NOISES.put(id, Files.exists(f) ? read(f, DatapackNoise.class) : null);
        }
        return NOISES.get(id);
    }

    public static final class Settings {

        public final NoiseGeneratorSettings.NoiseRouter router;
        public final NoiseGeneratorSettings.NoiseSettings noise;
        public final boolean legacyRandomSource;

        Settings(NoiseGeneratorSettings.NoiseRouter router, NoiseGeneratorSettings.NoiseSettings noise, boolean legacy) {
            this.router = router;
            this.noise = noise;
            this.legacyRandomSource = legacy;
        }

        public int cellWidth() {
            return noise.size_horizontal * 4;
        }

        public int cellHeight() {
            return noise.size_vertical * 4;
        }
    }

    /// Only the parts of the noise settings density functions need (block states etc. need Minecraft's registries).
    public static synchronized Settings settings(String name) {
        return SETTINGS.computeIfAbsent(name, n -> {
            try (Reader r = Files.newBufferedReader(file("noise_settings", n))) {
                JsonObject json = new JsonParser().parse(r).getAsJsonObject();
                Gson g = gson();
                return new Settings(
                    g.fromJson(json.get("noise_router"), NoiseGeneratorSettings.NoiseRouter.class),
                    g.fromJson(json.get("noise"), NoiseGeneratorSettings.NoiseSettings.class),
                    json.has("legacy_random_source") && json.get("legacy_random_source").getAsBoolean());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public static DensityBuildContext context(long seed, boolean legacyRandomSource) {
        return new DensityBuildContext(seed, legacyRandomSource, VanillaWorldgen::noise, VanillaWorldgen::function);
    }

    public static DensityRouter router(String settingsName, long seed, boolean optimize) {
        Settings s = settings(settingsName);
        return context(seed, s.legacyRandomSource).buildRouter(s.router, optimize);
    }
}
