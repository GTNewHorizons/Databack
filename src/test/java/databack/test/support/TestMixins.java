package databack.test.support;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.security.ProtectionDomain;

import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.transformer.IMixinTransformer;

import cpw.mods.fml.relauncher.FMLLaunchHandler;
import cpw.mods.fml.relauncher.Side;
import net.bytebuddy.agent.ByteBuddyAgent;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;

/// Runs Mixin in tests, with Databack's early mixins (`mixins.databack.test.json`): Mixin's own transformer, driven
/// by a load-time agent instead of LaunchWrapper. The service glue is [TestMixinService] and [TestGlobalProperties].
///
/// The side is the server's, so client-only mixins are left out. Assumption: installed before any mixin target
/// loads.
final class TestMixins {

    private static final String CONFIG = "mixins.databack.test.json";

    private TestMixins() {}

    static void install() {
        System.setProperty("mixin.service", TestMixinService.class.getName());
        System.setProperty("mixin.bootstrapService", TestMixinService.Bootstrap.class.getName());
        setSide(Side.SERVER);
        // GTNHMixins sanity-checks LaunchWrapper's sources when it first logs; an empty loader has none.
        Launch.classLoader = new LaunchClassLoader(new URL[0]);

        MixinBootstrap.init();
        org.spongepowered.asm.mixin.Mixins.addConfiguration(CONFIG);
        enterDefaultPhase();
        IMixinTransformer transformer = TestMixinService.transformerFactory().createTransformer();

        Instrumentation instrumentation = ByteBuddyAgent.install();
        checkNothingLoaded(instrumentation);
        instrumentation.addTransformer(new Agent(transformer));
    }

    /// Mixins can't change classes already loaded. Minecraft's own classes are the targets; LaunchWrapper's are not.
    private static void checkNothingLoaded(Instrumentation instrumentation) {
        for (Class<?> loaded : instrumentation.getAllLoadedClasses()) {
            String name = loaded.getName();
            if (name.startsWith("net.minecraft.") && !name.startsWith("net.minecraft.launchwrapper.")) {
                throw new IllegalStateException(name + " was loaded before the test mixins were installed");
            }
        }
    }

    /// Steps the environment from pre-init to default, as the game's launch does (Databack's mixins are early, applied
    /// in the default phase). Mixin keeps the step to itself; Fabric's launcher takes it the same way.
    private static void enterDefaultPhase() {
        try {
            Method gotoPhase = MixinEnvironment.class.getDeclaredMethod("gotoPhase", MixinEnvironment.Phase.class);
            gotoPhase.setAccessible(true);
            gotoPhase.invoke(null, MixinEnvironment.Phase.INIT);
            gotoPhase.invoke(null, MixinEnvironment.Phase.DEFAULT);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not enter Mixin's default phase", e);
        }
    }

    /// GTNHMixins picks sided mixins by [FMLLaunchHandler#side], which FML's launch sets.
    private static void setSide(Side side) {
        try {
            Field field = FMLLaunchHandler.class.getDeclaredField("side");
            field.setAccessible(true);
            field.set(null, side);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not set FML's side", e);
        }
    }

    private static final class Agent implements ClassFileTransformer {

        private final IMixinTransformer transformer;

        Agent(IMixinTransformer transformer) {
            this.transformer = transformer;
        }

        @Override
        public byte[] transform(
            ClassLoader loader, String className, Class<?> redefined, ProtectionDomain domain, byte[] bytes
        ) {
            if (className == null || redefined != null) return null;

            try {
                String name = className.replace('/', '.');
                byte[] transformed = transformer.transformClassBytes(name, name, bytes);
                return transformed == bytes ? null : transformed;
            } catch (Throwable t) {
                // The JVM swallows exceptions thrown here; make them visible.
                t.printStackTrace();
                throw t;
            }
        }
    }
}
