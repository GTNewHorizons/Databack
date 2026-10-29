package databack.common.worldgen.feature;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import databack.common.annotation.ThreadSafe;
import databack.common.loader.VanillaDatapackDownloader;

/// The [VanillaFeatures] built from the classes the vanilla datapack download fetched, loaded once.
@ThreadSafe
public final class VanillaFeaturesCache {

    private static final Logger LOGGER = LogManager.getLogger("databack|features");

    private static volatile VanillaFeatures features;
    private static volatile boolean failed;

    private VanillaFeaturesCache() {}

    /// The features, or null (after logging a warning) when they are unavailable, e.g. because the vanilla download
    /// failed. Before the download has finished nothing is cached, so a later call can still succeed.
    @Nullable
    public static VanillaFeatures get() {
        if (features != null || failed) return features;

        File datapackDir = VanillaDatapackDownloader.getCachedDir();
        if (datapackDir == null) {
            LOGGER.warn("Features are unavailable: the vanilla datapack was not downloaded");
            return null;
        }

        synchronized (VanillaFeaturesCache.class) {
            if (features == null && !failed) {
                try {
                    features = VanillaFeatures.load(entry -> {
                        try {
                            return Files.readAllBytes(VanillaDatapackDownloader.getClassFile(datapackDir, entry)
                                .toPath());
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    });
                } catch (IllegalStateException | UncheckedIOException e) {
                    LOGGER.warn("Features are unavailable: {}", e.getMessage(), e);
                    failed = true;
                }
            }
            return features;
        }
    }
}
