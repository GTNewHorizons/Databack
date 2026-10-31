package databack.common.worldgen.cubic;

import java.util.LinkedHashMap;
import java.util.Map;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/// The last `capacity` values put, by key, dropping the least recently used first. Thread-safe.
final class LruCache<V> {

    private final LinkedHashMap<Long, V> values;

    LruCache(int capacity) {
        this.values = new LinkedHashMap<Long, V>(capacity * 2, 0.75f, true) {

            @Override
            protected boolean removeEldestEntry(Map.Entry<Long, V> eldest) {
                return size() > capacity;
            }
        };
    }

    @Nullable
    synchronized V get(long key) {
        return values.get(key);
    }

    synchronized void put(long key, @NotNull V value) {
        values.put(key, value);
    }
}
