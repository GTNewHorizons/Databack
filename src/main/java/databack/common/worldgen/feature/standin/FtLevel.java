package databack.common.worldgen.feature.standin;

import java.util.Optional;
import java.util.function.Predicate;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import databack.common.interop.heightmap.HeightmapType;
import databack.common.worldgen.rng.RandomSource;
import databack.common.worldgen.vanilla.standin.VnBlockPos;
import databack.common.worldgen.vanilla.standin.VnDirection;

/// Stand-in for the world a feature is placed in, under every name vanilla gives it (generation level, level reader,
/// block getter...). Positions outside the area being populated may be refused ([#ensureCanWrite]).
@ApiStatus.Internal
public interface FtLevel {

    int getMinY();

    /// The number of block layers.
    int getHeight();

    default boolean isOutsideBuildHeight(int y) {
        return y < getMinY() || y >= getMinY() + getHeight();
    }

    default boolean isInsideBuildHeight(@NotNull VnBlockPos pos) {
        return !isOutsideBuildHeight(pos.getY());
    }

    /// The highest layer.
    default int getMaxY() {
        return getMinY() + getHeight() - 1;
    }

    /// Whether a feature may change `pos`.
    boolean ensureCanWrite(@NotNull VnBlockPos pos);

    @NotNull
    FtBlockState getBlockState(@NotNull VnBlockPos pos);

    default boolean isEmptyBlock(@NotNull VnBlockPos pos) {
        return getBlockState(pos).isAir();
    }

    default boolean isStateAtPosition(@NotNull VnBlockPos pos, @NotNull Predicate<FtBlockState> test) {
        return test.test(getBlockState(pos));
    }

    default boolean isFluidAtPosition(@NotNull VnBlockPos pos, @NotNull Predicate<FtFluidState> test) {
        return test.test(getBlockState(pos).getFluidState());
    }

    /// Features see no block entities: worldgen does not fill them in (bee nests get no bees). `type` is always null.
    @NotNull
    default Optional<?> getBlockEntity(@NotNull VnBlockPos pos, @Nullable Object type) {
        return Optional.empty();
    }

    /// Sets `pos` to `state`; `flags` are vanilla's update flags. Whether the block changed.
    boolean setBlock(@NotNull VnBlockPos pos, @NotNull FtBlockState state, int flags);

    /// The first Y above the surface `heightmap` tracks in column `(x, z)`.
    int getHeight(@NotNull HeightmapType heightmap, int x, int z);

    /// The position in `pos`'s column just above the surface `heightmap` tracks.
    @NotNull
    default VnBlockPos getHeightmapPos(@NotNull HeightmapType heightmap, @NotNull VnBlockPos pos) {
        return new VnBlockPos(pos.getX(), getHeight(heightmap, pos.getX(), pos.getZ()), pos.getZ());
    }

    /// The biome at `pos`, as its id.
    @NotNull
    FtHolder getBiome(@NotNull VnBlockPos pos);

    /// The random of the world itself, not the feature's.
    @NotNull
    RandomSource getRandom();

    /// Asks for `block` at `pos` to be updated `delay` ticks from now.
    void scheduleTick(@NotNull VnBlockPos pos, @NotNull FtBlock block, int delay);

    /// Whether `state` can stay at `pos`, as placed blocks are checked.
    boolean canSurvive(@NotNull FtBlockState state, @NotNull VnBlockPos pos);

    /// Whether `face` of `state` at `pos` can hold blocks against it.
    boolean isFaceSturdy(@NotNull FtBlockState state, @NotNull VnBlockPos pos, @NotNull VnDirection face);

    /// Worldgen has no entities in the way. `entity` is always null.
    default boolean isUnobstructed(@Nullable Object entity, @NotNull FtShape shape) {
        return true;
    }

    @NotNull
    default FtChunk getChunk(int chunkX, int chunkZ) {
        return FtChunk.INSTANCE;
    }

    @NotNull
    default FtChunk getChunk(@NotNull VnBlockPos pos) {
        return FtChunk.INSTANCE;
    }
}
