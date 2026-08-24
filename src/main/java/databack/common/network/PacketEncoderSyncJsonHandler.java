package databack.common.network;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import net.minecraft.world.World;

import com.github.bsideup.jabel.Desugar;
import com.google.gson.JsonElement;
import databack.common.handlers.DatapackHandlerRegistry;
import databack.common.handlers.JsonDatapackTypeHandler;
import databack.common.network.PacketEncoderSyncJsonHandler.PacketSyncJsonHandler;
import databack.common.serde.DatapackSerialization;

public class PacketEncoderSyncJsonHandler extends DBPacketEncoder<PacketSyncJsonHandler> {

    @Desugar
    public record PacketSyncJsonHandler(String resourceType, Map<String, JsonElement> data) implements DBPacket {

        @Override
        public byte getPacketID() {
            return DBPacketEntry.SyncJsonHandler.id;
        }
    }

    public static PacketSyncJsonHandler createPacket(JsonDatapackTypeHandler<?> handler) {
        return new PacketSyncJsonHandler(handler.getResourceType(), handler.getRawObjectData());
    }

    @Override
    public byte getPacketID() {
        return DBPacketEntry.SyncJsonHandler.id;
    }

    @Override
    public void writePacket(DBPacketBuffer buffer, PacketSyncJsonHandler packet) {
        buffer.writeStringToBuffer(packet.resourceType);

        buffer.writeVarIntToBuffer(packet.data.size());

        for (Entry<String, JsonElement> entry : packet.data.entrySet()) {
            buffer.writeStringToBuffer(entry.getKey());
            buffer.writeStringToBuffer(DatapackSerialization.getGson().toJson(entry.getValue()));
        }
    }

    @Override
    public PacketSyncJsonHandler readPacket(DBPacketBuffer buffer) {
        String resourceType = buffer.readStringFromBuffer(1000);

        Map<String, JsonElement> data = new HashMap<>();

        int len = buffer.readVarIntFromBuffer();

        for (int i = 0; i < len; i++) {
            String id = buffer.readStringFromBuffer(1000);
            String value = buffer.readStringFromBuffer(32000);

            data.put(id, DatapackSerialization.getGson().fromJson(value, JsonElement.class));
        }

        return new PacketSyncJsonHandler(resourceType, data);
    }

    @Override
    public void process(World world, PacketSyncJsonHandler packet) {
        ((JsonDatapackTypeHandler<?>) DatapackHandlerRegistry.getTypeHandler(Arrays.asList(packet.resourceType.split("/")))).receive(packet.data);
    }
}
