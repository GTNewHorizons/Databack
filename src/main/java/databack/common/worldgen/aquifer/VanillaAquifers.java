package databack.common.worldgen.aquifer;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import databack.common.annotation.ThreadSafe;
import databack.common.loader.VanillaDatapackDownloader;

/// The [VanillaAquifer] built from the class the vanilla datapack download fetched, loaded once.
@ThreadSafe
public final class VanillaAquifers {

    private static final Logger LOGGER = LogManager.getLogger("databack-aquifer");

    private static volatile VanillaAquifer aquifer;
    private static volatile boolean failed;

    private VanillaAquifers() {}

    /// The aquifer, or null (after logging a warning) when it is unavailable, e.g. because the vanilla download
    /// failed. Before the download has finished nothing is cached, so a later call can still succeed.
    @Nullable
    public static VanillaAquifer get() {
        if (aquifer != null || failed) return aquifer;

        File datapackDir = VanillaDatapackDownloader.getCachedDir();
        if (datapackDir == null) return build(null);

        synchronized (VanillaAquifers.class) {
            if (aquifer == null && !failed) {
                aquifer = build(datapackDir);
                failed = aquifer == null;
            }
            return aquifer;
        }
    }

    /// Loads the aquifer from the class cached in `datapackDir`, without caching it. Null (after logging a warning)
    /// when it cannot be loaded.
    @Nullable
    static VanillaAquifer build(@Nullable File datapackDir) {
        if (datapackDir == null) {
            LOGGER.warn("Aquifers are unavailable: the vanilla datapack was not downloaded");
            return null;
        }

        try {
            return VanillaAquifer.load(Files.readAllBytes(
                VanillaDatapackDownloader.getClassFile(datapackDir, VanillaAquifer.CLASS).toPath()));
        } catch (IOException | IllegalStateException e) {
            LOGGER.warn("Aquifers are unavailable: {}", e.getMessage(), e);
            return null;
        }
    }
}
