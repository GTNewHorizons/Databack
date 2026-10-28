package databack.common.biome;

import java.io.File;

import net.minecraft.world.biome.BiomeGenBase;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.gtnewhorizon.gtnhlib.eventbus.EventBusSubscriber;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import databack.DatabackConfig;
import databack.common.handlers.BiomeList;
import databack.common.interop.ChunkBiomes;
import databack.common.interop.registry.ProxyBiomeRegistry;
import databack.common.loader.DatapackEvent.DatapackFinishedLoadingEvent;

/// Wires [ModernBiomeRegistry] and [VanillaBiomeAliases] into the mod's lifecycle.
@EventBusSubscriber
public final class ModernBiomeLifecycle {

    @Nullable
    private static ModernBiomeRegistry registry;

    private ModernBiomeLifecycle() {}

    /// Pre-init, after the config is loaded. Ids are kept in `<config>/databack/modern_biome_ids.json`.
    public static void init(@NotNull File configDir) {
        registry = new ModernBiomeRegistry(
            ModernBiomeIds.load(new File(configDir, "databack/modern_biome_ids.json")), ProxyBiomeRegistry.INSTANCE,
            BiomeGenBase.getBiomeGenArray(), ChunkBiomes.current().idCount() - 1, DatabackConfig.modernBiomesOnly);
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
}
