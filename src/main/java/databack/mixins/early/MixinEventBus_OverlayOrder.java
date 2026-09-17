package databack.mixins.early;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.Minecraft;

import cpw.mods.fml.common.eventhandler.Event;
import cpw.mods.fml.common.eventhandler.EventBus;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.IEventListener;
import databack.common.debug.DebugOverlayRegistry;
import databack.common.debug.IEventListenerExt;
import net.minecraftforge.client.event.RenderGameOverlayEvent;

@Mixin(value = EventBus.class, remap = false)
public class MixinEventBus_OverlayOrder {

    @Final
    @Shadow
    private int busID;

    @Inject(method = "post", at = @At("HEAD"), cancellable = true)
    private void db$sortOverlayHandlers(Event event, CallbackInfoReturnable<Boolean> cir) {
        if (!(event instanceof RenderGameOverlayEvent.Text)) return;

        IEventListener[] original = event.getListenerList().getListeners(busID);

        // Build a rank map from the registry's current order.
        List<String> order = DebugOverlayRegistry.getOrder();
        Map<String, Integer> rank = new HashMap<>(order.size() * 2);
        for (int i = 0; i < order.size(); i++) {
            rank.put(order.get(i), i);
        }

        // The listener array interleaves EventPriority sentinels with actual listeners:
        //   [HIGHEST_sentinel, h1, h2, NORMAL_sentinel, n1, n2, ...]
        // We sort actual listeners within each priority group, preserving sentinels.
        List<IEventListener> sorted = new ArrayList<>(original.length);
        List<IEventListener> group = new ArrayList<>();

        for (IEventListener l : original) {
            if (l instanceof EventPriority) {
                // Flush the previous group (sorted), then emit the new sentinel.
                if (!group.isEmpty()) {
                    group.sort((a, b) -> Integer.compare(db$rank(a, rank), db$rank(b, rank)));
                    sorted.addAll(group);
                    group.clear();
                }
                sorted.add(l);
            } else {
                group.add(l);
            }
        }
        if (!group.isEmpty()) {
            group.sort(Comparator.comparingInt(a -> db$rank(a, rank)));
            sorted.addAll(group);
        }

        // Invoke in sorted order, applying toggle and showDebugInfo shim per-handler.
        // Capturing the real value once; ALWAYS-mode handlers temporarily see it as true.
        boolean realShowDebugInfo = Minecraft.getMinecraft().gameSettings.showDebugInfo;
        for (IEventListener l : sorted) {
            if (l instanceof EventPriority) {
                l.invoke(event); // priority sentinel — updates event phase, no-op otherwise
                continue;
            }
            if (l instanceof IEventListenerExt ext) {
                Method method = ext.db$getWrappedMethod();
ke
                method.toString()

                String id = ((IEventListenerExt) l).db$getReadable();
                DebugOverlayRegistry.registerHandler(id);
                if (!DebugOverlayRegistry.isHandlerEnabled(id)) continue;
                Minecraft.getMinecraft().gameSettings.showDebugInfo =
                    DebugOverlayRegistry.isAlwaysOn(id) || realShowDebugInfo;
                l.invoke(event);
                Minecraft.getMinecraft().gameSettings.showDebugInfo = realShowDebugInfo;
            } else {
                l.invoke(event);
            }
        }

        cir.setReturnValue(event.isCancelable() && event.isCanceled());
    }

    @Unique
    private static int db$rank(IEventListener l, Map<String, Integer> rank) {
        if (l instanceof IEventListenerExt ext) {
            Integer r = rank.get(((IEventListenerExt) l).db$getReadable());
            if (r != null) return r;
        }
        return Integer.MAX_VALUE;
    }
}
