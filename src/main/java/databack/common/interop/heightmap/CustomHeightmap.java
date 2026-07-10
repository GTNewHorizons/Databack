package databack.common.interop.heightmap;

import net.minecraft.world.World;

import cpw.mods.fml.common.Optional;
import databack.common.dto.worldgen.block_predicate.IBlockPredicate;
import databack.mixins.DBMods;

public class CustomHeightmap implements Heightmap {

    private final World world;
    private final IBlockPredicate predicate;

    public CustomHeightmap(World world, IBlockPredicate predicate) {
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
