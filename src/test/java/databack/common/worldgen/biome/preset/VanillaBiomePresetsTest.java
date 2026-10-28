package databack.common.worldgen.biome.preset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import databack.common.worldgen.biome.ClimateEntry;
import databack.common.worldgen.vanilla.VanillaJar;

class VanillaBiomePresetsTest {

    @Test
    void buildsPresetsFromCachedClasses(@TempDir File datapackDir) throws IOException {
        VanillaJar.cacheClasses(datapackDir);

        List<ClimateEntry> overworld = VanillaBiomePresets.build("minecraft:overworld", datapackDir);
        List<ClimateEntry> nether = VanillaBiomePresets.build("minecraft:nether", datapackDir);

        assertEquals(7593, overworld.size());
        assertEquals("minecraft:mushroom_fields", overworld.get(0).biome());
        assertEquals(5, nether.size());
        assertEquals("minecraft:nether_wastes", nether.get(0).biome());
    }

    @Test
    void unknownPresetIsNull(@TempDir File datapackDir) throws IOException {
        VanillaJar.cacheClasses(datapackDir);
        assertNull(VanillaBiomePresets.build("minecraft:the_end", datapackDir));
    }

    @Test
    void missingDownloadIsNull(@TempDir File datapackDir) {
        assertNull(VanillaBiomePresets.build("minecraft:overworld", null));
        assertNull(VanillaBiomePresets.build("minecraft:overworld", datapackDir));
    }
}
