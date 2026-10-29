package databack.common.worldgen.carver;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import databack.common.annotation.ThreadSafe;
import databack.common.worldgen.carver.standin.CvCarver;
import databack.common.worldgen.vanilla.VanillaClassLoader;

/// Vanilla 26.1's carvers, obtained by running the vanilla classes that implement them (downloaded from Mojang at
/// runtime, see `VanillaDatapackDownloader`) against stand-ins. Nothing of vanilla's is shipped or copied; only the
/// classes' carver constants and the names they use are relied on.
@ThreadSafe
public final class VanillaCarvers {

    /// Jar entries of the carver classes.
    public static final List<String> CLASSES = Collections.unmodifiableList(Arrays.asList(
        CarverMappings.CARVER + "WorldCarver.class", CarverMappings.CARVER + "WorldCarver$CarveSkipChecker.class",
        CarverMappings.CARVER + "CaveWorldCarver.class", CarverMappings.CARVER + "CanyonWorldCarver.class",
        CarverMappings.CARVER + "NetherWorldCarver.class"));

    /// The carver types, by datapack name.
    @NotNull
    public final CvCarver cave, netherCave, canyon;

    private VanillaCarvers(@NotNull CvCarver cave, @NotNull CvCarver netherCave, @NotNull CvCarver canyon) {
        this.cave = cave;
        this.netherCave = netherCave;
        this.canyon = canyon;
    }

    /// `classFile` gives each of [#CLASSES]. Throws [IllegalStateException] when the classes cannot be loaded.
    @NotNull
    @Contract("_ -> new")
    public static VanillaCarvers load(@NotNull Function<String, byte[]> classFile) {
        try {
            Map<String, byte[]> files = new java.util.HashMap<>();
            for (String entry : CLASSES) files.put(entry, classFile.apply(entry));

            Class<?> carver = VanillaClassLoader.load(files, CarverMappings.MAPPINGS)
                .loadClass(VanillaClassLoader.binaryName(CLASSES.get(0)));
            return new VanillaCarvers(constant(carver, "CAVE"), constant(carver, "NETHER_CAVE"),
                constant(carver, "CANYON"));
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            throw new IllegalStateException("Could not load the carvers from vanilla's classes", e);
        }
    }

    private static CvCarver constant(Class<?> carver, String name) throws ReflectiveOperationException {
        return (CvCarver) carver.getField(name).get(null);
    }
}
