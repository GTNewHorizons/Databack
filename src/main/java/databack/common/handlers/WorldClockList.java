package databack.common.handlers;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.entity.player.EntityPlayerMP;

import org.jetbrains.annotations.NotNull;

import databack.common.interop.WorldClock;
import databack.common.interop.world_clock.DimensionWorldClock;
import databack.common.loader.ResourceId;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;

public class WorldClockList implements IDatapackTypeHandler {

    public static final WorldClockList INSTANCE = new WorldClockList();

    private final Map<String, WorldClock> clocks = new HashMap<>();

    @Override
    public void onLoadStart() {
        clocks.clear();

        clocks.put("minecraft:overworld", new DimensionWorldClock(0));
    }

    @Override
    public void handle(@NotNull ResourceId id, byte @NotNull [] content) {
        clocks.put(id.fqid(), new DimensionWorldClock(0));
    }

    @Override
    public void onWorldUnload() {
        clocks.clear();
    }

    @Override
    public void syncToPlayer(EntityPlayerMP player) {
        IDatapackTypeHandler.super.syncToPlayer(player);
    }

    private static class WorldClockState {
        public long counter;
    }
}
