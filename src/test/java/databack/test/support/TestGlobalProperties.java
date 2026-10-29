package databack.test.support;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.spongepowered.asm.service.IGlobalPropertyService;
import org.spongepowered.asm.service.IPropertyKey;

/// Mixin's global properties for tests, in a map; the game's service keeps them on LaunchWrapper's blackboard, which
/// doesn't exist here. Found through `META-INF/services` ahead of that one (test resources come first on the class
/// path).
public final class TestGlobalProperties implements IGlobalPropertyService {

    private static final class Key implements IPropertyKey {

        final String name;

        Key(String name) {
            this.name = name;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key && ((Key) o).name.equals(name);
        }

        @Override
        public int hashCode() {
            return name.hashCode();
        }

        @Override
        public String toString() {
            return name;
        }
    }

    private final Map<IPropertyKey, Object> properties = new ConcurrentHashMap<>();

    @Override
    public IPropertyKey resolveKey(String name) {
        return new Key(name);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getProperty(IPropertyKey key) {
        return (T) properties.get(key);
    }

    @Override
    public void setProperty(IPropertyKey key, Object value) {
        properties.put(key, value);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getProperty(IPropertyKey key, T defaultValue) {
        return (T) properties.getOrDefault(key, defaultValue);
    }

    @Override
    public String getPropertyString(IPropertyKey key, String defaultValue) {
        Object value = properties.get(key);
        return value == null ? defaultValue : value.toString();
    }
}
