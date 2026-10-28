package databack.common.worldgen.biome.preset;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import databack.common.annotation.ThreadSafe;
import databack.common.loader.VanillaDatapackDownloader;
import databack.common.worldgen.biome.ClimateEntry;

/// The built-in presets a `multi_noise_biome_source_parameter_list` names (`minecraft:overworld`,
/// `minecraft:nether`). Vanilla defines them in code, so they are built from the classes the vanilla datapack
/// download fetched (see [VanillaPresetExtractor]), once each.
@ThreadSafe
public final class VanillaBiomePresets {

    private static final Logger LOGGER = LogManager.getLogger("databack-biome-presets");

    /// Failures are cached too (as empty), so each one is reported once.
    private static final Map<String, Optional<List<ClimateEntry>>> PRESETS = new ConcurrentHashMap<>();

    private VanillaBiomePresets() {}

    /// The preset's entries in vanilla's list order, or null (after logging a warning) when the id is unknown or the
    /// preset is unavailable, e.g. because the vanilla download failed. Before the download has finished nothing is
    /// cached, so a later call can still succeed.
    @Nullable
    public static List<ClimateEntry> get(@NotNull String presetId) {
        File datapackDir = VanillaDatapackDownloader.getCachedDir();
        if (datapackDir == null) return build(presetId, null);

        return PRESETS.computeIfAbsent(presetId, id -> Optional.ofNullable(build(id, datapackDir))).orElse(null);
    }

    /// Builds a preset from the classes cached in `datapackDir`, without caching the result. Null (after logging a
    /// warning) when it cannot be built.
    @Nullable
    static List<ClimateEntry> build(@NotNull String presetId, @Nullable File datapackDir) {
        String entry;
        Function<byte[], List<ClimateEntry>> extractor;

        switch (presetId) {
            case "minecraft:overworld" -> {
                entry = VanillaPresetExtractor.OVERWORLD_CLASS;
                extractor = VanillaPresetExtractor::overworld;
            }
            case "minecraft:nether" -> {
                entry = VanillaPresetExtractor.NETHER_CLASS;
                extractor = VanillaPresetExtractor::nether;
            }
            default -> {
                LOGGER.warn("Unknown multi-noise biome preset {}; biomes using it will not generate", presetId);
                return null;
            }
        }

        if (datapackDir == null) {
            LOGGER.warn("Biome preset {} is unavailable: the vanilla datapack was not downloaded", presetId);
            return null;
        }

        try {
            byte[] classFile = Files.readAllBytes(VanillaDatapackDownloader.getClassFile(datapackDir, entry).toPath());
            return extractor.apply(classFile);
        } catch (IOException | IllegalStateException e) {
            LOGGER.warn("Biome preset {} is unavailable: {}", presetId, e.getMessage(), e);
            return null;
        }
    }
}
