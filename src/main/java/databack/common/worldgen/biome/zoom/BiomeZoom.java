package databack.common.worldgen.biome.zoom;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import databack.common.annotation.ThreadSafe;
import databack.common.worldgen.vanilla.VanillaClassLoader;

/// Vanilla 26.1's biome zoom (which quart cell's biome each block gets), obtained by running the vanilla classes that
/// perform it (downloaded from Mojang at runtime, see `VanillaDatapackDownloader`) against stand-ins. Nothing of
/// vanilla's is shipped or copied; only the classes' public entry points and the names they use are relied on.
@ThreadSafe
public final class BiomeZoom {

    /// Jar entry of the class that performs the zoom.
    public static final String MANAGER_CLASS = ZoomMappings.MANAGER + ".class";

    /// Jar entry of a helper class the zoom uses.
    public static final String LCG_CLASS = "net/minecraft/util/LinearCongruentialGenerator.class";

    @NotNull
    private final Method obfuscateSeed;
    @NotNull
    private final Constructor<?> constructor;

    private BiomeZoom(@NotNull Method obfuscateSeed, @NotNull Constructor<?> constructor) {
        this.obfuscateSeed = obfuscateSeed;
        this.constructor = constructor;
    }

    /// Loads the zoom from the two classes' files. Throws [IllegalStateException] when they cannot be loaded.
    @NotNull
    @Contract("_, _ -> new")
    public static BiomeZoom load(byte @NotNull [] managerClass, byte @NotNull [] lcgClass) {
        Map<String, byte[]> classFiles = new HashMap<>();
        classFiles.put(MANAGER_CLASS, managerClass);
        classFiles.put(LCG_CLASS, lcgClass);

        try {
            Class<?> manager = VanillaClassLoader.load(classFiles, ZoomMappings.MAPPINGS)
                .loadClass(VanillaClassLoader.binaryName(MANAGER_CLASS));

            return new BiomeZoom(manager.getMethod("obfuscateSeed", long.class),
                manager.getConstructor(ZoomSource.class, long.class));
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            throw new IllegalStateException("Could not load biome zoom from vanilla's classes", e);
        }
    }

    /// The zoom seed derived from a world seed.
    public long zoomSeed(long worldSeed) {
        try {
            return (Long) obfuscateSeed.invoke(null, worldSeed);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw failure("Biome zoom seed failed", e);
        }
    }

    /// Per-block biomes over `source` for the world whose zoom seed is `zoomSeed` (see [#zoomSeed]).
    @NotNull
    @Contract("_, _ -> new")
    public <T> ZoomedBiomes<T> zoom(@NotNull ZoomSource<T> source, long zoomSeed) {
        try {
            return new ZoomedBiomes<>((ZoomLookup) constructor.newInstance(source, zoomSeed));
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            throw failure("Biome zoom failed", e);
        }
    }

    private static IllegalStateException failure(String message, Exception e) {
        return new IllegalStateException(message, e instanceof InvocationTargetException ? e.getCause() : e);
    }
}
