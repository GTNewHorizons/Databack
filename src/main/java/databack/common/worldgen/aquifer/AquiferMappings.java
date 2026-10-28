package databack.common.worldgen.aquifer;

import databack.common.worldgen.aquifer.standin.AqAquifer;
import databack.common.worldgen.aquifer.standin.AqBlock;
import databack.common.worldgen.aquifer.standin.AqBlockState;
import databack.common.worldgen.aquifer.standin.AqBlocks;
import databack.common.worldgen.aquifer.standin.AqChunkPos;
import databack.common.worldgen.aquifer.standin.AqContext;
import databack.common.worldgen.aquifer.standin.AqFluidPicker;
import databack.common.worldgen.aquifer.standin.AqFluidStatus;
import databack.common.worldgen.aquifer.standin.AqFunction;
import databack.common.worldgen.aquifer.standin.AqMath;
import databack.common.worldgen.aquifer.standin.AqNoiseChunk;
import databack.common.worldgen.aquifer.standin.AqPoint;
import databack.common.worldgen.aquifer.standin.AqRandom;
import databack.common.worldgen.aquifer.standin.AqRandomFactory;
import databack.common.worldgen.aquifer.standin.AqRouter;
import databack.common.worldgen.vanilla.VanillaMappings;

/// What vanilla's aquifer class may use, and what each use becomes (see [VanillaMappings]).
final class AquiferMappings {

    private static final String LEVELGEN = "net/minecraft/world/level/levelgen/";

    /// The "no fluid" height vanilla uses (observed black-box).
    static final int WAY_BELOW_MIN_Y = -32512;

    static final VanillaMappings MAPPINGS = VanillaMappings.builder()
        .mapClass(LEVELGEN + "DensityFunction", AqFunction.class)
        .mapClass(LEVELGEN + "DensityFunction$FunctionContext", AqContext.class)
        .mapClass(LEVELGEN + "DensityFunction$SinglePointContext", AqPoint.class)
        .mapClass(LEVELGEN + "NoiseRouter", AqRouter.class)
        .mapClass(LEVELGEN + "NoiseChunk", AqNoiseChunk.class)
        .mapClass(LEVELGEN + "PositionalRandomFactory", AqRandomFactory.class)
        .mapClass(LEVELGEN + "Aquifer", AqAquifer.class)
        .mapClass(LEVELGEN + "Aquifer$FluidPicker", AqFluidPicker.class)
        .mapClass(LEVELGEN + "Aquifer$FluidStatus", AqFluidStatus.class)
        .mapClass("net/minecraft/world/level/ChunkPos", AqChunkPos.class)
        .mapClass("net/minecraft/util/RandomSource", AqRandom.class)
        .mapClass("net/minecraft/world/level/block/Blocks", AqBlocks.class)
        .mapClass("net/minecraft/world/level/block/Block", AqBlock.class)
        .mapClass("net/minecraft/world/level/block/state/BlockState", AqBlockState.class)
        .mapClass("net/minecraft/util/Mth", AqMath.class)
        .mapClass("net/minecraft/core/SectionPos", AqMath.class)
        .mapClass("net/minecraft/core/BlockPos", AqMath.class)
        .mapClass("net/minecraft/world/level/biome/OverworldBiomeBuilder", AqMath.class)
        .constantFields("net/minecraft/world/level/dimension/DimensionType", "I", name -> WAY_BELOW_MIN_Y)
        .constantFields("net/minecraft/SharedConstants", "Z", flag -> Boolean.FALSE)
        // commons-lang3 ships with 1.7.10.
        .passthrough("org/apache/commons/lang3/mutable/MutableDouble", "java/util/Arrays", "java/lang/Math",
            "java/lang/Double")
        .build();

    private AquiferMappings() {}
}
