package databack.common.biome;

import java.io.File;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import net.minecraft.world.biome.BiomeGenBase;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.gtnewhorizon.gtnhlib.eventbus.EventBusSubscriber;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import databack.DatabackConfig;
import databack.common.dto.worldgen.biome.DatapackBiome;
import databack.common.handlers.BiomeList;
import databack.common.interop.ChunkBiomes;
import databack.common.interop.registry.ProxyBiomeRegistry;
import databack.common.loader.DatapackEvent.DatapackFinishedLoadingEvent;
import databack.common.loader.DatapackEvent.DatapackSyncEvent;
import databack.common.loader.DatapackLoader;
import databack.common.network.PacketEncoderSyncModernBiomes.PacketSyncModernBiomes;

/// Wires [ModernBiomeRegistry] and [VanillaBiomeAliases] into the mod's lifecycle: the server activates biomes when
/// datapacks load and sends them to remote clients, which mirror them.
@EventBusSubscriber
public final class ModernBiomeLifecycle {

    private static final Logger LOGGER = LogManager.getLogger("databack-biomes");

    @Nullable
    private static ModernBiomeRegistry registry;

    /// The latest biome set received from a server, waiting for the client thread.
    private static final AtomicReference<Map<String, Integer>> RECEIVED = new AtomicReference<>();

    private ModernBiomeLifecycle() {}

    /// Pre-init, after the config is loaded. Ids are kept in `<config>/databack/modern_biome_ids.json`.
    public static void init(@NotNull File configDir) {
        registry = new ModernBiomeRegistry(
            ModernBiomeIds.load(new File(configDir, "databack/modern_biome_ids.json")), ProxyBiomeRegistry.INSTANCE,
            BiomeGenBase.getBiomeGenArray(), ChunkBiomes.current().idCount() - 1, DatabackConfig.modernBiomesOnly,
            ModernBiomeLifecycle::configure);
    }

    /// Post-init, once every 1.7 biome is registered: in alias mode, 1.7 biomes take their modern names.
    public static void applyAliases() {
        if (!DatabackConfig.modernBiomesOnly) VanillaBiomeAliases.apply(ProxyBiomeRegistry.INSTANCE);
    }

    /// Runs before anything else reacting to the load (biome tags, in particular) so they see the new biomes.
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onDatapacksLoaded(DatapackFinishedLoadingEvent event) {
        if (registry != null) registry.activate(BiomeList.RT.getHandler().getRawObjectData().keySet());
    }

    /// Sent after the datapack data, so the client has the biomes' JSON when the ids arrive. Clients of an integrated
    /// server share its biome list and need nothing.
    @SubscribeEvent
    public static void onSync(DatapackSyncEvent event) {
        if (registry == null || DatapackLoader.isIntegratedServer()) return;
        new PacketSyncModernBiomes(registry.activeIds()).sendToPlayer(event.player);
    }

    /// Network thread: queues a server's biome set for the client thread (see [#onClientTick]).
    public static void receiveFromServer(@NotNull Map<String, Integer> biomes) {
        RECEIVED.set(biomes);
    }

    /// The biome list is read by the client thread while rendering, so it is only changed there.
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START || registry == null) return;

        Map<String, Integer> biomes = RECEIVED.getAndSet(null);
        if (biomes != null) registry.synchronize(biomes);
    }

    private static void configure(ModernBiome biome) {
        String id = biome.getDatapackId();
        DatapackBiome definition = BiomeList.RT.getHandler().getBiome(id);
        if (definition == null) LOGGER.warn("Biome {} has no datapack definition; it uses default properties", id);
        biome.setProperties(ModernBiomeProperties.of(id, definition));
    }
}
