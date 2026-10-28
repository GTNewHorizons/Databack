package databack.common.worldgen.aquifer;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import databack.common.annotation.ThreadSafe;
import databack.common.worldgen.aquifer.standin.AqAquifer;
import databack.common.worldgen.aquifer.standin.AqBlockState;
import databack.common.worldgen.aquifer.standin.AqBlocks;
import databack.common.worldgen.aquifer.standin.AqChunkPos;
import databack.common.worldgen.aquifer.standin.AqFluidPicker;
import databack.common.worldgen.aquifer.standin.AqFluidStatus;
import databack.common.worldgen.aquifer.standin.AqFunction;
import databack.common.worldgen.aquifer.standin.AqNoiseChunk;
import databack.common.worldgen.aquifer.standin.AqPoint;
import databack.common.worldgen.aquifer.standin.AqRandomFactory;
import databack.common.worldgen.aquifer.standin.AqRouter;
import databack.common.worldgen.density.DensityNode;
import databack.common.worldgen.density.DensityRouter;
import databack.common.worldgen.rng.RandomFactory;
import databack.common.worldgen.surface.SurfaceSystem;

/// Decides what fills open terrain: air, the default fluid, lava, or a barrier (docs/worldgen/aquifers.md). With
/// aquifers, vanilla's own aquifer decides ([VanillaAquifer]); without, the global fluid rule applies. Thread-safe:
/// each call has its own state.
@ThreadSafe
public final class AquiferSystem {

    /// Open blocks below this height are lava under the global fluid rule (section 1).
    static final int LAVA_BELOW = -54;

    private final int seaLevel;
    @Nullable
    private final VanillaAquifer aquifer;
    @Nullable
    private final AqRouter router;
    @Nullable
    private final DensityNode preliminarySurface;
    @Nullable
    private final AqRandomFactory random;

    private AquiferSystem(
        int seaLevel, @Nullable VanillaAquifer aquifer, @Nullable AqRouter router,
        @Nullable DensityNode preliminarySurface, @Nullable AqRandomFactory random
    ) {
        this.seaLevel = seaLevel;
        this.aquifer = aquifer;
        this.router = router;
        this.preliminarySurface = preliminarySurface;
        this.random = random;
    }

    /// Without aquifers: the global fluid rule only.
    @NotNull
    @Contract("_ -> new")
    public static AquiferSystem disabled(int seaLevel) {
        return new AquiferSystem(seaLevel, null, null, null, null);
    }

    /// With vanilla's aquifer over the noise router's fields. `random` is the world's positional random factory.
    /// **Approximation:** vanilla's aquifer randomness is not reproduced, so a stream of ours is used.
    @NotNull
    @Contract("_, _, _, _ -> new")
    public static AquiferSystem enabled(
        int seaLevel, @NotNull VanillaAquifer aquifer, @NotNull DensityRouter router, @NotNull RandomFactory random
    ) {
        RandomFactory forked = random.fromHashOf("minecraft:aquifer").forkFactory();
        AqRouter fields = new AqRouter(field(router.barrierNoise), field(router.fluidLevelFloodednessNoise),
            field(router.fluidLevelSpreadNoise), field(router.lavaNoise), field(router.erosion), field(router.depth));
        return new AquiferSystem(seaLevel, aquifer, fields, router.preliminarySurfaceLevel,
            (x, y, z) -> forked.at(x, y, z)::nextInt);
    }

    private static AqFunction field(DensityNode node) {
        return ctx -> node.sample(ctx.blockX(), ctx.blockY(), ctx.blockZ());
    }

    /// What fills each block of chunk `(chunkX, chunkZ)`, as [SurfaceSystem]'s kinds ([SurfaceSystem#SOLID],
    /// [SurfaceSystem#AIR], [SurfaceSystem#FLUID] for the default fluid, [SurfaceSystem#LAVA]). `density` covers
    /// `layers` layers from `bottomY` at index `((y - bottomY) * 16 + z) * 16 + x`; positive is solid.
    @NotNull
    public byte[] kinds(int chunkX, int chunkZ, double @NotNull [] density, int bottomY, int layers) {
        AqFluidPicker global = this::globalFluid;
        AqAquifer chunkAquifer = aquifer == null ? null
            : aquifer.create(this::preliminarySurface, new AqChunkPos(chunkX, chunkZ), router, random, bottomY, layers,
                global);

        byte[] kinds = new byte[density.length];
        for (int index = 0; index < density.length; index++) {
            if (density[index] > 0) {
                kinds[index] = SurfaceSystem.SOLID;
                continue;
            }

            int x = chunkX * 16 + (index & 15), y = bottomY + (index >> 8), z = chunkZ * 16 + ((index >> 4) & 15);
            AqBlockState state = chunkAquifer == null ? global.computeFluid(x, y, z).at(y)
                : chunkAquifer.computeSubstance(new AqPoint(x, y, z), density[index]);
            kinds[index] = kind(state);
        }

        return kinds;
    }

    /// The global fluid rule (section 1): lava deep down, the default fluid below sea level.
    private AqFluidStatus globalFluid(int x, int y, int z) {
        return y < LAVA_BELOW ? new AqFluidStatus(LAVA_BELOW, AqBlocks.LAVA.defaultBlockState())
            : new AqFluidStatus(seaLevel, AqBlocks.WATER.defaultBlockState());
    }

    /// The preliminary surface level of the 4x4-column cell containing the column (as the surface system samples
    /// it).
    private int preliminarySurface(int x, int z) {
        return (int) Math.floor(preliminarySurface.sample(x & ~3, 0, z & ~3));
    }

    private static byte kind(@Nullable AqBlockState state) {
        if (state == null) return SurfaceSystem.SOLID;
        if (state.isAir()) return SurfaceSystem.AIR;
        return state.is(AqBlocks.LAVA) ? SurfaceSystem.LAVA : SurfaceSystem.FLUID;
    }
}
