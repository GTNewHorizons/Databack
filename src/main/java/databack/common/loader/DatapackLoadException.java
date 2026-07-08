package databack.common.loader;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Signals any fatal, unrecoverable error during the datapack loading pipeline.
 * Extends {@link RuntimeException} and propagates unchecked to the caller.
 */
public class DatapackLoadException extends RuntimeException {

    public DatapackLoadException(@Nonnull String message) {
        super(message);
    }

    public DatapackLoadException(@Nonnull String message, @Nullable Throwable cause) {
        super(message, cause);
    }
}
