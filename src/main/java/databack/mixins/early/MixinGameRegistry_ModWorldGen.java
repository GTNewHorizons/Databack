package databack.mixins.early;

import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import cpw.mods.fml.common.registry.GameRegistry;
import databack.DatabackConfig;
import databack.common.worldgen.ModernWorldType;

/// Skips other mods' world generators (`IWorldGenerator`s) in Databack worlds when
/// [DatabackConfig#disableModWorldGeneration] is set, so every block comes from Databack.
@Mixin(value = GameRegistry.class, remap = false)
public class MixinGameRegistry_ModWorldGen {

    @Inject(method = "generateWorld", at = @At("HEAD"), cancellable = true)
    private static void db$skipModWorldGen(
        int chunkX, int chunkZ, World world, IChunkProvider chunkGenerator, IChunkProvider chunkProvider,
        CallbackInfo ci
    ) {
        if (!DatabackConfig.disableModWorldGeneration) return;
        if (world.getWorldInfo().getTerrainType() instanceof ModernWorldType) {
            ci.cancel();
        }
    }
}
