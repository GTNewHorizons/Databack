package databack.common.interop.heightmap;

import net.minecraft.world.World;

import cpw.mods.fml.common.Optional;
import databack.mixins.DBMods;

public class CustomHeightmap implements Heightmap {

    /// Whether the block at a position counts towards the heightmap.
    @FunctionalInterface
    public interface BlockTest {

        boolean test(World world, int x, int y, int z);
    }

    private final World world;
    private final BlockTest predicate;

    public CustomHeightmap(World world, BlockTest predicate) {
        this.world = world;
        this.predicate = predicate;
    }

    @Override
    public int getTop(int worldX, int worldY, int worldZ) {
        int baseTop = world.getHeightValue(worldX, worldZ);

        while (!predicate.test(world, worldX, baseTop, worldZ)) {
            baseTop--;

            if (!DBMods.CUBIC_CHUNKS.isModLoaded() && baseTop < 0) {
                return 0;
            }
        }

        return baseTop;
    }

    @Optional.Method(modid = "cubicchunks")
    private int getTopCC(int worldX, int worldY, int worldZ) {
        throw new UnsupportedOperationException("not yet implemented");
    }
}
