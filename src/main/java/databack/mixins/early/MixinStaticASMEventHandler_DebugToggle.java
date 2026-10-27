package databack.mixins.early;

import java.lang.reflect.Method;

import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Type;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.gtnewhorizon.gtnhlib.eventbus.MethodInfo;
import com.gtnewhorizon.gtnhlib.eventbus.StaticASMEventHandler;

import cpw.mods.fml.common.eventhandler.Event;
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

    @Inject(method = "invoke", at = @At(value = "INVOKE", target = "Lcpw/mods/fml/common/eventhandler/IEventListener;invoke(Lcpw/mods/fml/common/eventhandler/Event;)V", shift = Shift.AFTER))
    private void db$invokeHook(Event event, CallbackInfo ci) {
        db$getWrappedMethod();
    }

    @Override
    public String db$getHandlerId() {
        var annotation = db$getOverlayAnnotation();

        if (annotation != null) return annotation.title();

        return db$methodInfo.getDeclaringClass() + "." + db$methodInfo.name;
    }

    @Override
    public Method db$getWrappedMethod() {
        if (db$method == null) {
            try {
                Class<?> clazz = Class.forName(db$methodInfo.declaringClass);

                Type[] argTypes = Type.getArgumentTypes(db$methodInfo.desc);
                Class<?>[] paramTypes = new Class<?>[argTypes.length];
                for (int i = 0; i < argTypes.length; i++) {
                    paramTypes[i] = Class.forName(argTypes[i].getClassName());
                }

                db$method = clazz.getDeclaredMethod(db$methodInfo.name, paramTypes);
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
