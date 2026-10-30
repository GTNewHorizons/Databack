package databack.common.worldgen.feature;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import databack.common.annotation.ThreadSafe;
import databack.common.worldgen.feature.standin.FtChunkGenerator;
import databack.common.worldgen.feature.standin.FtLevel;
import databack.common.worldgen.rng.RandomSource;
import databack.common.worldgen.vanilla.VanillaClassLoader;
import databack.common.worldgen.vanilla.standin.VnBlockPos;

/// Vanilla 26.1's features and placement modifiers, obtained by running the vanilla classes that implement them
/// (downloaded from Mojang at runtime, see `VanillaDatapackDownloader`) against stand-ins. Nothing of vanilla's is
/// shipped or copied; only the classes' public constructors, factories and constants, and the names they use, are
/// relied on.
@ThreadSafe
public final class VanillaFeatures {

    static final String LEVELGEN = "net/minecraft/world/level/levelgen/";

    /// Jar entries of the classes run.
    public static final List<String> CLASSES = Collections.unmodifiableList(entries());

    private static List<String> entries() {
        List<String> entries = new ArrayList<>();
        add(entries, "feature/", "ConfiguredFeature", "DiskFeature", "Feature", "FeaturePlaceContext", "NoOpFeature",
            "OreFeature", "RandomBooleanSelectorFeature", "RandomSelectorFeature", "SimpleBlockFeature",
            "SimpleRandomSelectorFeature", "WeightedPlacedFeature");
        add(entries, "feature/configurations/", "DiskConfiguration", "FeatureConfiguration",
            "NoneFeatureConfiguration", "OreConfiguration", "OreConfiguration$TargetBlockState",
            "RandomBooleanFeatureConfiguration", "RandomFeatureConfiguration", "SimpleBlockConfiguration",
            "SimpleRandomFeatureConfiguration");
        add(entries, "feature/stateproviders/", "BlockStateProvider", "DualNoiseProvider", "NoiseBasedStateProvider",
            "NoiseProvider", "NoiseThresholdProvider", "RandomizedIntStateProvider", "RotatedBlockProvider",
            "RuleBasedStateProvider", "RuleBasedStateProvider$Builder", "RuleBasedStateProvider$Rule",
            "SimpleStateProvider", "WeightedStateProvider");
        add(entries, "blockpredicates/", "AllOfPredicate", "AnyOfPredicate", "BlockPredicate", "CombiningPredicate",
            "HasSturdyFacePredicate", "InsideWorldBoundsPredicate", "MatchingBlocksPredicate",
            "MatchingBlockTagPredicate", "MatchingFluidsPredicate", "NotPredicate", "ReplaceablePredicate",
            "SolidPredicate", "StateTestingPredicate", "TrueBlockPredicate", "UnobstructedPredicate",
            "WouldSurvivePredicate");
        add(entries, "placement/", "BiomeFilter", "BlockPredicateFilter", "CaveSurface", "CountOnEveryLayerPlacement",
            "CountPlacement", "EnvironmentScanPlacement", "FixedPlacement", "HeightmapPlacement",
            "HeightRangePlacement", "InSquarePlacement", "NoiseBasedCountPlacement", "NoiseThresholdCountPlacement",
            "PlacedFeature", "PlacementContext", "PlacementFilter", "PlacementModifier", "RandomOffsetPlacement",
            "RarityFilter", "RepeatingPlacement", "SurfaceRelativeThresholdFilter", "SurfaceWaterDepthFilter");
        add(entries, "feature/", "FallenTreeFeature", "configurations/FallenTreeConfiguration");
        add(entries, "feature/", "SnowAndFreezeFeature", "SpringFeature", "configurations/SpringConfiguration");
        add(entries, "feature/", "TreeFeature", "TreeFeature$1", "configurations/TreeConfiguration",
            "configurations/TreeConfiguration$TreeConfigurationBuilder");
        add(entries, "feature/trunkplacers/", "TrunkPlacer", "StraightTrunkPlacer", "FancyTrunkPlacer",
            "FancyTrunkPlacer$FoliageCoords", "DarkOakTrunkPlacer", "UpwardsBranchingTrunkPlacer", "GiantTrunkPlacer",
            "CherryTrunkPlacer", "MegaJungleTrunkPlacer", "ForkingTrunkPlacer", "BendingTrunkPlacer");
        add(entries, "feature/foliageplacers/", "FoliagePlacer", "FoliagePlacer$FoliageAttachment",
            "FoliagePlacer$FoliageSetter", "BlobFoliagePlacer", "FancyFoliagePlacer", "DarkOakFoliagePlacer",
            "RandomSpreadFoliagePlacer", "MegaPineFoliagePlacer", "CherryFoliagePlacer", "SpruceFoliagePlacer",
            "PineFoliagePlacer", "MegaJungleFoliagePlacer", "BushFoliagePlacer", "AcaciaFoliagePlacer");
        add(entries, "feature/featuresize/", "FeatureSize", "TwoLayersFeatureSize", "ThreeLayersFeatureSize");
        add(entries, "feature/rootplacers/", "RootPlacer", "MangroveRootPlacer", "MangroveRootPlacement",
            "AboveRootPlacement");
        add(entries, "feature/treedecorators/", "TreeDecorator", "TreeDecorator$Context", "AlterGroundDecorator",
            "AttachedToLeavesDecorator", "AttachedToLogsDecorator", "BeehiveDecorator", "CocoaDecorator",
            "CreakingHeartDecorator", "LeaveVineDecorator", "PaleMossDecorator", "PlaceOnGroundDecorator",
            "TrunkVineDecorator");
        add(entries, "structure/templatesystem/", "AlwaysTrueTest", "BlockMatchTest", "BlockStateMatchTest",
            "RandomBlockMatchTest", "RandomBlockStateMatchTest", "RuleTest", "TagMatchTest");
        return entries;
    }

    private static void add(List<String> entries, String pkg, String... names) {
        for (String name : names) entries.add(LEVELGEN + pkg + name + ".class");
    }

    @NotNull
    private final ClassLoader loader;

    private VanillaFeatures(@NotNull ClassLoader loader) {
        this.loader = loader;
    }

    /// `classFile` gives each of [#CLASSES]. Throws [IllegalStateException] when the classes cannot be loaded.
    @NotNull
    @Contract("_ -> new")
    public static VanillaFeatures load(@NotNull Function<String, byte[]> classFile) {
        try {
            Map<String, byte[]> files = new HashMap<>();
            for (String entry : CLASSES) files.put(entry, classFile.apply(entry));

            VanillaFeatures features = new VanillaFeatures(VanillaClassLoader.load(files, FeatureMappings.MAPPINGS));
            features.vanillaClass("feature/Feature");
            return features;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            throw new IllegalStateException("Could not load the features from vanilla's classes", e);
        }
    }

    /// Loads the rewritten classes, and through its parent everything they use.
    @NotNull
    ClassLoader classLoader() {
        return loader;
    }

    /// The rewritten vanilla class `name`, relative to the levelgen package (`feature/Feature`). Initialized.
    @NotNull
    public Class<?> vanillaClass(@NotNull String name) throws ClassNotFoundException {
        return Class.forName(VanillaClassLoader.binaryName(LEVELGEN + name + ".class"), true, loader);
    }

    /// A new instance of the vanilla class `name` (as for [#vanillaClass]), through a constructor whose parameters
    /// accept `args`. Throws [IllegalArgumentException] when none does, or it throws.
    @NotNull
    public Object construct(@NotNull String name, Object @NotNull ... args) {
        try {
            // Declared, not public: some datapack-built classes have protected constructors.
            for (Constructor<?> c : vanillaClass(name).getDeclaredConstructors()) {
                if (!accepts(c.getParameterTypes(), args)) continue;
                // Some vanilla classes are package-private, constructors and all.
                c.setAccessible(true);
                return c.newInstance(args);
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalArgumentException("Could not construct " + name, e);
        }
        throw new IllegalArgumentException("No constructor of " + name + " takes " + Arrays.toString(args));
    }

    /// The result of the public static method `method` of the vanilla class `name` whose parameters accept `args`.
    /// Throws [IllegalArgumentException] when none does, or it throws.
    @Nullable
    public Object call(@NotNull String name, @NotNull String method, Object @NotNull ... args) {
        try {
            for (Method m : vanillaClass(name).getMethods()) {
                if (m.getName().equals(method) && Modifier.isStatic(m.getModifiers())
                    && accepts(m.getParameterTypes(), args)) {
                    m.setAccessible(true);
                    return m.invoke(null, args);
                }
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalArgumentException("Could not call " + name + "." + method, e);
        }
        throw new IllegalArgumentException("No method " + name + "." + method + " takes " + Arrays.toString(args));
    }

    /// The public static field `field` of the vanilla class `name`.
    @Nullable
    public Object constant(@NotNull String name, @NotNull String field) {
        try {
            return vanillaClass(name).getField(field).get(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalArgumentException("No constant " + name + "." + field, e);
        }
    }

    /// Whether `args` can be passed as `types`: no varargs, null for any reference type.
    private static boolean accepts(Class<?>[] types, Object[] args) {
        if (types.length != args.length) return false;
        for (int i = 0; i < types.length; i++) {
            Class<?> type = types[i].isPrimitive() ? BOXES.get(types[i]) : types[i];
            if (args[i] == null ? types[i].isPrimitive() : !type.isInstance(args[i])) return false;
        }
        return true;
    }

    private static final Map<Class<?>, Class<?>> BOXES = new HashMap<>();

    static {
        BOXES.put(boolean.class, Boolean.class);
        BOXES.put(int.class, Integer.class);
        BOXES.put(long.class, Long.class);
        BOXES.put(float.class, Float.class);
        BOXES.put(double.class, Double.class);
    }

    /// Places `feature` (a vanilla placed feature) at `origin` if the biome there has it, as vanilla decorates
    /// chunks. Whether anything was placed. Rethrows what the feature throws.
    public boolean placeWithBiomeCheck(
        @NotNull Object feature, @NotNull FtLevel level, @NotNull FtChunkGenerator generator,
        @NotNull RandomSource random, @NotNull VnBlockPos origin
    ) {
        try {
            if (placeWithBiomeCheck == null) {
                placeWithBiomeCheck = vanillaClass("placement/PlacedFeature").getMethod("placeWithBiomeCheck",
                    FtLevel.class, FtChunkGenerator.class, RandomSource.class, VnBlockPos.class);
            }
            return (boolean) placeWithBiomeCheck.invoke(feature, level, generator, random, origin);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException) throw (RuntimeException) e.getCause();
            throw new IllegalStateException(e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not place a feature", e);
        }
    }

    /// See [#placeWithBiomeCheck]; looked up on first use.
    private volatile Method placeWithBiomeCheck;

    /// The feature type constant `name` of vanilla's feature class (`ORE`), or null when it could not be built
    /// (its feature is not run).
    @Nullable
    public Object featureType(@NotNull String name) {
        return constant("feature/Feature", name);
    }
}
