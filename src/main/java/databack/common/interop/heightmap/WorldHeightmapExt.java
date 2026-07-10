package databack.common.interop.heightmap;

import net.minecraft.world.World;

public interface WorldHeightmapExt {

    Heightmap getHeightmap(HeightmapType heightmapType);

    static Heightmap getHeightmap(World world, HeightmapType heightmapType) {
        return ((WorldHeightmapExt) world).getHeightmap(heightmapType);
    }
}
