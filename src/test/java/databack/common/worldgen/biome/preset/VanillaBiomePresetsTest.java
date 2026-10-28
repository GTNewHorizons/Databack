package databack.common.worldgen.biome.preset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.zip.ZipFile;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import databack.common.loader.VanillaDatapackDownloader;
import databack.common.worldgen.biome.ClimateEntry;

class VanillaBiomePresetsTest {

    private static final Path JAR = Paths.get(System.getProperty("databack.vanillaJar",
        System.getProperty("user.home") + "/.gradle/caches/minecraft-client-jars/minecraft-26.1-client.jar"));

    @Test
    void buildsPresetsFromCachedClasses(@TempDir File datapackDir) throws IOException {
        cacheClasses(datapackDir);

        List<ClimateEntry> overworld = VanillaBiomePresets.build("minecraft:overworld", datapackDir);
        List<ClimateEntry> nether = VanillaBiomePresets.build("minecraft:nether", datapackDir);

        assertEquals(7593, overworld.size());
        assertEquals("minecraft:mushroom_fields", overworld.get(0).biome());
        assertEquals(5, nether.size());
        assertEquals("minecraft:nether_wastes", nether.get(0).biome());
    }

    @Test
    void unknownPresetIsNull(@TempDir File datapackDir) throws IOException {
        cacheClasses(datapackDir);
        assertNull(VanillaBiomePresets.build("minecraft:the_end", datapackDir));
    }

    @Test
    void missingDownloadIsNull(@TempDir File datapackDir) {
        assertNull(VanillaBiomePresets.build("minecraft:overworld", null));
        assertNull(VanillaBiomePresets.build("minecraft:overworld", datapackDir));
    }

    /// Lays the classes out the way `VanillaDatapackDownloader` caches them.
    private static void cacheClasses(File datapackDir) throws IOException {
        try (ZipFile zip = new ZipFile(JAR.toFile())) {
            for (String entry : VanillaDatapackDownloader.VANILLA_CLASSES) {
                Path dest = VanillaDatapackDownloader.getClassFile(datapackDir, entry).toPath();
                Files.createDirectories(dest.getParent());
                try (InputStream in = zip.getInputStream(zip.getEntry(entry))) {
                    Files.copy(in, dest);
                }
            }
        }
    }
}
