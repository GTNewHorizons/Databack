package databack.test.support;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;

import net.minecraft.block.Block;
import net.minecraft.item.Item;

import cpw.mods.fml.common.Loader;
import sun.misc.Unsafe;

import databack.common.interop.VanillaBlockIdentities;
import databack.common.interop.registry.BlockIdentityRegistry;

/// Registers 1.7.10's blocks and items, and Databack's vanilla block identities, outside the game, so tests can use
/// [net.minecraft.init.Blocks], block metas and identities. Applies the member-only mixins first ([TestMixins]).
/// Every test JVM runs it first ([MinecraftTestSession]); benchmarks call [#init] themselves.
///
/// Forge's registry asks [Loader] for the active mod, and a real Loader only works under LaunchWrapper's class
/// loader, so an empty one (no mods, no controller) is installed: registrations then take the `minecraft` prefix,
/// and [Loader#isModLoaded] answers false.
public final class MinecraftBootstrap {

    private static boolean done;

    private MinecraftBootstrap() {}

    /// Idempotent. Must run before anything loads a mixin target or initializes [net.minecraft.init.Blocks].
    public static synchronized void init() {
        if (done) return;
        done = true;

        TestMixins.install();
        installEmptyLoader();
        Block.registerBlocks();
        Item.registerItems();

        VanillaBlockIdentities.init();
        BlockIdentityRegistry.INSTANCE.claimUnrenamedVanilla();
    }

    private static void installEmptyLoader() {
        try {
            Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            Loader loader = (Loader) ((Unsafe) unsafeField.get(null)).allocateInstance(Loader.class);

            set(loader, "mods", new ArrayList<>());
            set(loader, "namedMods", new HashMap<>());
            set(null, "instance", loader);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not install a test Loader", e);
        }
    }

    private static void set(Loader owner, String name, Object value) throws ReflectiveOperationException {
        Field field = Loader.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(owner, value);
    }
}
