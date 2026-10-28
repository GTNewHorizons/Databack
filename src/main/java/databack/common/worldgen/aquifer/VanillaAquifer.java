package databack.common.worldgen.aquifer;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Collections;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import databack.common.annotation.ThreadSafe;
import databack.common.worldgen.aquifer.standin.AqAquifer;
import databack.common.worldgen.aquifer.standin.AqChunkPos;
import databack.common.worldgen.aquifer.standin.AqFluidPicker;
import databack.common.worldgen.aquifer.standin.AqNoiseChunk;
import databack.common.worldgen.aquifer.standin.AqRandomFactory;
import databack.common.worldgen.aquifer.standin.AqRouter;
import databack.common.worldgen.vanilla.VanillaClassLoader;

/// Vanilla 26.1's aquifer, obtained by running the vanilla class that implements it (downloaded from Mojang at
/// runtime, see `VanillaDatapackDownloader`) against stand-ins. Nothing of vanilla's is shipped or copied; only the
/// class's constructor and the names it uses are relied on.
@ThreadSafe
public final class VanillaAquifer {

    /// Jar entry of the aquifer class.
    public static final String CLASS = "net/minecraft/world/level/levelgen/Aquifer$NoiseBasedAquifer.class";

    @NotNull
    private final Constructor<?> constructor;

    private VanillaAquifer(@NotNull Constructor<?> constructor) {
        this.constructor = constructor;
    }

    /// Throws [IllegalStateException] when the class cannot be loaded.
    @NotNull
    @Contract("_ -> new")
    public static VanillaAquifer load(byte @NotNull [] classFile) {
        try {
            Class<?> aquifer = VanillaClassLoader.load(Collections.singletonMap(CLASS, classFile),
                AquiferMappings.MAPPINGS).loadClass(VanillaClassLoader.binaryName(CLASS));
            Constructor<?> constructor = aquifer.getDeclaredConstructor(AqNoiseChunk.class, AqChunkPos.class,
                AqRouter.class, AqRandomFactory.class, int.class, int.class, AqFluidPicker.class);
            constructor.setAccessible(true);
            return new VanillaAquifer(constructor);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            throw new IllegalStateException("Could not load the aquifer from vanilla's classes", e);
        }
    }

    /// The aquifer for one chunk, over `height` layers from `minY`. Not thread-safe; use one per chunk.
    @NotNull
    public AqAquifer create(
        @NotNull AqNoiseChunk surface, @NotNull AqChunkPos chunk, @NotNull AqRouter router,
        @NotNull AqRandomFactory random, int minY, int height, @NotNull AqFluidPicker globalFluid
    ) {
        try {
            return (AqAquifer) constructor.newInstance(surface, chunk, router, random, minY, height, globalFluid);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            throw new IllegalStateException("Aquifer creation failed",
                e instanceof InvocationTargetException ? e.getCause() : e);
        }
    }
}
