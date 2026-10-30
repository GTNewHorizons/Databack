package databack.common.worldgen.feature;

import databack.common.interop.heightmap.HeightmapType;
import databack.common.worldgen.feature.standin.FtBiome;
import databack.common.worldgen.feature.standin.FtVoxelBits;
import databack.common.worldgen.feature.standin.FtStructureTemplate;
import databack.common.worldgen.feature.standin.FtFluids;
import databack.common.worldgen.feature.standin.FtFluidTags;
import databack.common.worldgen.feature.standin.FtFluid;
import databack.common.worldgen.feature.standin.FtBoundingBox;
import databack.common.worldgen.feature.standin.FtBlockProperties;
import databack.common.worldgen.feature.standin.FtBiomeFeatures;
import databack.common.worldgen.feature.standin.FtBlock;
import databack.common.worldgen.feature.standin.FtBlockState;
import databack.common.worldgen.feature.standin.FtBlockTags;
import databack.common.worldgen.feature.standin.FtBlocks;
import databack.common.worldgen.feature.standin.FtChunk;
import databack.common.worldgen.feature.standin.FtChunkGenerator;
import databack.common.worldgen.feature.standin.FtDoublePlantBlock;
import databack.common.worldgen.feature.standin.FtFluidState;
import databack.common.worldgen.feature.standin.FtGenerationContext;
import databack.common.worldgen.feature.standin.FtHeightProvider;
import databack.common.worldgen.feature.standin.FtHolder;
import databack.common.worldgen.feature.standin.FtHolderSet;
import databack.common.worldgen.feature.standin.FtInfoNoise;
import databack.common.worldgen.feature.standin.FtIntProvider;
import databack.common.worldgen.feature.standin.FtIntRange;
import databack.common.worldgen.feature.standin.FtLevel;
import databack.common.worldgen.feature.standin.FtMossyCarpetBlock;
import databack.common.worldgen.feature.standin.FtProperty;
import databack.common.worldgen.feature.standin.FtSection;
import databack.common.worldgen.feature.standin.FtSectionAccess;
import databack.common.worldgen.feature.standin.FtShape;
import databack.common.worldgen.feature.standin.FtShapes;
import databack.common.worldgen.feature.standin.FtTag;
import databack.common.worldgen.feature.standin.FtWeightedList;
import databack.common.worldgen.noise.NormalNoise;
import databack.common.worldgen.rng.RandomSource;
import databack.common.worldgen.rng.SourceBits;
import databack.common.worldgen.rng.StandardRandom;
import databack.common.worldgen.vanilla.VanillaMappings;
import databack.common.worldgen.vanilla.standin.VnAxis;
import databack.common.worldgen.vanilla.standin.VnAxisDirection;
import databack.common.worldgen.vanilla.standin.VnBlockPos;
import databack.common.worldgen.vanilla.standin.VnDirection;
import databack.common.worldgen.vanilla.standin.VnLists;
import databack.common.worldgen.vanilla.standin.VnMath;
import databack.common.worldgen.vanilla.standin.VnPlane;
import databack.common.worldgen.vanilla.standin.VnRegistry;

/// What vanilla's feature and placement classes may use, and what each use becomes (see [VanillaMappings]).
final class FeatureMappings {

    private static final String MC = "net/minecraft/";
    private static final String LEVEL = MC + "world/level/";
    private static final String LEVELGEN = LEVEL + "levelgen/";
    private static final String BLOCK = LEVEL + "block/";

    static final VanillaMappings MAPPINGS = VanillaMappings.builder()
        // Codecs and registries: built, never used.
        .inert("com/mojang/", MC + "core/registries/", MC + "core/RegistryCodecs", MC + "core/DefaultedRegistry",
            MC + "resources/", MC + "util/ExtraCodecs", MC + "util/StringRepresentable",
            MC + "util/valueproviders/IntProviders", MC + "server/level/ServerLevel", MC + "world/entity/Entity",
            LEVELGEN + "feature/FeatureCountTracker", BLOCK + "entity/",
            LEVELGEN + "blockpredicates/BlockPredicateType", LEVELGEN + "feature/stateproviders/BlockStateProviderType",
            LEVELGEN + "placement/PlacementModifierType", LEVELGEN + "structure/templatesystem/RuleTestType",
            LEVELGEN + "feature/featuresize/FeatureSizeType", LEVELGEN + "feature/foliageplacers/FoliagePlacerType",
            LEVELGEN + "feature/rootplacers/RootPlacerType", LEVELGEN + "feature/treedecorators/TreeDecoratorType",
            LEVELGEN + "feature/trunkplacers/TrunkPlacerType")
        .mapClass(MC + "core/Registry", VnRegistry.class)
        .constantFields(MC + "SharedConstants", "Z", flag -> Boolean.FALSE)
        .redirectInstance("java/util/List", "getFirst", VnLists.class)
        .redirectInstance("java/util/List", "getLast", VnLists.class)

        .mapClass(MC + "core/BlockPos", VnBlockPos.class)
        .mapClass(MC + "core/BlockPos$MutableBlockPos", VnBlockPos.class)
        .mapClass(MC + "core/Vec3i", VnBlockPos.class)
        .mapClass(MC + "core/Direction", VnDirection.class)
        .mapClass(MC + "core/Direction$Axis", VnAxis.class)
        .mapClass(MC + "core/Direction$AxisDirection", VnAxisDirection.class)
        .mapClass(MC + "core/Direction$Plane", VnPlane.class)
        .mapClass(MC + "core/SectionPos", VnMath.class)
        .mapClass(MC + "util/Mth", VnMath.class)
        .mapClass(MC + "util/Util", VnMath.class)
        .mapClass(MC + "core/Holder", FtHolder.class)
        .mapClass(MC + "core/Holder$Reference", FtHolder.class)
        .mapClass(MC + "core/HolderSet", FtHolderSet.class)
        .mapClass(MC + "core/HolderSet$Direct", FtHolderSet.class)
        .mapClass(MC + "tags/TagKey", FtTag.class)
        .mapClass(MC + "tags/BlockTags", FtBlockTags.class)

        .mapClass(MC + "util/RandomSource", RandomSource.class)
        .mapClass(LEVELGEN + "WorldgenRandom", SourceBits.class)
        .mapClass(LEVELGEN + "LegacyRandomSource", StandardRandom.class)
        .mapClass(MC + "util/random/WeightedList", FtWeightedList.class)
        .mapClass(MC + "util/random/WeightedList$Builder", FtWeightedList.Builder.class)
        .mapClass(MC + "util/valueproviders/IntProvider", FtIntProvider.class)
        .mapClass(MC + "util/valueproviders/ConstantInt", FtIntProvider.class)
        .mapClass(MC + "util/valueproviders/TrapezoidInt", FtIntProvider.class)
        .mapClass(MC + "util/valueproviders/UniformInt", FtIntProvider.class)
        .mapClass(MC + "util/InclusiveRange", FtIntRange.class)
        .mapClass(LEVELGEN + "heightproviders/HeightProvider", FtHeightProvider.class)
        .mapClass(LEVELGEN + "synth/NormalNoise", NormalNoise.class)
        .mapClass(LEVELGEN + "synth/NormalNoise$NoiseParameters", NormalNoise.NoiseParameters.class)
        .mapClass(LEVELGEN + "synth/PerlinSimplexNoise", FtInfoNoise.class)

        .mapClass(BLOCK + "Block", FtBlock.class)
        .mapClass(BLOCK + "Blocks", FtBlocks.class)
        .mapClass(BLOCK + "DoublePlantBlock", FtDoublePlantBlock.class)
        .mapClass(BLOCK + "MossyCarpetBlock", FtMossyCarpetBlock.class)
        .mapClass(BLOCK + "RotatedPillarBlock", FtBlockProperties.class)
        .mapClass(BLOCK + "LeavesBlock", FtBlockProperties.class)
        .mapClass(BLOCK + "VineBlock", FtBlockProperties.class)
        .mapClass(BLOCK + "CocoaBlock", FtBlockProperties.class)
        .mapClass(BLOCK + "BeehiveBlock", FtBlockProperties.class)
        .mapClass(BLOCK + "SnowyBlock", FtBlockProperties.class)
        .mapClass(BLOCK + "state/properties/BlockStateProperties", FtBlockProperties.class)
        .mapClass(BLOCK + "state/BlockState", FtBlockState.class)
        .mapClass(BLOCK + "state/BlockBehaviour$BlockStateBase", FtBlockState.class)
        .mapClass(BLOCK + "state/properties/Property", FtProperty.class)
        .mapClass(BLOCK + "state/properties/IntegerProperty", FtProperty.class)
        .mapClass(BLOCK + "state/properties/EnumProperty", FtProperty.class)
        .mapClass(BLOCK + "state/properties/BooleanProperty", FtProperty.class)
        .mapClass(LEVEL + "material/FluidState", FtFluidState.class)
        .mapClass(LEVEL + "material/Fluid", FtFluid.class)
        .mapClass(LEVEL + "material/FlowingFluid", FtFluid.class)
        .mapClass(LEVEL + "material/Fluids", FtFluids.class)
        .mapClass(MC + "tags/FluidTags", FtFluidTags.class)
        .mapClass(MC + "world/phys/shapes/Shapes", FtShapes.class)
        .mapClass(MC + "world/phys/shapes/VoxelShape", FtShape.class)
        .mapClass(MC + "world/phys/shapes/DiscreteVoxelShape", FtVoxelBits.class)
        .mapClass(MC + "world/phys/shapes/BitSetDiscreteVoxelShape", FtVoxelBits.class)
        .mapClass(LEVELGEN + "structure/BoundingBox", FtBoundingBox.class)
        .mapClass(LEVELGEN + "structure/templatesystem/StructureTemplate", FtStructureTemplate.class)

        .mapClass(LEVEL + "WorldGenLevel", FtLevel.class)
        .mapClass(LEVEL + "LevelAccessor", FtLevel.class)
        .mapClass(LEVEL + "LevelReader", FtLevel.class)
        .mapClass(LEVEL + "LevelWriter", FtLevel.class)
        .mapClass(LEVEL + "BlockGetter", FtLevel.class)
        .mapClass(LEVEL + "LevelHeightAccessor", FtLevel.class)
        .mapClass(LEVEL + "LevelSimulatedReader", FtLevel.class)
        .mapClass(LEVEL + "LevelSimulatedRW", FtLevel.class)
        .mapClass(LEVEL + "chunk/ChunkAccess", FtChunk.class)
        .mapClass(LEVEL + "chunk/BulkSectionAccess", FtSectionAccess.class)
        .mapClass(LEVEL + "chunk/LevelChunkSection", FtSection.class)
        .mapClass(LEVEL + "chunk/ChunkGenerator", FtChunkGenerator.class)
        .mapClass(LEVEL + "biome/Biome", FtBiome.class)
        .mapClass(LEVEL + "biome/BiomeGenerationSettings", FtBiomeFeatures.class)
        .mapClass(LEVELGEN + "Heightmap$Types", HeightmapType.class)
        .mapClass(LEVELGEN + "WorldGenerationContext", FtGenerationContext.class)

        // commons-lang3 and Guava ship with 1.7.10.
        .passthrough("org/apache/commons/lang3/mutable/MutableBoolean", "com/google/common/collect/ImmutableList",
            "com/google/common/collect/Lists", "com/google/common/collect/Iterables", "com/google/common/collect/Sets",
            "com/google/common/collect/Sets$SetView", "it/unimi/dsi/fastutil/objects/ObjectArrayList")
        .passthrough("java/lang/Boolean", "java/lang/Class", "java/lang/Comparable", "java/lang/Double",
            "java/lang/Enum", "java/lang/Float", "java/lang/IllegalArgumentException",
            "java/lang/IllegalStateException", "java/lang/Integer", "java/lang/Iterable", "java/lang/Long",
            "java/lang/Math", "java/lang/Throwable", "java/util/ArrayList", "java/util/BitSet", "java/util/Collection",
            "java/util/Iterator", "java/util/List", "java/util/Objects", "java/util/Optional",
            "java/util/function/BiFunction", "java/util/function/BiPredicate", "java/util/function/Consumer",
            "java/util/function/Function", "java/util/function/IntFunction", "java/util/function/Predicate",
            "java/util/function/Supplier", "java/util/stream/IntStream", "java/util/stream/Stream",
            "java/util/stream/Stream$Builder", "java/util/Collections", "java/util/Comparator", "java/util/HashSet",
            "java/util/Set", "java/util/OptionalInt", "java/util/function/BiConsumer", "java/util/function/ToIntFunction",
            "java/util/stream/Collector", "java/util/stream/Collectors")
        .build();

    private FeatureMappings() {}
}
