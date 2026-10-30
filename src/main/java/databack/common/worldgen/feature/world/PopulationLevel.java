package databack.common.worldgen.feature.world;

import net.minecraft.block.Block;
import net.minecraftforge.common.util.ForgeDirection;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.gtnewhorizon.gtnhlib.util.data.ImmutableBlockMeta;

import databack.common.interop.heightmap.HeightmapType;
import databack.common.worldgen.feature.standin.FtBlock;
import databack.common.worldgen.feature.standin.FtBlockFacts;
import databack.common.worldgen.feature.standin.FtBlockState;
import databack.common.worldgen.feature.standin.FtHolder;
import databack.common.worldgen.feature.standin.FtLevel;
import databack.common.worldgen.rng.RandomSource;
import databack.common.worldgen.vanilla.standin.VnBlockPos;
import databack.common.worldgen.vanilla.standin.VnDirection;

/// The level features see while populating: a 1.7 world, readable and writable only within an area (the chunks known
/// to be generated), with biomes from the generator.
///
/// Block states read back from the world carry what 1.7 stores ([LegacyStates#toModern]); properties 1.7 computes
/// from neighbours (fence connections, stair shapes) are absent. Vanilla's update flags are ignored; writes never
/// update neighbours.
public final class PopulationLevel implements FtLevel {

    /// The generator's biome at a block, as a namespaced id.
    @FunctionalInterface
    public interface BiomeLookup {

        @NotNull
        String at(int x, int y, int z);
    }

    @NotNull
    private final LegacyWorld world;
    @NotNull
    private final LegacyStates states;
    @NotNull
    private final ColumnArea area;
    @NotNull
    private final BiomeLookup biomes;
    @NotNull
    private final RandomSource random;

    public PopulationLevel(
        @NotNull LegacyWorld world, @NotNull LegacyStates states, @NotNull ColumnArea area,
        @NotNull BiomeLookup biomes, @NotNull RandomSource random
    ) {
        this.world = world;
        this.states = states;
        this.area = area;
        this.biomes = biomes;
        this.random = random;
    }

    @Override
    public int getMinY() {
        return world.minY();
    }

    @Override
    public int getHeight() {
        return world.height();
    }

    @Override
    public boolean ensureCanWrite(@NotNull VnBlockPos pos) {
        return area.contains(pos.getX(), pos.getZ()) && isInsideBuildHeight(pos);
    }

    /// Air outside the build height and the area: only the area's chunks are known to exist.
    @NotNull
    @Override
    public FtBlockState getBlockState(@NotNull VnBlockPos pos) {
        if (!ensureCanWrite(pos)) return FtBlock.of("minecraft:air").defaultBlockState();

        int x = pos.getX(), y = pos.getY(), z = pos.getZ();
        return states.toModern(world.getBlock(x, y, z), world.getMeta(x, y, z));
    }

    /// Refused (false) where [#ensureCanWrite] is false and for blocks without a 1.7 counterpart, and false when the
    /// block is already there.
    @Override
    public boolean setBlock(@NotNull VnBlockPos pos, @NotNull FtBlockState state, int flags) {
        if (!ensureCanWrite(pos)) return false;

        int x = pos.getX(), y = pos.getY(), z = pos.getZ();
        ImmutableBlockMeta block = states.toLegacy(state);
        if (block == null) return false;
        if (world.getBlock(x, y, z) == block.getBlock() && world.getMeta(x, y, z) == block.getBlockMeta()) {
            return false;
        }

        world.setBlock(x, y, z, block.getBlock(), block.getBlockMeta());
        return true;
    }

    @Override
    public int getHeight(@NotNull HeightmapType heightmap, int x, int z) {
        return world.surface(heightmap, x, z);
    }

    @NotNull
    @Override
    public FtHolder getBiome(@NotNull VnBlockPos pos) {
        String id = biomes.at(pos.getX(), pos.getY(), pos.getZ());
        return new FtHolder(id, id);
    }

    @NotNull
    @Override
    public RandomSource getRandom() {
        return random;
    }

    @Override
    public void scheduleTick(@NotNull VnBlockPos pos, @NotNull FtBlock block, int delay) {
        Block legacy = legacyBlock(block.defaultBlockState());
        if (legacy == null || !ensureCanWrite(pos)) return;
        world.scheduleUpdate(pos.getX(), pos.getY(), pos.getZ(), legacy, delay);
    }

    /// Blocks without a 1.7 counterpart survive nowhere.
    @Override
    public boolean canSurvive(@NotNull FtBlockState state, @NotNull VnBlockPos pos) {
        Block legacy = legacyBlock(state);
        return legacy != null && world.canBlockStay(legacy, pos.getX(), pos.getY(), pos.getZ());
    }

    /// Vanilla judges `state` itself, and some callers pass a `pos` that holds another block (fallen trees ask about
    /// the block below with the position above it). The world is asked only when `pos` holds `state`, since it
    /// knows partial faces; otherwise full blocks are sturdy.
    @Override
    public boolean isFaceSturdy(@NotNull FtBlockState state, @NotNull VnBlockPos pos, @NotNull VnDirection face) {
        if (!state.equals(getBlockState(pos))) return FtBlockFacts.get().isSolidRender(state);

        return world.isSideSolid(pos.getX(), pos.getY(), pos.getZ(), side(face));
    }

    private static ForgeDirection side(VnDirection face) {
        for (ForgeDirection side : ForgeDirection.VALID_DIRECTIONS) {
            if (side.offsetX == face.getStepX() && side.offsetY == face.getStepY() && side.offsetZ == face.getStepZ()) {
                return side;
            }
        }
        throw new IllegalArgumentException("Not a direction: " + face);
    }

    @Nullable
    private Block legacyBlock(FtBlockState state) {
        ImmutableBlockMeta block = states.toLegacy(state);
        return block == null ? null : block.getBlock();
    }
}
