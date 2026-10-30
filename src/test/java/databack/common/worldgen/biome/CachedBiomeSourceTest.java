package databack.common.worldgen.biome;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

/// [CachedBiomeSource] asks its source once per cell it keeps, and always answers as the source does.
class CachedBiomeSourceTest {

    private final Map<String, Integer> asked = new HashMap<>();
    private final CachedBiomeSource cached = new CachedBiomeSource((x, y, z) -> {
        asked.merge(x + " " + y + " " + z, 1, Integer::sum);
        return y == 9 ? null : x + "/" + y + "/" + z;
    });

    @Test
    void cellsAreAskedOnce() {
        for (int i = 0; i < 3; i++) assertEquals("1/-2/3", cached.getNoiseBiome(1, -2, 3));
        assertEquals(1, asked.get("1 -2 3"));
    }

    @Test
    void cellsWithoutABiomeAreKeptToo() {
        assertNull(cached.getNoiseBiome(0, 9, 0));
        assertNull(cached.getNoiseBiome(0, 9, 0));
        assertEquals(1, asked.get("0 9 0"));
    }

    /// Far more cells than slots: whatever was replaced is asked again, never answered wrong.
    @Test
    void replacedCellsAreAskedAgain() {
        for (int round = 0; round < 2; round++) {
            for (int x = -300; x < 300; x++) {
                for (int z = -300; z < 300; z++) assertEquals(x + "/5/" + z, cached.getNoiseBiome(x, 5, z));
            }
        }
    }
}
