package databack.common.worldgen.biome.preset;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import databack.common.worldgen.biome.ClimateEntry;
import databack.common.worldgen.vanilla.VanillaClassLoader;

/// Builds vanilla 26.1's built-in multi-noise parameter list presets by running the vanilla classes that build them
/// (downloaded from Mojang at runtime, see `VanillaDatapackDownloader`) against stand-ins. Nothing of vanilla's is
/// shipped or copied; only the classes' public entry points and the names they use are relied on.
public final class VanillaPresetExtractor {

    /// Jar entry of the overworld preset's builder.
    public static final String OVERWORLD_CLASS = "net/minecraft/world/level/biome/OverworldBiomeBuilder.class";

    /// Jar entry of the nether preset's provider.
    public static final String NETHER_CLASS =
        "net/minecraft/world/level/biome/MultiNoiseBiomeSourceParameterList$Preset$1.class";

    private VanillaPresetExtractor() {}

    /// The `minecraft:overworld` preset, in vanilla's list order. Throws [IllegalStateException] when the class
    /// cannot be run.
    @NotNull
    @Contract("_ -> new")
    public static List<ClimateEntry> overworld(byte @NotNull [] builderClass) {
        try {
            Class<?> builder = load(OVERWORLD_CLASS, builderClass);
            Object instance = builder.getConstructor().newInstance();

            Method addBiomes = builder.getDeclaredMethod("addBiomes", Consumer.class);
            addBiomes.setAccessible(true);

            List<ClimateEntry> entries = new ArrayList<>();
            Consumer<Object> collector = pair -> entries.add(toEntry(pair));
            addBiomes.invoke(instance, collector);

            return Collections.unmodifiableList(entries);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            throw failure("minecraft:overworld", e);
        }
    }

    /// The `minecraft:nether` preset, in vanilla's list order. Throws [IllegalStateException] when the class cannot
    /// be run.
    @NotNull
    @Contract("_ -> new")
    public static List<ClimateEntry> nether(byte @NotNull [] providerClass) {
        try {
            Constructor<?> constructor = load(NETHER_CLASS, providerClass).getDeclaredConstructor();
            constructor.setAccessible(true);

            PresetSourceProvider provider = (PresetSourceProvider) constructor.newInstance();
            PresetParameterList list = provider.apply(Function.identity());

            List<ClimateEntry> entries = new ArrayList<>();
            for (Object pair : list.values()) entries.add(toEntry(pair));

            return Collections.unmodifiableList(entries);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            throw failure("minecraft:nether", e);
        }
    }

    private static Class<?> load(String entry, byte[] classFile) throws ClassNotFoundException {
        return VanillaClassLoader.load(Collections.singletonMap(entry, classFile), PresetMappings.MAPPINGS)
            .loadClass(VanillaClassLoader.binaryName(entry));
    }

    private static ClimateEntry toEntry(Object pair) {
        PresetPair p = (PresetPair) pair;
        return ((PresetPoint) p.first()).toEntry((String) p.second());
    }

    private static IllegalStateException failure(String preset, Throwable e) {
        Throwable cause = e instanceof InvocationTargetException ? e.getCause() : e;
        return new IllegalStateException("Could not build biome preset " + preset + " from vanilla's classes", cause);
    }
}
