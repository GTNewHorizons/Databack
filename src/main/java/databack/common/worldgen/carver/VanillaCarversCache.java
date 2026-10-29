package databack.common.worldgen.carver;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import databack.common.annotation.ThreadSafe;
import databack.common.loader.VanillaDatapackDownloader;

/// The [VanillaCarvers] built from the classes the vanilla datapack download fetched, loaded once.
@ThreadSafe
public final class VanillaCarversCache {

    private static final Logger LOGGER = LogManager.getLogger("databack-carver");

    private static volatile VanillaCarvers carvers;
    private static volatile boolean failed;

    private VanillaCarversCache() {}

    /// The carvers, or null (after logging a warning) when they are unavailable, e.g. because the vanilla download
    /// failed. Before the download has finished nothing is cached, so a later call can still succeed.
    @Nullable
    public static VanillaCarvers get() {
        if (carvers != null || failed) return carvers;

        File datapackDir = VanillaDatapackDownloader.getCachedDir();
        if (datapackDir == null) {
            LOGGER.warn("Carvers are unavailable: the vanilla datapack was not downloaded");
            return null;
        }

        synchronized (VanillaCarversCache.class) {
            if (carvers == null && !failed) {
                try {
                    carvers = VanillaCarvers.load(entry -> {
                        try {
                            return Files.readAllBytes(VanillaDatapackDownloader.getClassFile(datapackDir, entry)
                                .toPath());
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    });
                } catch (IllegalStateException | UncheckedIOException e) {
                    LOGGER.warn("Carvers are unavailable: {}", e.getMessage(), e);
                    failed = true;
                }
            }
            return carvers;
        }
    }
}
