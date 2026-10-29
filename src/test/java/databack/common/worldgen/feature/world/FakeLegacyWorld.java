package databack.common.worldgen.feature.world;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraftforge.common.util.ForgeDirection;

import org.jetbrains.annotations.NotNull;

import com.gtnewhorizon.gtnhlib.util.data.BlockMeta;
import com.gtnewhorizon.gtnhlib.util.data.ImmutableBlockMeta;

import databack.common.interop.heightmap.HeightmapType;

/// A world of layers 0..256 filled with one block, except where set. Every side is sturdy, every block can stay, and
/// every surface is at 64.
public final class FakeLegacyWorld implements LegacyWorld {

    private final Map<Long, ImmutableBlockMeta> blocks = new HashMap<>();
    @NotNull
    private final ImmutableBlockMeta fill;
    /// The side last asked about.
    ForgeDirection lastSide;

    public FakeLegacyWorld(@NotNull Block fill) {
        this.fill = new BlockMeta(fill, 0);
    }

    private static long key(int x, int y, int z) {
        return ((long) x & 0x3FFFFFF) << 38 | ((long) z & 0x3FFFFFF) << 12 | y & 0xFFF;
    }

    private ImmutableBlockMeta at(int x, int y, int z) {
        return blocks.getOrDefault(key(x, y, z), fill);
    }

    /// How many positions were set to `block`.
    public int count(@NotNull Block block) {
        int n = 0;
        for (ImmutableBlockMeta set : blocks.values()) {
            if (set.getBlock() == block) n++;
        }
        return n;
    }

    @Override
    public int minY() {
        return 0;
    }

    @Override
    public int height() {
        return 256;
    }

    @NotNull
    @Override
    public Block getBlock(int x, int y, int z) {
        return at(x, y, z).getBlock();
    }

    @Override
    public int getMeta(int x, int y, int z) {
        return at(x, y, z).getBlockMeta();
    }

    @Override
    public void setBlock(int x, int y, int z, @NotNull Block block, int meta) {
        blocks.put(key(x, y, z), new BlockMeta(block, meta));
    }

    @Override
    public boolean isSideSolid(int x, int y, int z, @NotNull ForgeDirection side) {
        lastSide = side;
        return true;
    }

    @Override
    public boolean canBlockStay(@NotNull Block block, int x, int y, int z) {
        return true;
    }

    @Override
    public int surface(@NotNull HeightmapType heightmap, int x, int z) {
        return 64;
    }

    @Override
    public void scheduleUpdate(int x, int y, int z, @NotNull Block block, int delay) {}
}
