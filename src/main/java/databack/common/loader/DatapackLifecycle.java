package databack.common.loader;

import java.io.File;

import javax.annotation.Nonnull;

import net.minecraft.client.Minecraft;
import net.minecraft.crash.CrashReport;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ReportedException;
import net.minecraftforge.event.world.WorldEvent;

import com.gtnewhorizon.gtnhlib.eventbus.EventBusSubscriber;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent.PlayerLoggedInEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import databack.Databack;

/**
 * Forge event listener that drives the datapack loading pipeline lifecycle.
 *
 * <p>Registers for {@link WorldEvent.Load} and {@link WorldEvent.Unload}. Both handlers apply two
 * guards: the world must be server-side ({@code isRemote == false}) and must be the overworld
 * ({@code dimensionId == 0}).
 */
@EventBusSubscriber
public class DatapackLifecycle {

    /**
     * Triggered when a world is loaded. Calls {@link DatapackLoader#load} for the overworld.
     * On failure, halts world initialisation: returns to main menu on an integrated (singleplayer)
     * server, or hard-crashes the process on a dedicated server.
     */
    @SubscribeEvent
    public static void onWorldLoad(@Nonnull WorldEvent.Load event) {
        // Guard 1: server-side only
        if (event.world.isRemote) {
            return;
        }
        // Guard 2: overworld only
        if (event.world.provider.dimensionId != 0) {
            return;
        }

        File worldSaveDir = event.world.getSaveHandler().getWorldDirectory();

        try {
            DatapackLoader.load(worldSaveDir, event.world);
        } catch (DatapackLoadException e) {
            Databack.LOGGER.error("Fatal error loading datapacks: {}", e.getMessage(), e);

            CrashReport crashReport = new CrashReport("Datapack loading failed", e);

            if (FMLCommonHandler.instance().getSide() == Side.CLIENT) {
                // Integrated (singleplayer) server — return to main menu cleanly
                bailToMainMenu();
            } else {
                // Dedicated server — hard crash
                throw new ReportedException(crashReport);
            }
        }
    }

    /**
     * Triggered when a player joins a SP or MP world. Syncs any datapack data to them as needed.
     */
    @SubscribeEvent
    public static void onPlayerJoin(@Nonnull PlayerLoggedInEvent event) {
        DatapackLoader.syncToPlayer((EntityPlayerMP) event.player);
    }

    /**
     * Triggered when a world is unloaded. Calls {@link DatapackLoader#unload} for the overworld.
     */
    @SubscribeEvent
    public static void onWorldUnload(@Nonnull WorldEvent.Unload event) {
        // Guard 1: server-side only
        if (event.world.isRemote) {
            return;
        }
        // Guard 2: overworld only
        if (event.world.provider.dimensionId != 0) {
            return;
        }

        DatapackLoader.unload();
    }

    /**
     * Returns the client to the main menu by unloading the current world.
     * Must only be called when running on the physical client side
     * ({@code FMLCommonHandler.instance().getSide() == Side.CLIENT}).
     */
    @SideOnly(Side.CLIENT)
    private static void bailToMainMenu() {
        Minecraft.getMinecraft().loadWorld(null);
    }
}
