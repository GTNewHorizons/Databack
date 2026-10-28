package databack.common.worldgen.biome.zoom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.File;
import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import databack.common.worldgen.vanilla.VanillaJar;

class VanillaBiomeZoomTest {

    @Test
    void loadsFromCachedClasses(@TempDir File datapackDir) throws IOException {
        VanillaJar.cacheClasses(datapackDir);

        BiomeZoom zoom = VanillaBiomeZoom.build(datapackDir);
        assertNotNull(zoom);

        ZoomedBiomes<String> constant = zoom.zoom((qx, qy, qz) -> "minecraft:plains", zoom.zoomSeed(1));
        assertEquals("minecraft:plains", constant.biome(10, 64, -10));
    }

    @Test
    void missingDownloadIsNull(@TempDir File datapackDir) {
        assertNull(VanillaBiomeZoom.build(null));
        assertNull(VanillaBiomeZoom.build(datapackDir));
    }
}
