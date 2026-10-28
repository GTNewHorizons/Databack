package databack.common.worldgen.biome.zoom;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import databack.common.annotation.ThreadSafe;
import databack.common.loader.VanillaDatapackDownloader;

/// The [BiomeZoom] built from the classes the vanilla datapack download fetched, loaded once.
@ThreadSafe
public final class VanillaBiomeZoom {

    private static final Logger LOGGER = LogManager.getLogger("databack-biome-zoom");

    private static volatile BiomeZoom zoom;
    private static volatile boolean failed;

    private VanillaBiomeZoom() {}

    /// The zoom, or null (after logging a warning) when it is unavailable, e.g. because the vanilla download failed.
    /// Before the download has finished nothing is cached, so a later call can still succeed.
    @Nullable
    public static BiomeZoom get() {
        if (zoom != null || failed) return zoom;

        File datapackDir = VanillaDatapackDownloader.getCachedDir();
        if (datapackDir == null) return build(null);

        synchronized (VanillaBiomeZoom.class) {
            if (zoom == null && !failed) {
                zoom = build(datapackDir);
                failed = zoom == null;
            }
            return zoom;
        }
    }

    /// Loads the zoom from the classes cached in `datapackDir`, without caching the result. Null (after logging a
    /// warning) when it cannot be loaded.
    @Nullable
    static BiomeZoom build(@Nullable File datapackDir) {
        if (datapackDir == null) {
            LOGGER.warn("Biome zoom is unavailable: the vanilla datapack was not downloaded");
            return null;
        }

        try {
            return BiomeZoom.load(read(datapackDir, BiomeZoom.MANAGER_CLASS), read(datapackDir, BiomeZoom.LCG_CLASS));
        } catch (IOException | IllegalStateException e) {
            LOGGER.warn("Biome zoom is unavailable: {}", e.getMessage(), e);
            return null;
        }
    }

    private static byte[] read(@NotNull File datapackDir, @NotNull String entry) throws IOException {
        return Files.readAllBytes(VanillaDatapackDownloader.getClassFile(datapackDir, entry).toPath());
    }
}
