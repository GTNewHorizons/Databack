package databack.mixins.early;

import java.util.EnumMap;

import net.minecraft.block.Block;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import databack.common.interop.heightmap.CustomHeightmap;
import databack.common.interop.heightmap.Heightmap;
import databack.common.interop.heightmap.HeightmapType;
import databack.common.interop.heightmap.WorldHeightmapExt;

@Mixin(World.class)
public class MixinWorld_HeightmapExt implements WorldHeightmapExt {

    @Unique
    private final EnumMap<HeightmapType, Heightmap> gtnhlib$heightmaps = new EnumMap<>(HeightmapType.class);

    {
        World self = (World) (Object) this;

        gtnhlib$heightmaps.put(HeightmapType.MOTION_BLOCKING, new CustomHeightmap(self, (world, x, y, z) -> {
            Block block = world.getBlock(x, y, z);
            int meta = world.getBlockMetadata(x, y, z);

            return block.canCollideCheck(meta, true);
        }));

        gtnhlib$heightmaps.put(HeightmapType.MOTION_BLOCKING_NO_LEAVES, new CustomHeightmap(self, (world, x, y, z) -> {
            Block block = world.getBlock(x, y, z);

            if (block.isLeaves(world, x, y, z)) return false;

            int meta = world.getBlockMetadata(x, y, z);

            return block.canCollideCheck(meta, true);
        }));

        gtnhlib$heightmaps.put(HeightmapType.OCEAN_FLOOR, new CustomHeightmap(self, (world, x, y, z) -> {
            Block block = world.getBlock(x, y, z);
            int meta = world.getBlockMetadata(x, y, z);

            return block.canCollideCheck(meta, false);
        }));

        gtnhlib$heightmaps.put(HeightmapType.OCEAN_FLOOR_WG, new CustomHeightmap(self, (world, x, y, z) -> {
            Block block = world.getBlock(x, y, z);
            int meta = world.getBlockMetadata(x, y, z);

            return block.canCollideCheck(meta, false);
        }));

        gtnhlib$heightmaps.put(HeightmapType.WORLD_SURFACE, new CustomHeightmap(self, (world, x, y, z) -> {
            return !world.isAirBlock(x, y, z);
        }));
        gtnhlib$heightmaps.put(HeightmapType.WORLD_SURFACE_WG, new CustomHeightmap(self, (world, x, y, z) -> {
            return !world.isAirBlock(x, y, z);
        }));
    }

    @SuppressWarnings("AddedMixinMembersNamePattern")
    @Override
    public Heightmap getHeightmap(HeightmapType heightmapType) {
        return gtnhlib$heightmaps.get(heightmapType);
    }
}
