package databack.common.handlers;

import java.lang.ref.WeakReference;
import java.util.LinkedList;
import java.util.function.Supplier;

import com.gtnewhorizon.gtnhlib.eventbus.EventBusSubscriber;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import databack.common.loader.DatapackEvent.DatapackStartLoadingEvent;

@EventBusSubscriber
public class DatapackHandle<T> implements Supplier<T> {

    private final Supplier<T> getter;

    private boolean hasValue = false;
    private T value;

    private static final LinkedList<WeakReference<DatapackHandle<?>>> HANDLES = new LinkedList<>();

    public DatapackHandle(Supplier<T> getter) {
        this.getter = getter;
        HANDLES.add(new WeakReference<>(this));
    }

    public void clear() {
        value = null;
        hasValue = false;
    }

    @Override
    public T get() {
        if (!hasValue) {
            value = getter.get();
            hasValue = true;
        }

        return value;
    }

    @SubscribeEvent
    public static void onDatapackLoadStart(DatapackStartLoadingEvent event) {
        var iter = HANDLES.iterator();

        while (iter.hasNext()) {
            var ref = iter.next();

            var handle = ref.get();

            if (handle == null) {
                iter.remove();
                continue;
            }

            handle.clear();
        }
    }
}
