package databack.common.handlers;

import java.util.List;
import java.util.Set;

import net.minecraft.entity.player.EntityPlayerMP;

import org.jetbrains.annotations.NotNull;

import databack.common.loader.DatapackLoadException;
import databack.common.loader.DatapackLoader;
import databack.common.loader.ResourceId;

/**
 * Defines the contract for type-specific datapack resource processors.
 *
 * <p>The loader calls {@link #handle} for every resource whose type string is registered.
 * Handlers must throw on error; they must not catch and continue.
 */
public interface IDatapackTypeHandler {

    /**
     * Processes the raw byte content of a single datapack resource.
     *
     * @param id      the fully derived resource location for this resource
     * @param content the complete raw byte content of the resource file, already read into memory
     * @throws Exception if the handler cannot process the content; the loader wraps this in a
     *                   {@link DatapackLoadException}
     */
    void handle(@NotNull ResourceId id, byte @NotNull [] content) throws Exception;

    /**
     * Called by {@link DatapackLoader#enumerateAndDispatch(List, Set)} when datapacks are loaded, prior to the
     * handling of any resources.
     *
     * <p>Must not throw. Any exception is caught, logged, and suppressed by the loader.
     */
    default void onLoadStart() {}

    /**
     * Called by {@link DatapackLoader#enumerateAndDispatch(List, Set)} after all datapack resources have been handled.
     *
     * <p>Must not throw. Any exception is caught, logged, and suppressed by the loader.
     */
    default void onLoadFinished() {}

    /**
     * Called by {@link DatapackLoader#unload()} when the server-side overworld unloads.
     * Implementations should clear any state accumulated during {@link #handle} calls for the
     * current world session. Must tolerate being called before any {@link #handle} calls.
     *
     * <p>Must not throw. Any exception is caught, logged, and suppressed by the loader.
     */
    default void onWorldUnload() {}

    /**
     * Called after all datapacks are loaded, and all resources are handled. Syncs the data received by this handler to
     * the specified player.
     */
    default void syncToPlayer(EntityPlayerMP player) {}
}
