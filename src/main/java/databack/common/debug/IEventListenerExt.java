package databack.common.debug;

import java.lang.reflect.Method;

import org.jetbrains.annotations.Nullable;

import cpw.mods.fml.common.eventhandler.IEventListener;

/**
 * Mixin interface added to ASMEventHandler and StaticASMEventHandler so that
 * their internal "readable" description string can be read by the EventBus
 * ordering mixin without requiring reflective field access at runtime.
 */
public interface IEventListenerExt extends IEventListener {
    Method db$getWrappedMethod();

    @Nullable
    DebugOverlayEntry db$getOverlayAnnotation();
}
