package databack.common.worldgen.carver;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import databack.common.dto.worldgen.carver.IConfiguredCarver;
import databack.common.worldgen.aquifer.AquiferSystem;
import databack.common.worldgen.carver.standin.CvAquifer;
import databack.common.worldgen.carver.standin.CvBlock;
import databack.common.worldgen.carver.standin.CvBlockSet;
import databack.common.worldgen.carver.standin.CvBlockState;
import databack.common.worldgen.carver.standin.CvBlocks;
import databack.common.worldgen.carver.standin.CvChunk;
import databack.common.worldgen.carver.standin.CvChunkPos;
import databack.common.worldgen.carver.standin.CvStatePool;
import databack.common.worldgen.density.VanillaWorldgen;
import databack.common.worldgen.surface.SurfaceDump;
import databack.common.worldgen.vanilla.VanillaJar;

/// Shared pieces of the carver tests (`run.sh carver`). Block states' payloads are vanilla's block names.
final class CarverFixture {

    static final int MIN_Y = -64, HEIGHT = 384, SEA_LEVEL = 63;
    /// The carvers of nearly every overworld biome, in order.
    static final String[] OVERWORLD_CARVERS = { "minecraft:cave", "minecraft:cave_extra_underground",
        "minecraft:canyon" };

    private CarverFixture() {}

    private static VanillaCarvers carvers;

    static synchronized VanillaCarvers carvers() {
        if (carvers == null) {
            carvers = VanillaCarvers.load(entry -> {
                try {
                    return VanillaJar.classFile(entry);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        }
        return carvers;
    }

    static CvStatePool pool() {
        Map<CvBlock, Object> payloads = new HashMap<>();
        for (CvBlock block : new CvBlock[] { CvBlocks.AIR, CvBlocks.CAVE_AIR, CvBlocks.DIRT, CvBlocks.GRASS_BLOCK,
            CvBlocks.MYCELIUM, CvBlocks.WATER, CvBlocks.LAVA }) {
            payloads.put(block, block.id);
        }
        return new CvStatePool(payloads);
    }

    /// Configured carvers by id, from the vanilla datapack.
    static List<ConfiguredCarver> configured(CvStatePool pool, String... ids) {
        CarverBuildContext ctx = new CarverBuildContext(carvers(), replaceable -> blockSet(pool, replaceable),
            id -> null);
        List<ConfiguredCarver> result = new ArrayList<>();
        for (String id : ids) {
            Path file = VanillaWorldgen.WORLDGEN.resolve("configured_carver")
                .resolve(id.replace("minecraft:", "") + ".json");
            result.add(VanillaWorldgen.parse(json(file), IConfiguredCarver.class).instantiate(ctx));
        }
        return result;
    }

    /// A `replaceable` value (block ids and tags) over block-name payloads, with tags from the vanilla datapack.
    static CvBlockSet blockSet(CvStatePool pool, JsonElement replaceable) {
        Set<String> blocks = new HashSet<>();
        if (replaceable.isJsonArray()) {
            for (JsonElement e : replaceable.getAsJsonArray()) addEntry(e.getAsString(), blocks, new HashSet<>());
        } else {
            addEntry(replaceable.getAsString(), blocks, new HashSet<>());
        }
        return new CvBlockSet(pool, blocks::contains);
    }

    private static void addEntry(String entry, Set<String> blocks, Set<String> seen) {
        if (!entry.startsWith("#")) {
            blocks.add(entry);
            return;
        }

        String tag = entry.substring(1);
        if (!seen.add(tag)) return;
        Path file = VanillaWorldgen.WORLDGEN.resolve("../tags/block").resolve(tag.replace("minecraft:", "") + ".json");
        for (JsonElement value : json(file).getAsJsonObject().getAsJsonArray("values")) {
            String id = value.isJsonObject() ? value.getAsJsonObject().get("id").getAsString() : value.getAsString();
            addEntry(id, blocks, seen);
        }
    }

    private static JsonElement json(Path file) {
        try {
            return new JsonParser().parse(new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /// The chunk of a dump, as carver states.
    static CvChunk chunk(CvStatePool pool, SurfaceDump dump, int chunkX, int chunkZ) {
        CvBlockState[] states = new CvBlockState[HEIGHT * 256];
        for (int i = 0; i < states.length; i++) {
            states[i] = pool.state(dump.block(i & 15, MIN_Y + (i >> 8), (i >> 4) & 15));
        }
        return new CvChunk(new CvChunkPos(chunkX, chunkZ), MIN_Y, HEIGHT, states);
    }

    /// The global fluid rule's aquifer (aquifers off).
    static CvAquifer disabledAquifer(int chunkX, int chunkZ) {
        return new CvAquifer(AquiferSystem.disabled(SEA_LEVEL).chunkAquifer(chunkX, chunkZ, MIN_Y, HEIGHT));
    }
}
