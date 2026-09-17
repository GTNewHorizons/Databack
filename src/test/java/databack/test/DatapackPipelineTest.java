package databack.test;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.*;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import databack.common.handlers.DatapackHandlerRegistry;
import databack.common.handlers.IDatapackTypeHandler;
import databack.common.loader.Datapack;
import databack.common.loader.DatapackLoadException;
import databack.common.loader.DatapackLoader;
import databack.common.loader.DatapackWorldInfo;
import databack.common.loader.ResourceId;

/**
 * Integration tests for the full datapack loading pipeline.
 *
 * <p>Tests that require the vanilla datapack are skipped automatically when
 * {@code misc/test-packs/minecraft} is absent. To enable them, copy (or symlink)
 * the vanilla {@code minecraft} datapack folder there.
 */
class DatapackPipelineTest {

    static final File VANILLA_PACK = new File("misc/test-packs/minecraft");

    @TempDir
    Path tempWorldDir;

    RecordingHandler biomeHandler;
    RecordingHandler configuredFeatureHandler;
    RecordingHandler placedFeatureHandler;
    RecordingHandler densityFunctionHandler;
    RecordingHandler noiseSettingsHandler;
    RecordingHandler noiseHandler;
    RecordingHandler configuredCarverHandler;
    RecordingHandler dimensionTypeHandler;

    @BeforeEach
    void setup() {
        DatapackHandlerRegistry.clearForTesting();
        biomeHandler = new RecordingHandler();
        configuredFeatureHandler = new RecordingHandler();
        placedFeatureHandler = new RecordingHandler();
        densityFunctionHandler = new RecordingHandler();
        noiseSettingsHandler = new RecordingHandler();
        noiseHandler = new RecordingHandler();
        configuredCarverHandler = new RecordingHandler();
        dimensionTypeHandler = new RecordingHandler();

        DatapackHandlerRegistry.registerTypeHandler("worldgen/biome", () -> biomeHandler);
        DatapackHandlerRegistry.registerTypeHandler("worldgen/configured_feature", () -> configuredFeatureHandler);
        DatapackHandlerRegistry.registerTypeHandler("worldgen/placed_feature", () -> placedFeatureHandler);
        DatapackHandlerRegistry.registerTypeHandler("worldgen/density_function", () -> densityFunctionHandler);
        DatapackHandlerRegistry.registerTypeHandler("worldgen/noise_settings", () -> noiseSettingsHandler);
        DatapackHandlerRegistry.registerTypeHandler("worldgen/noise", () -> noiseHandler);
        DatapackHandlerRegistry.registerTypeHandler("worldgen/configured_carver", () -> configuredCarverHandler);
        DatapackHandlerRegistry.registerTypeHandler("dimension_type", () -> dimensionTypeHandler);
    }

    // ---- Discovery ----------------------------------------------------------

    @Test
    void testDiscovery_noDatapacksDir_returnsEmpty() {
        List<File> candidates = DatapackLoader.discoverCandidates(tempWorldDir.toFile());
        assertTrue(candidates.isEmpty());
    }

    @Test
    void testDiscovery_emptyDatapacksDir_returnsEmpty() throws IOException {
        Files.createDirectories(tempWorldDir.resolve("datapacks"));
        List<File> candidates = DatapackLoader.discoverCandidates(tempWorldDir.toFile());
        assertTrue(candidates.isEmpty());
    }

    @Test
    void testDiscovery_vanillaPack_findsOnePack() throws IOException {
        assumeTrue(VANILLA_PACK.exists(), "Vanilla pack absent; copy it to misc/test-packs/minecraft");

        Path datapacksDir = Files.createDirectories(tempWorldDir.resolve("datapacks"));
        Files.createSymbolicLink(datapacksDir.resolve("minecraft"), VANILLA_PACK.toPath().toAbsolutePath());

        List<File> candidates = DatapackLoader.discoverCandidates(tempWorldDir.toFile());

        assertEquals(1, candidates.size());
        assertEquals("minecraft", candidates.get(0).getName());
    }

    // ---- Candidate loading --------------------------------------------------

    @Test
    void testLoadDatapacks_packWithoutMeta_skippedSilently() throws IOException {
        Path datapacksDir = Files.createDirectories(tempWorldDir.resolve("datapacks"));
        Files.createDirectories(datapacksDir.resolve("no-meta"));

        List<File> candidates = DatapackLoader.discoverCandidates(tempWorldDir.toFile());
        List<Datapack> packs = DatapackLoader.loadDatapacks(candidates);

        assertTrue(packs.isEmpty());
        closePacks(packs);
    }

    @Test
    void testLoadDatapacks_invalidMeta_throwsLoadException() throws IOException {
        Path datapacksDir = Files.createDirectories(tempWorldDir.resolve("datapacks"));
        Path badPack = Files.createDirectories(datapacksDir.resolve("bad"));
        Files.write(badPack.resolve("pack.mcmeta"), "not-valid-json{{{".getBytes(StandardCharsets.UTF_8));

        List<File> candidates = DatapackLoader.discoverCandidates(tempWorldDir.toFile());

        assertThrows(DatapackLoadException.class, () -> DatapackLoader.loadDatapacks(candidates));
    }

    @Test
    void testLoadDatapacks_vanillaPack_parsesName() throws IOException {
        assumeTrue(VANILLA_PACK.exists(), "Vanilla pack absent; copy it to misc/test-packs/minecraft");

        Path datapacksDir = Files.createDirectories(tempWorldDir.resolve("datapacks"));
        Files.createSymbolicLink(datapacksDir.resolve("minecraft"), VANILLA_PACK.toPath().toAbsolutePath());

        List<File> candidates = DatapackLoader.discoverCandidates(tempWorldDir.toFile());
        List<Datapack> packs = DatapackLoader.loadDatapacks(candidates);

        try {
            assertEquals(1, packs.size());
            assertEquals("minecraft", packs.get(0).getName());
            assertNotNull(packs.get(0).getMetadata());
        } finally {
            closePacks(packs);
        }
    }

    // ---- Full pipeline -------------------------------------------------------

    @Test
    void testFullPipeline_noDatapacksDir_noOp() {
        assertDoesNotThrow(() -> DatapackLoader.load(tempWorldDir.toFile(), new TestDatapackWorldInfo()));
        assertFalse(biomeHandler.started, "onLoadStart should not fire when no packs are found");
    }

    @Test
    void testFullPipeline_emptyDatapacksDir_noOp() throws IOException {
        Files.createDirectories(tempWorldDir.resolve("datapacks"));
        assertDoesNotThrow(() -> DatapackLoader.load(tempWorldDir.toFile(), new TestDatapackWorldInfo()));
        assertFalse(biomeHandler.started);
    }

    @Test
    void testFullPipeline_vanillaPack_noExceptions() throws IOException {
        assumeTrue(VANILLA_PACK.exists(), "Vanilla pack absent; copy it to misc/test-packs/minecraft");

        Path datapacksDir = Files.createDirectories(tempWorldDir.resolve("datapacks"));
        Files.createSymbolicLink(datapacksDir.resolve("minecraft"), VANILLA_PACK.toPath().toAbsolutePath());

        assertDoesNotThrow(() -> DatapackLoader.load(tempWorldDir.toFile(), new TestDatapackWorldInfo()));
    }

    @Test
    void testFullPipeline_vanillaPack_dispatchesKnownTypes() throws IOException {
        assumeTrue(VANILLA_PACK.exists(), "Vanilla pack absent; copy it to misc/test-packs/minecraft");

        Path datapacksDir = Files.createDirectories(tempWorldDir.resolve("datapacks"));
        Files.createSymbolicLink(datapacksDir.resolve("minecraft"), VANILLA_PACK.toPath().toAbsolutePath());

        DatapackLoader.load(tempWorldDir.toFile(), new TestDatapackWorldInfo());

        assertFalse(biomeHandler.dispatched.isEmpty(), "No biomes dispatched");
        assertFalse(configuredFeatureHandler.dispatched.isEmpty(), "No configured_features dispatched");
        assertFalse(placedFeatureHandler.dispatched.isEmpty(), "No placed_features dispatched");
        assertFalse(densityFunctionHandler.dispatched.isEmpty(), "No density_functions dispatched");
        assertFalse(noiseSettingsHandler.dispatched.isEmpty(), "No noise_settings dispatched");
        assertFalse(configuredCarverHandler.dispatched.isEmpty(), "No configured_carvers dispatched");
        assertFalse(dimensionTypeHandler.dispatched.isEmpty(), "No dimension_types dispatched");
    }

    @Test
    void testFullPipeline_vanillaPack_containsKnownBiome() throws IOException {
        assumeTrue(VANILLA_PACK.exists(), "Vanilla pack absent; copy it to misc/test-packs/minecraft");

        Path datapacksDir = Files.createDirectories(tempWorldDir.resolve("datapacks"));
        Files.createSymbolicLink(datapacksDir.resolve("minecraft"), VANILLA_PACK.toPath().toAbsolutePath());

        DatapackLoader.load(tempWorldDir.toFile(), new TestDatapackWorldInfo());

        assertTrue(
            biomeHandler.dispatched.stream().anyMatch(id -> id.id().equals("plains")),
            "Expected minecraft:plains biome to be dispatched");
    }

    @Test
    void testFullPipeline_handlerLifecycleOrder() throws IOException {
        assumeTrue(VANILLA_PACK.exists(), "Vanilla pack absent; copy it to misc/test-packs/minecraft");

        Path datapacksDir = Files.createDirectories(tempWorldDir.resolve("datapacks"));
        Files.createSymbolicLink(datapacksDir.resolve("minecraft"), VANILLA_PACK.toPath().toAbsolutePath());

        List<String> events = new ArrayList<>();
        IDatapackTypeHandler ordered = new IDatapackTypeHandler() {

            @Override
            public void handle(ResourceId id, byte[] content) {
                if (events.isEmpty() || !events.get(events.size() - 1).equals("handle")) {
                    events.add("handle");
                }
            }

            @Override
            public void onLoadStart() {
                events.add("start");
            }

            @Override
            public void onLoadFinished() {
                events.add("finish");
            }
        };

        DatapackHandlerRegistry.clearForTesting();
        DatapackHandlerRegistry.registerTypeHandler("worldgen/biome", () -> ordered);

        DatapackLoader.load(tempWorldDir.toFile(), new TestDatapackWorldInfo());

        assertEquals(Arrays.asList("start", "handle", "finish"), events);
    }

    // ---- Helpers ------------------------------------------------------------

    /**
     * Writes a minimal datapack containing a single tag file.
     *
     * @param datapacksDir the datapacks directory
     * @param packName     name of the pack folder (created if absent)
     * @param namespace    tag namespace, e.g. {@code "minecraft"}
     * @param tagType      tag folder, e.g. {@code "blocks"}
     * @param tagName      tag file name (without {@code .json}), e.g. {@code "logs"}
     * @param json         raw JSON content for the tag file
     */
    private static void writeTagPack(
        Path datapacksDir,
        String packName,
        String namespace,
        String tagType,
        String tagName,
        String json) throws IOException {

        Path packDir = Files.createDirectories(datapacksDir.resolve(packName));
        Files.write(
            packDir.resolve("pack.mcmeta"),
            ("{\"pack\":{\"pack_format\":48,\"description\":\"test\"}}").getBytes(StandardCharsets.UTF_8));
        Path tagDir = Files.createDirectories(packDir.resolve("data").resolve(namespace).resolve("tags").resolve(tagType));
        Files.write(tagDir.resolve(tagName + ".json"), json.getBytes(StandardCharsets.UTF_8));
    }

    private static void closePacks(List<Datapack> packs) {
        for (Datapack p : packs) {
            try {
                p.getSource().close();
            } catch (IOException ignored) {}
        }
    }

    static class RecordingHandler implements IDatapackTypeHandler {

        final List<ResourceId> dispatched = new ArrayList<>();
        boolean started = false;
        boolean finished = false;

        @Override
        public void handle(ResourceId id, byte[] content) {
            dispatched.add(id);
        }

        @Override
        public void onLoadStart() {
            started = true;
            dispatched.clear();
        }

        @Override
        public void onLoadFinished() {
            finished = true;
        }
    }

    static class TestDatapackWorldInfo implements DatapackWorldInfo {

        @Override
        public net.minecraft.nbt.NBTTagCompound db$saveDatapackInfo() { return new net.minecraft.nbt.NBTTagCompound(); }

        @Override
        public void db$loadDatapackInfo(net.minecraft.nbt.NBTTagCompound tag) {}

        @Override
        public List<String> db$getDatapackOrder() {
            return new ArrayList<>();
        }

        @Override
        public Set<String> db$getDisabledPacks() {
            return new HashSet<>();
        }

        @Override
        public void db$enable(String pack) {}

        @Override
        public void db$disable(String pack) {}

        @Override
        public void db$syncPackDeltas(@NotNull List<Datapack> packs) {}

        @Override
        public @NotNull List<Datapack> db$order(@NotNull List<Datapack> packs) {
            return new ArrayList<>(packs);
        }
    }
}
