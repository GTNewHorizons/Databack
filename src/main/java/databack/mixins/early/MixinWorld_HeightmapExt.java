package databack.mixins.early;

import java.util.EnumMap;

import net.minecraft.block.Block;
import net.minecraft.profiler.Profiler;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.storage.ISaveHandler;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import databack.common.interop.heightmap.CustomHeightmap;
import databack.common.interop.heightmap.Heightmap;
import databack.common.interop.heightmap.HeightmapType;
import databack.common.interop.heightmap.WorldHeightmapExt;

@Mixin(World.class)
public class MixinWorld_HeightmapExt implements WorldHeightmapExt {

    @Unique
    private EnumMap<HeightmapType, Heightmap> db$heightmaps;

    @Inject(method = "<init>(Lnet/minecraft/world/storage/ISaveHandler;Ljava/lang/String;Lnet/minecraft/world/WorldProvider;Lnet/minecraft/world/WorldSettings;Lnet/minecraft/profiler/Profiler;)V", at = @At("RETURN"))
    private void db$hookInit(
        ISaveHandler p_i45368_1_, String p_i45368_2_, WorldProvider p_i45368_3_, WorldSettings p_i45368_4_,
        Profiler p_i45368_5_, CallbackInfo ci
    ) {
        db$initHeightmaps();
    }

    @Inject(method = "<init>(Lnet/minecraft/world/storage/ISaveHandler;Ljava/lang/String;Lnet/minecraft/world/WorldSettings;Lnet/minecraft/world/WorldProvider;Lnet/minecraft/profiler/Profiler;)V", at = @At("RETURN"))
    private void db$hookInit(
        ISaveHandler p_i45369_1_, String p_i45369_2_, WorldSettings p_i45369_3_, WorldProvider p_i45369_4_,
        Profiler p_i45369_5_, CallbackInfo ci
    ) {
        db$initHeightmaps();
    }

    @Unique
    private void db$initHeightmaps() {
        db$heightmaps = new EnumMap<>(HeightmapType.class);

        World self = (World) (Object) this;

        db$heightmaps.put(HeightmapType.MOTION_BLOCKING, new CustomHeightmap(self, (world, x, y, z) -> {
            Block block = world.getBlock(x, y, z);
            int meta = world.getBlockMetadata(x, y, z);

            return block.canCollideCheck(meta, true);
        }));

        db$heightmaps.put(HeightmapType.MOTION_BLOCKING_NO_LEAVES, new CustomHeightmap(self, (world, x, y, z) -> {
            Block block = world.getBlock(x, y, z);

            if (block.isLeaves(world, x, y, z)) return false;

            int meta = world.getBlockMetadata(x, y, z);

            return block.canCollideCheck(meta, true);
        }));

        db$heightmaps.put(HeightmapType.OCEAN_FLOOR, new CustomHeightmap(self, (world, x, y, z) -> {
            Block block = world.getBlock(x, y, z);
            int meta = world.getBlockMetadata(x, y, z);

            return block.canCollideCheck(meta, false);
        }));

        db$heightmaps.put(HeightmapType.OCEAN_FLOOR_WG, new CustomHeightmap(self, (world, x, y, z) -> {
            Block block = world.getBlock(x, y, z);
            int meta = world.getBlockMetadata(x, y, z);

            return block.canCollideCheck(meta, false);
        }));

        db$heightmaps.put(HeightmapType.WORLD_SURFACE, new CustomHeightmap(self, (world, x, y, z) -> {
            return !world.isAirBlock(x, y, z);
        }));
        db$heightmaps.put(HeightmapType.WORLD_SURFACE_WG, new CustomHeightmap(self, (world, x, y, z) -> {
            return !world.isAirBlock(x, y, z);
        }));
    }

    @SuppressWarnings("AddedMixinMembersNamePattern")
    @Override
    public Heightmap getHeightmap(HeightmapType heightmapType) {
        return db$heightmaps.get(heightmapType);
    }
}
