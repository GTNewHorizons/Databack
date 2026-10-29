package databack.common.worldgen.feature;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import databack.common.annotation.ThreadSafe;
import databack.common.worldgen.vanilla.VanillaClassLoader;

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
    Class<?> vanillaClass(@NotNull String name) throws ClassNotFoundException {
        return Class.forName(VanillaClassLoader.binaryName(LEVELGEN + name + ".class"), true, loader);
    }

    /// The feature type constant `name` of vanilla's feature class (`ORE`), or null when it could not be built
    /// (its feature is not run).
    @Nullable
    public Object featureType(@NotNull String name) {
        try {
            return vanillaClass("feature/Feature").getField(name).get(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalArgumentException("No feature type " + name, e);
        }
    }
}
