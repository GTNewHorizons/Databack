package databack.common.worldgen.aquifer;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import databack.common.annotation.ThreadSafe;
import databack.common.worldgen.aquifer.standin.AqAquifer;
import databack.common.worldgen.aquifer.standin.AqBlockState;
import databack.common.worldgen.aquifer.standin.AqBlocks;
import databack.common.worldgen.aquifer.standin.AqChunkPos;
import databack.common.worldgen.aquifer.standin.AqContext;
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
    private final PreliminarySurfaceCache preliminarySurface;
    @Nullable
    private final AqRandomFactory random;

    private AquiferSystem(
        int seaLevel, @Nullable VanillaAquifer aquifer, @Nullable AqRouter router,
        @Nullable PreliminarySurfaceCache preliminarySurface, @Nullable AqRandomFactory random
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
        return new AquiferSystem(seaLevel, aquifer, fields, new PreliminarySurfaceCache(router.preliminarySurfaceLevel),
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
        AqAquifer chunkAquifer = chunkAquifer(chunkX, chunkZ, bottomY, layers);

        byte[] kinds = new byte[density.length];
        for (int index = 0; index < density.length; index++) {
            if (density[index] > 0) {
                kinds[index] = SurfaceSystem.SOLID;
                continue;
            }

            int x = chunkX * 16 + (index & 15), y = bottomY + (index >> 8), z = chunkZ * 16 + ((index >> 4) & 15);
            AqBlockState state = chunkAquifer.computeSubstance(new AqPoint(x, y, z), density[index]);
            kinds[index] = kind(state);
        }

        return kinds;
    }

    /// The aquifer of chunk `(chunkX, chunkZ)` over `layers` layers from `bottomY`: vanilla's, or the global fluid rule
    /// when aquifers are off. Later steps (carvers) ask the same aquifer. Not thread-safe; one per chunk.
    @NotNull
    public AqAquifer chunkAquifer(int chunkX, int chunkZ, int bottomY, int layers) {
        AqFluidPicker global = this::globalFluid;
        if (aquifer != null) {
            return aquifer.create(preliminarySurface::at, new AqChunkPos(chunkX, chunkZ), router, random, bottomY,
                layers, global);
        }

        return new AqAquifer() {

            @Override
            public AqBlockState computeSubstance(AqContext ctx, double density) {
                if (density > 0) return null;
                return global.computeFluid(ctx.blockX(), ctx.blockY(), ctx.blockZ()).at(ctx.blockY());
            }

            @Override
            public boolean shouldScheduleFluidUpdate() {
                return false;
            }
        };
    }

    /// The global fluid rule (section 1): lava deep down, the default fluid below sea level.
    private AqFluidStatus globalFluid(int x, int y, int z) {
        return y < LAVA_BELOW ? new AqFluidStatus(LAVA_BELOW, AqBlocks.LAVA.defaultBlockState())
            : new AqFluidStatus(seaLevel, AqBlocks.WATER.defaultBlockState());
    }

    private static byte kind(@Nullable AqBlockState state) {
        if (state == null) return SurfaceSystem.SOLID;
        if (state.isAir()) return SurfaceSystem.AIR;
        return state.is(AqBlocks.LAVA) ? SurfaceSystem.LAVA : SurfaceSystem.FLUID;
    }
}
