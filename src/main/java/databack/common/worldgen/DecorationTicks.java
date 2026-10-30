package databack.common.worldgen;

import net.minecraft.world.gen.ChunkProviderServer;

import com.gtnewhorizon.gtnhlib.eventbus.EventBusSubscriber;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

import databack.DatabackConfig;

/// Decorates each Databack world's queued chunks a few at a time, after the world ticks
/// ([DatabackConfig#decorationsPerTick]).
@EventBusSubscriber
public final class DecorationTicks {

    private DecorationTicks() {}

    @SubscribeEvent
    public static void onWorldTick(TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.world.isRemote) return;
        if (!(event.world.getChunkProvider() instanceof ChunkProviderServer server)) return;
        if (!(server.currentChunkProvider instanceof ModernWorldGenerator generator)) return;

        generator.tickDecoration(DatabackConfig.decorationsPerTick);
    }
}
