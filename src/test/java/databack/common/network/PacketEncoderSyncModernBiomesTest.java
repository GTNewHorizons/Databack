package databack.common.network;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;

import databack.common.network.PacketEncoderSyncModernBiomes.PacketSyncModernBiomes;
import io.netty.buffer.Unpooled;

class PacketEncoderSyncModernBiomesTest {

    @Test
    void roundTrips() {
        Map<String, Integer> biomes = new TreeMap<>();
        biomes.put("minecraft:cherry_grove", 40);
        biomes.put("terralith:alpine_grove", 65000);

        PacketEncoderSyncModernBiomes encoder = new PacketEncoderSyncModernBiomes();
        DBPacketBuffer buffer = new DBPacketBuffer(Unpooled.buffer());
        encoder.writePacket(buffer, new PacketSyncModernBiomes(biomes));

        assertEquals(biomes, encoder.readPacket(buffer).biomes);
    }
}
