package databack.mixins.early;

import net.minecraft.util.ResourceLocation;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.biome.BiomeGenMutated;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import databack.common.interop.registry.IProxyRegistry;
import databack.common.interop.registry.ProxyBiomeRegistry;

@Mixin(BiomeGenMutated.class)
public class MixinBiomeGenMutated_Registry extends MixinBiomeGenBase_Registry {

    @Inject(method = "<init>", at = @At("TAIL"))
    private void db$mutatedInit(int p_i45381_1_, BiomeGenBase p_i45381_2_, CallbackInfo ci) {
        ProxyBiomeRegistry.INSTANCE.putBiome(new ResourceLocation(db$domain, IProxyRegistry.cleanId(this.biomeName)), (BiomeGenBase) (Object) this);
    }
}
