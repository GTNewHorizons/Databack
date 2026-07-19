package databack.common.handlers;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import net.minecraft.entity.player.EntityPlayerMP;

import org.jetbrains.annotations.NotNull;

import com.google.gson.JsonElement;
import databack.common.loader.DatapackLoader;
import databack.common.loader.ResourceId;
import databack.common.network.PacketEncoderSyncJsonHandler;
import databack.common.serde.DatapackSerialization;
import lombok.Getter;

/// A [IDatapackTypeHandler] whose resources are json objects of a specific type.
/// All received objects will be kept in a list, until the world is unloaded.
/// It is assumed that [T] is immutable, as this object may be accessed from the client or server thread arbitrarily.
public class JsonDatapackTypeHandler<T> implements IDatapackTypeHandler {

    @Getter
    private final String resourceType;
    private final Class<T> type;
    private final Map<String, T> objects = new HashMap<>();
    @Getter
    private final Map<String, JsonElement> rawObjectData = new HashMap<>();

    protected JsonDatapackTypeHandler(ResourceType<?> resourceType, Class<T> type) {
        this.resourceType = resourceType.getResourcePath();
        this.type = type;
    }

    protected JsonDatapackTypeHandler(String resourceType, Class<T> type) {
        this.resourceType = resourceType;
        this.type = type;
    }

    @Override
    public void handle(@NotNull ResourceId id, byte @NotNull [] content) {
        JsonElement tree = DatapackSerialization.getGson().fromJson(new String(content, StandardCharsets.UTF_8), JsonElement.class);

        T obj = DatapackSerialization.getGson().fromJson(tree, type);

        objects.put(id.fqid(), obj);
        rawObjectData.put(id.fqid(), tree);
    }

    @Override
    public void onLoadStart() {
        objects.clear();
        rawObjectData.clear();
    }

    @Override
    public void onWorldUnload() {
        objects.clear();
        rawObjectData.clear();
    }

    protected T getObject(String name) {
        return objects.get(name);
    }

    protected boolean doSync() {
        return true;
    }

    @Override
    public void syncToPlayer(EntityPlayerMP player) {
        if (!doSync()) return;

        PacketEncoderSyncJsonHandler.createPacket(this).sendToPlayer(player);
    }

    public void receive(Map<String, JsonElement> data) {
        rawObjectData.clear();
        objects.clear();

        rawObjectData.putAll(data);

        rawObjectData.forEach((id, value) -> {
            T obj = DatapackSerialization.getGson().fromJson(value, type);

            objects.put(id, obj);
        });

        DatapackLoader.LOGGER.info("Received {} objects for the {} JsonDatapackTypeHandler", objects.size(), this.resourceType);
    }
}
