package databack.common.tags;

import databack.common.handlers.IDatapackTypeHandler;

/**
 * Implemented by any {@link IDatapackTypeHandler} that manages a tag registry.
 * Exposes {@link #gatherTags()} so that the global {@code TagReloadEvent} subscriber
 * in {@code BuiltinTagRegistries} can delegate without instanceof checks per type.
 */
public interface ITagHandler extends IDatapackTypeHandler {

    void gatherTags();
}
