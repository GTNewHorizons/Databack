package databack.mixins.early;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.util.ResourceLocation;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import cpw.mods.fml.common.Loader;
import databack.common.interop.registry.IProxyRegistry;
import databack.common.interop.registry.ProxyEntityRegistry;

@Mixin(EntityList.class)
public class MixinEntityList_Registry {

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

    @Inject(method = "addMapping(Ljava/lang/Class;Ljava/lang/String;I)V", at = @At("TAIL"))
    private static void db$hookAddMapping(Class<? extends Entity> entity, String name, int id, CallbackInfo ci) {
        String domain = db$clinit ? "minecraft" : Loader.instance().activeModContainer().getModId();

        ProxyEntityRegistry.INSTANCE.putEntity(new ResourceLocation(domain, IProxyRegistry.cleanId(name)), entity);
    }
}
