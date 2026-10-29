package databack.common.worldgen.carver;

import databack.common.worldgen.aquifer.standin.AqContext;
import databack.common.worldgen.aquifer.standin.AqPoint;
import databack.common.worldgen.carver.standin.CvAnchor;
import databack.common.worldgen.carver.standin.CvAquifer;
import databack.common.worldgen.carver.standin.CvBlock;
import databack.common.worldgen.carver.standin.CvBlockPos;
import databack.common.worldgen.carver.standin.CvBlockSet;
import databack.common.worldgen.carver.standin.CvBlockState;
import databack.common.worldgen.carver.standin.CvBlocks;
import databack.common.worldgen.carver.standin.CvCanyonConfiguration;
import databack.common.worldgen.carver.standin.CvCanyonShape;
import databack.common.worldgen.carver.standin.CvCarver;
import databack.common.worldgen.carver.standin.CvCarverConfiguration;
import databack.common.worldgen.carver.standin.CvCarvingMask;
import databack.common.worldgen.carver.standin.CvCaveConfiguration;
import databack.common.worldgen.carver.standin.CvChunk;
import databack.common.worldgen.carver.standin.CvChunkPos;
import databack.common.worldgen.carver.standin.CvContext;
import databack.common.worldgen.carver.standin.CvDebug;
import databack.common.worldgen.carver.standin.CvDirection;
import databack.common.worldgen.carver.standin.CvFloatProvider;
import databack.common.worldgen.carver.standin.CvFluid;
import databack.common.worldgen.carver.standin.CvFluidState;
import databack.common.worldgen.carver.standin.CvFluids;
import databack.common.worldgen.carver.standin.CvHeightProvider;
import databack.common.worldgen.carver.standin.CvMath;
import databack.common.worldgen.carver.standin.CvProperty;
import databack.common.worldgen.carver.standin.CvRandom;
import databack.common.worldgen.carver.standin.CvRegistration;
import databack.common.worldgen.vanilla.VanillaMappings;

/// What vanilla's carver classes may use, and what each use becomes (see [VanillaMappings]).
final class CarverMappings {

    private static final String LEVELGEN = "net/minecraft/world/level/levelgen/";
    static final String CARVER = LEVELGEN + "carver/";
    private static final String BLOCK = "net/minecraft/world/level/block/";
    private static final String MATERIAL = "net/minecraft/world/level/material/";

    static final VanillaMappings MAPPINGS = VanillaMappings.builder()
        .mapClass(CARVER + "CarvingContext", CvContext.class)
        .mapClass(LEVELGEN + "WorldGenerationContext", CvContext.class)
        .mapClass(CARVER + "CarverConfiguration", CvCarverConfiguration.class)
        .mapClass(CARVER + "CaveCarverConfiguration", CvCaveConfiguration.class)
        .mapClass(CARVER + "CanyonCarverConfiguration", CvCanyonConfiguration.class)
        .mapClass(CARVER + "CanyonCarverConfiguration$CanyonShapeConfiguration", CvCanyonShape.class)
        .mapClass(CARVER + "CarverDebugSettings", CvDebug.class)
        .mapClass("net/minecraft/world/level/chunk/ChunkAccess", CvChunk.class)
        .mapClass("net/minecraft/world/level/chunk/CarvingMask", CvCarvingMask.class)
        .mapClass("net/minecraft/world/level/ChunkPos", CvChunkPos.class)
        .mapClass("net/minecraft/core/BlockPos", CvBlockPos.class)
        .mapClass("net/minecraft/core/BlockPos$MutableBlockPos", CvBlockPos.class)
        .mapClass("net/minecraft/core/Vec3i", CvBlockPos.class)
        .mapClass("net/minecraft/core/Direction", CvDirection.class)
        .mapClass("net/minecraft/core/SectionPos", CvMath.class)
        .mapClass("net/minecraft/util/Mth", CvMath.class)
        .mapClass("net/minecraft/util/RandomSource", CvRandom.class)
        .mapClass(BLOCK + "Blocks", CvBlocks.class)
        .mapClass(BLOCK + "Block", CvBlock.class)
        .mapClass(BLOCK + "state/BlockState", CvBlockState.class)
        .mapClass(BLOCK + "state/properties/Property", CvProperty.class)
        .mapClass(BLOCK + "state/properties/BooleanProperty", CvProperty.class)
        .mapClass(BLOCK + "state/properties/BlockStateProperties", CvProperty.class)
        .mapClass("net/minecraft/core/HolderSet", CvBlockSet.class)
        .mapClass(MATERIAL + "FluidState", CvFluidState.class)
        .mapClass(MATERIAL + "Fluid", CvFluid.class)
        .mapClass(MATERIAL + "FlowingFluid", CvFluid.class)
        .mapClass(MATERIAL + "Fluids", CvFluids.class)
        .mapClass(LEVELGEN + "Aquifer", CvAquifer.class)
        .mapClass(LEVELGEN + "DensityFunction$FunctionContext", AqContext.class)
        .mapClass(LEVELGEN + "DensityFunction$SinglePointContext", AqPoint.class)
        .mapClass(LEVELGEN + "VerticalAnchor", CvAnchor.class)
        .mapClass(LEVELGEN + "heightproviders/HeightProvider", CvHeightProvider.class)
        .mapClass("net/minecraft/util/valueproviders/FloatProvider", CvFloatProvider.class)
        .mapClass("com/mojang/serialization/Codec", CvRegistration.class)
        .mapClass("com/mojang/serialization/MapCodec", CvRegistration.class)
        .mapClass("net/minecraft/core/Registry", CvRegistration.class)
        .mapClass("net/minecraft/core/registries/BuiltInRegistries", CvRegistration.class)
        .constantFields("net/minecraft/SharedConstants", "Z", flag -> Boolean.FALSE)
        .redirectStatic("net/minecraft/SharedConstants", "debugVoidTerrain", CvDebug.class)
        // Only the codec plumbing uses configured carvers, and the codecs are inert.
        .nullLambdasOver(CARVER + "ConfiguredWorldCarver")
        .addInterface(CARVER + "WorldCarver", CvCarver.class)
        // commons-lang3 and Guava ship with 1.7.10.
        .passthrough("org/apache/commons/lang3/mutable/MutableBoolean", "com/google/common/collect/ImmutableSet",
            "java/util/function/Function", "java/util/function/Consumer", "java/util/Optional", "java/util/Set",
            "java/lang/Comparable", "java/lang/Boolean", "java/lang/Math", "java/lang/String")
        .build();

    private CarverMappings() {}
}
