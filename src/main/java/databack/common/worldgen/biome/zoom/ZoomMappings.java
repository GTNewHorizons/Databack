package databack.common.worldgen.biome.zoom;

import databack.common.worldgen.vanilla.VanillaMappings;

/// What vanilla's biome zoom classes may use, and what each use becomes (see [VanillaMappings]).
final class ZoomMappings {

    static final String MANAGER = "net/minecraft/world/level/biome/BiomeManager";

    static final VanillaMappings MAPPINGS = VanillaMappings.builder()
        .mapClass(MANAGER + "$NoiseBiomeSource", ZoomSource.class)
        .mapClass("net/minecraft/core/Holder", Object.class)
        .mapClass("net/minecraft/core/BlockPos", ZoomPos.class)
        .mapClass("net/minecraft/core/QuartPos", ZoomQuartPos.class)
        .mapClass("net/minecraft/util/Mth", ZoomMth.class)
        .redirectStatic("java/lang/Math", "floorMod", ZoomMath.class)
        // Guava ships with 1.7.10 (version 17), which has everything the zoom seed uses.
        .passthrough("com/google/common/hash/Hashing", "com/google/common/hash/HashFunction",
            "com/google/common/hash/HashCode")
        .addInterface(MANAGER, ZoomLookup.class)
        .build();

    private ZoomMappings() {}
}
