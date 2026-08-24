package databack.common.handlers;

import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

import databack.common.loader.PathTrie;
import databack.common.loader.PathTrie.TrieVisitor;

public class DatapackHandlerRegistry {

    private static final PathTrie<IDatapackTypeHandler> HANDLER_REGISTRY = new PathTrie<>();

    public static void registerTypeHandler(String resourceType, Supplier<IDatapackTypeHandler> handler) {
        List<String> path = Arrays.asList(resourceType.split("/"));

        if (HANDLER_REGISTRY.get(path) != null) {
            throw new IllegalStateException("Cannot register two handlers for the same resource (" + String.join("/", path) + "; " + HANDLER_REGISTRY.get(path) + ")");
        }

        HANDLER_REGISTRY.put(path, handler.get());
    }

    public static List<String> findDeepestHandler(List<String> path) {
        return HANDLER_REGISTRY.findDeepestNode(path);
    }

    public static IDatapackTypeHandler getTypeHandler(List<String> path) {
        return HANDLER_REGISTRY.get(path);
    }

    public static void forEach(TrieVisitor<IDatapackTypeHandler> visitor) {
        HANDLER_REGISTRY.dfs(visitor);
    }

    /** Clears all registered handlers. Safe to call between worlds or in tests. */
    public static void clearAll() {
        HANDLER_REGISTRY.clear();
    }

    /** @deprecated Use {@link #clearAll()} */
    @Deprecated
    public static void clearForTesting() {
        clearAll();
    }
}
