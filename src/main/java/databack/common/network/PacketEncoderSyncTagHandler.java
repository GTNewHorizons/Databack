package databack.common.network;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;

import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;

import com.github.bsideup.jabel.Desugar;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.SetMultimap;
import databack.common.dto.tag.TagEntry;
import databack.common.handlers.DatapackHandlerRegistry;
import databack.common.handlers.IDatapackTypeHandler;
import databack.common.network.PacketEncoderSyncTagHandler.PacketSyncTagHandler;
import databack.common.tags.ITagStagingReceiver;

public class PacketEncoderSyncTagHandler extends DBPacketEncoder<PacketSyncTagHandler> {

    @Desugar
    public record PacketSyncTagHandler(String resourceType, SetMultimap<ResourceLocation, TagEntry> staging)
        implements DBPacket {

        @Override
        public byte getPacketID() {
            return DBPacketEntry.SyncTagHandler.id;
        }
    }

    public static PacketSyncTagHandler createPacket(String resourceType, SetMultimap<ResourceLocation, TagEntry> staging) {
        SetMultimap<ResourceLocation, TagEntry> snapshot = MultimapBuilder.hashKeys().hashSetValues().build();
        snapshot.putAll(staging);
        return new PacketSyncTagHandler(resourceType, snapshot);
    }

    @Override
    public byte getPacketID() {
        return DBPacketEntry.SyncTagHandler.id;
    }

    @Override
    public void writePacket(DBPacketBuffer buffer, PacketSyncTagHandler packet) {
        buffer.writeStringToBuffer(packet.resourceType());

        Map<ResourceLocation, Collection<TagEntry>> map = packet.staging().asMap();
        buffer.writeVarIntToBuffer(map.size());

        for (Map.Entry<ResourceLocation, Collection<TagEntry>> e : map.entrySet()) {
            buffer.writeStringToBuffer(e.getKey().toString());
            buffer.writeList(new ArrayList<>(e.getValue()), (buf, entry) -> {
                buf.writeStringToBuffer(entry.id);
                buf.writeBoolean(entry.isTagRef);
                buf.writeBoolean(entry.required);
            });
        }
    }

    @Override
    public PacketSyncTagHandler readPacket(DBPacketBuffer buffer) {
        String resourceType = buffer.readStringFromBuffer(1000);

        SetMultimap<ResourceLocation, TagEntry> staging = MultimapBuilder.hashKeys().hashSetValues().build();

        int keyCount = buffer.readVarIntFromBuffer();

        for (int i = 0; i < keyCount; i++) {
            ResourceLocation key = new ResourceLocation(buffer.readStringFromBuffer(1000));
            staging.putAll(key, buffer.readList(buf -> new TagEntry(buf.readStringFromBuffer(1000), buf.readBoolean(), buf.readBoolean())));
        }

        return new PacketSyncTagHandler(resourceType, staging);
    }

    @Override
    public void process(World world, PacketSyncTagHandler packet) {
        IDatapackTypeHandler handler = DatapackHandlerRegistry.getTypeHandler(
            Arrays.asList(packet.resourceType().split("/")));
        ((ITagStagingReceiver) handler).receiveTagStaging(packet.staging());
    }
}
