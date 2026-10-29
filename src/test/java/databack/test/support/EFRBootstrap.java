package databack.test.support;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;

import net.minecraft.block.Block;
import net.minecraft.launchwrapper.Launch;

import cpw.mods.fml.common.registry.GameData;
import cpw.mods.fml.relauncher.FMLInjectionData;
import cpw.mods.fml.relauncher.FMLRelaunchLog;
import cpw.mods.fml.relauncher.Side;

import ganymedes01.etfuturum.ModBlocks;
import ganymedes01.etfuturum.configuration.ConfigBase;

/// Registers Et Futurum Requiem's blocks without FML: its configs at their defaults (in a scratch directory), and
/// each enabled block under `etfuturum:` straight into the block registry, as `GameRegistry.registerBlock` would.
/// EFR's own mixins are not applied.
final class EFRBootstrap {

    private EFRBootstrap() {}

    /// Requires vanilla's blocks to be registered.
    static void registerBlocks() {
        File home = new File("build/tmp/efr-test-home").getAbsoluteFile();
        home.mkdirs();
        Launch.minecraftHome = home;
        if (Launch.blackboard == null) Launch.blackboard = new HashMap<>();
        Launch.blackboard.putIfAbsent("fml.deobfuscatedEnvironment", true);

        try {
            // Forge's configs find the game directory here; FML's launch fills it in.
            Field injected = FMLInjectionData.class.getDeclaredField("minecraftHome");
            injected.setAccessible(true);
            injected.set(null, home);
            ConfigBase.initializeConfigs();

            // Registration logs through FML, which needs the side the launch would set.
            Field side = FMLRelaunchLog.class.getDeclaredField("side");
            side.setAccessible(true);
            side.set(null, Side.SERVER);

            Method getMain = GameData.class.getDeclaredMethod("getMain");
            getMain.setAccessible(true);
            // The overload with an id hint keeps the name's prefix; the others replace it with the active mod's, and
            // tests have none. A hint of -1 takes the next free id.
            Method registerBlock = GameData.class.getDeclaredMethod("registerBlock", Block.class, String.class,
                int.class);
            registerBlock.setAccessible(true);
            Object data = getMain.invoke(null);

            for (ModBlocks block : ModBlocks.VALUES) {
                if (!block.isEnabled()) continue;
                registerBlock.invoke(data, block.get(), "etfuturum:" + block.name().toLowerCase(), -1);
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not register Et Futurum Requiem's blocks", e);
        }
    }
}
