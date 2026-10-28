package databack.common.worldgen.surface;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.init.Blocks;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.gtnewhorizon.gtnhlib.util.data.BlockMeta;
import com.gtnewhorizon.gtnhlib.util.data.ImmutableBlockMeta;

import databack.common.dto.worldgen.surface_rule.ISurfaceRuleFactory;
import databack.common.worldgen.biome.ClimateSampler;
import databack.common.worldgen.biome.MultiNoiseBiomeSource;
import databack.common.worldgen.biome.SurfaceBiomes;
import databack.common.worldgen.biome.preset.VanillaPresetExtractor;
import databack.common.worldgen.biome.zoom.BiomeZoom;
import databack.common.worldgen.density.DensityBuildContext;
import databack.common.worldgen.density.DensityRouter;
import databack.common.worldgen.density.VanillaWorldgen;
import databack.common.worldgen.vanilla.VanillaJar;

/// Our surface system for the overworld at one seed, with block names encoded as metadata so results can be compared
/// with the harness dumps by name.
final class SurfaceFixture {

    private static final List<String> NAMES = new ArrayList<>();
    private static final Map<String, Float> TEMPERATURES = new HashMap<>();

    private final long seed;
    private final DensityBuildContext density;
    private final DensityRouter router;
    private final SurfaceBiomes biomes;

    SurfaceFixture(long seed) throws IOException {
        this.seed = seed;
        this.density = VanillaWorldgen.context(seed, false);
        this.router = VanillaWorldgen.router("overworld", seed, true);

        MultiNoiseBiomeSource source = MultiNoiseBiomeSource.create(
            VanillaPresetExtractor.overworld(VanillaJar.classFile(VanillaPresetExtractor.OVERWORLD_CLASS)),
            ClimateSampler.of(router));
        BiomeZoom zoom = BiomeZoom.load(VanillaJar.classFile(BiomeZoom.MANAGER_CLASS),
            VanillaJar.classFile(BiomeZoom.LCG_CLASS));
        this.biomes = new SurfaceBiomes(source, zoom, zoom.zoomSeed(seed));
    }

    /// The system for `rule` (surface rule JSON) in the overworld's vertical range.
    SurfaceSystem system(JsonElement rule) {
        ISurfaceRuleFactory factory = VanillaWorldgen.parse(rule, ISurfaceRuleFactory.class);
        SurfaceBuildContext ctx = new SurfaceBuildContext(density, -64, 384,
            state -> encode(state.Name.toString()), SurfaceFixture::temperature);
        return new SurfaceSystem(factory.instantiate(ctx), -64, 384, seed, density.noise("minecraft:surface"),
            density.noise("minecraft:surface_secondary"), router.preliminarySurfaceLevel);
    }

    /// The vanilla overworld surface rule.
    static JsonElement overworldRule() throws IOException {
        try (Reader r = Files.newBufferedReader(VanillaWorldgen.WORLDGEN.resolve("noise_settings/overworld.json"))) {
            return new JsonParser().parse(r).getAsJsonObject().get("surface_rule");
        }
    }

    /// A cached per-chunk lookup, as the generator uses; get one per chunk.
    BlockBiomes biomes() {
        return biomes.forChunk();
    }

    /// The name a result of [#system] stands for; `fallback` (the default block) for null.
    static String decode(ImmutableBlockMeta block, String fallback) {
        return block == null ? fallback : NAMES.get(block.getBlockMeta());
    }

    private static synchronized ImmutableBlockMeta encode(String name) {
        int index = NAMES.indexOf(name);
        if (index < 0) {
            index = NAMES.size();
            NAMES.add(name);
        }
        return new BlockMeta(Blocks.stone, index);
    }

    private static synchronized double temperature(String biome) {
        return TEMPERATURES.computeIfAbsent(biome, id -> {
            Path file = VanillaWorldgen.WORLDGEN.resolve("biome/" + id.replace("minecraft:", "") + ".json");
            try (Reader r = Files.newBufferedReader(file)) {
                JsonObject json = new JsonParser().parse(r).getAsJsonObject();
                return json.get("temperature").getAsFloat();
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        });
    }
}
