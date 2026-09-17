package databack.mixins.early;

import net.minecraft.util.ResourceLocation;
import net.minecraft.world.biome.BiomeGenBase;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import cpw.mods.fml.common.Loader;
import databack.common.interop.registry.IProxyRegistry;
import databack.common.interop.registry.ProxyBiomeRegistry;

@Mixin(BiomeGenBase.class)
public class MixinBiomeGenBase_Registry {

    @Shadow
    public String biomeName;
    @Unique
    private static boolean db$clinit = false;

    @Inject(method = "<clinit>", at = @At("HEAD"))
    private static void db$beginCLInit(CallbackInfo ci) {
        db$clinit = true;
    }

    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void db$endCLInit(CallbackInfo ci) {
        db$clinit = false;
    }

    @Unique
    protected String db$domain;

    @Inject(method = "<init>(IZ)V", at = @At("TAIL"))
    private void db$hookBiomeInit(int p_i1971_1_, boolean register, CallbackInfo ci) {
        this.db$domain = db$clinit ? "minecraft" : Loader.instance().activeModContainer().getModId();
    }

    @Inject(method = "setBiomeName", at = @At("TAIL"))
    private void db$hookNameChange(String name, CallbackInfoReturnable<BiomeGenBase> cir) {
        ProxyBiomeRegistry.INSTANCE.putBiome(new ResourceLocation(db$domain, IProxyRegistry.cleanId(name)), (BiomeGenBase) (Object) this);
    }
}
