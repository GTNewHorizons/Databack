package databack.mixins.early;

import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.registry.EntityRegistry;
import databack.common.interop.registry.IProxyRegistry;
import databack.common.interop.registry.ProxyEntityRegistry;

@Mixin(value = EntityRegistry.class, remap = false)
public class MixinEntityRegistry_Registry {

    @Inject(method = "doModEntityRegistration", at = @At("RETURN"))
    private void db$hookModEntityRegister(
        Class<? extends Entity> entityClass, String entityName, int id, Object mod, int trackingRange,
        int updateFrequency, boolean sendsVelocityUpdates, CallbackInfo ci
    ) {
        String domain = Loader.instance().activeModContainer().getModId();

        ProxyEntityRegistry.INSTANCE.putEntity(new ResourceLocation(domain, IProxyRegistry.cleanId(entityName)), entityClass);
    }
}
