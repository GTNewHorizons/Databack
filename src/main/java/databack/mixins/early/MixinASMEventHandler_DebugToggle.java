package databack.mixins.early;

import java.lang.reflect.Method;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import cpw.mods.fml.common.ModContainer;
import cpw.mods.fml.common.eventhandler.ASMEventHandler;
import databack.common.debug.DebugOverlayEntry;
import databack.common.debug.IEventListenerExt;

@Mixin(value = ASMEventHandler.class, remap = false)
public abstract class MixinASMEventHandler_DebugToggle implements IEventListenerExt {

    @Unique
    private Method db$method;
    @Unique
    private DebugOverlayEntry db$overlayAnnotation;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void db$interceptMethod(Object target, Method method, ModContainer owner, CallbackInfo ci) {
        this.db$method = method;
        this.db$overlayAnnotation = method.getAnnotation(DebugOverlayEntry.class);
    }

    @Override
    public Method db$getWrappedMethod() {
        return db$method;
    }

    @Override
    public @Nullable DebugOverlayEntry db$getOverlayAnnotation() {
        return db$overlayAnnotation;
    }
}
