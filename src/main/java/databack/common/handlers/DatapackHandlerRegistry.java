package databack.common.handlers;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Supplier;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.relauncher.Side;

public class DatapackHandlerRegistry {

    private static final Map<String, IDatapackTypeHandler> SERVER_HANDLER_REGISTRY = new HashMap<>();
    private static final Map<String, IDatapackTypeHandler> CLIENT_HANDLER_REGISTRY = new HashMap<>();

    public static void registerTypeHandler(String resourceType, Supplier<IDatapackTypeHandler> handler) {
        if (SERVER_HANDLER_REGISTRY.containsKey(resourceType)) {
            throw new IllegalStateException("Cannot register two handlers for the same resource (" + resourceType + "; " + SERVER_HANDLER_REGISTRY.get(resourceType) + ")");
        }

        SERVER_HANDLER_REGISTRY.put(resourceType, handler.get());
        CLIENT_HANDLER_REGISTRY.put(resourceType, handler.get());
    }

    public static IDatapackTypeHandler getTypeHandler(String resourceType) {
        return getTypeHandler(resourceType, FMLCommonHandler.instance().getEffectiveSide());
    }

    public static IDatapackTypeHandler getTypeHandler(String resourceType, Side side) {
        return switch (side) {
            case CLIENT -> CLIENT_HANDLER_REGISTRY.get(resourceType);
            case SERVER -> SERVER_HANDLER_REGISTRY.get(resourceType);
        };
    }

    public static Set<Entry<String, IDatapackTypeHandler>> entrySet() {
        return entrySet(FMLCommonHandler.instance().getEffectiveSide());
    }

    public static Set<Entry<String, IDatapackTypeHandler>> entrySet(Side side) {
        return switch (side) {
            case CLIENT -> CLIENT_HANDLER_REGISTRY.entrySet();
            case SERVER -> SERVER_HANDLER_REGISTRY.entrySet();
        };
    }

    /** Clears all registered handlers. Only for use in tests. */
    public static void clearForTesting() {
        SERVER_HANDLER_REGISTRY.clear();
        CLIENT_HANDLER_REGISTRY.clear();
    }
}
