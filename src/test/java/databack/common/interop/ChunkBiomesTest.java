package databack.common.interop;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.world.chunk.Chunk;

import org.junit.jupiter.api.Test;

/// Covers 1.7's 8-bit storage; EndlessIDs is not on the test classpath.
class ChunkBiomesTest {

    @Test
    void writesEightBitIds() {
        assertEquals(256, ChunkBiomes.VANILLA.idCount());
        assertEquals(65536, ChunkBiomes.ENDLESS_IDS.idCount());

        Chunk chunk = new Chunk(null, 0, 0);
        int[] ids = new int[256];
        for (int i = 0; i < 256; i++) ids[i] = i % 255;

        ChunkBiomes.VANILLA.write(chunk, ids);

        for (int i = 0; i < 256; i++) assertEquals(i % 255, chunk.getBiomeArray()[i] & 0xFF);
    }
}
