package databack.common.network;

import java.util.Map;
import java.util.TreeMap;

import net.minecraft.world.World;

import org.jetbrains.annotations.NotNull;

import databack.common.biome.ModernBiomeLifecycle;
import databack.common.network.PacketEncoderSyncModernBiomes.PacketSyncModernBiomes;

/// Sends a client the server's active modern biomes and their numeric biome ids, so both agree on what a chunk's biome
/// ids mean.
public class PacketEncoderSyncModernBiomes extends DBPacketEncoder<PacketSyncModernBiomes> {

    public static final class PacketSyncModernBiomes implements DBPacket {

        /// Numeric biome id by datapack id.
        @NotNull
        public final Map<String, Integer> biomes;

        public PacketSyncModernBiomes(@NotNull Map<String, Integer> biomes) {
            this.biomes = biomes;
        }

        @Override
        public byte getPacketID() {
            return DBPacketEntry.SyncModernBiomes.id;
        }
    }

    @Override
    public byte getPacketID() {
        return DBPacketEntry.SyncModernBiomes.id;
    }

    @Override
    public void writePacket(DBPacketBuffer buffer, PacketSyncModernBiomes packet) {
        buffer.writeVarIntToBuffer(packet.biomes.size());
        for (Map.Entry<String, Integer> e : packet.biomes.entrySet()) {
            buffer.writeStringToBuffer(e.getKey());
            buffer.writeVarIntToBuffer(e.getValue());
        }
    }

    @Override
    public PacketSyncModernBiomes readPacket(DBPacketBuffer buffer) {
        Map<String, Integer> biomes = new TreeMap<>();
        int count = buffer.readVarIntFromBuffer();
        for (int i = 0; i < count; i++) biomes.put(buffer.readStringFromBuffer(1000), buffer.readVarIntFromBuffer());
        return new PacketSyncModernBiomes(biomes);
    }

    @Override
    public void process(World world, PacketSyncModernBiomes packet) {
        ModernBiomeLifecycle.receiveFromServer(packet.biomes);
    }
}
