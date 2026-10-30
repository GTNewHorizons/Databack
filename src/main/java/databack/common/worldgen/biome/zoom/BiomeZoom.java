package databack.common.worldgen.biome.zoom;

import java.util.HashMap;
import java.util.Map;
import java.util.function.LongUnaryOperator;

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

    /// Builds the zoom's lookup: a bridge to the vanilla class's constructor.
    @FunctionalInterface
    public interface LookupFactory {

        @NotNull
        ZoomLookup create(@NotNull ZoomSource<?> source, long zoomSeed);
    }

    /// A bridge to the vanilla class's static seed function.
    @NotNull
    private final LongUnaryOperator obfuscateSeed;
    @NotNull
    private final LookupFactory lookups;

    private BiomeZoom(@NotNull LongUnaryOperator obfuscateSeed, @NotNull LookupFactory lookups) {
        this.obfuscateSeed = obfuscateSeed;
        this.lookups = lookups;
    }

    /// Loads the zoom from the two classes' files. Throws [IllegalStateException] when they cannot be loaded.
    @NotNull
    @Contract("_, _ -> new")
    public static BiomeZoom load(byte @NotNull [] managerClass, byte @NotNull [] lcgClass) {
        Map<String, byte[]> classFiles = new HashMap<>();
        classFiles.put(MANAGER_CLASS, managerClass);
        classFiles.put(LCG_CLASS, lcgClass);

        try {
            VanillaClassLoader loader = VanillaClassLoader.load(classFiles, ZoomMappings.MAPPINGS);
            String manager = VanillaClassLoader.binaryName(MANAGER_CLASS);

            return new BiomeZoom(loader.bridge(LongUnaryOperator.class, manager, "obfuscateSeed"),
                loader.bridge(LookupFactory.class, manager, "<init>"));
        } catch (LinkageError | RuntimeException e) {
            throw new IllegalStateException("Could not load biome zoom from vanilla's classes", e);
        }
    }

    /// The zoom seed derived from a world seed.
    public long zoomSeed(long worldSeed) {
        return obfuscateSeed.applyAsLong(worldSeed);
    }

    /// Per-block biomes over `source` for the world whose zoom seed is `zoomSeed` (see [#zoomSeed]).
    @NotNull
    @Contract("_, _ -> new")
    public <T> ZoomedBiomes<T> zoom(@NotNull ZoomSource<T> source, long zoomSeed) {
        return new ZoomedBiomes<>(lookups.create(source, zoomSeed));
    }
}
