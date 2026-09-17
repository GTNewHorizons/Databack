package databack.mixins.early;

import java.lang.reflect.Method;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.gtnewhorizon.gtnhlib.eventbus.MethodInfo;
import com.gtnewhorizon.gtnhlib.eventbus.StaticASMEventHandler;

import databack.common.debug.DebugOverlayEntry;
import databack.common.debug.IEventListenerExt;

@Mixin(value = StaticASMEventHandler.class, remap = false)
public abstract class MixinStaticASMEventHandler_DebugToggle implements IEventListenerExt {

    @Unique
    private MethodInfo db$methodInfo;

    @Unique
    private Method db$method;
    @Unique
    private DebugOverlayEntry db$overlayAnnotation;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void db$interceptMethod(MethodInfo method, CallbackInfo ci) {
        this.db$methodInfo = method;
    }

    @Override
    public Method db$getWrappedMethod() {
        if (db$method == null) {
            try {
                Class<?> clazz = Class.forName(db$methodInfo.declaringClass);

                db$method = clazz.getDeclaredMethod(db$methodInfo.name);
                db$overlayAnnotation = db$method.getAnnotation(DebugOverlayEntry.class);
            } catch (ClassNotFoundException | NoSuchMethodException e) {
                throw new RuntimeException(e);
            }
        }

        return db$method;
    }

    @Override
    public @Nullable DebugOverlayEntry db$getOverlayAnnotation() {
        return db$overlayAnnotation;
    }
}
