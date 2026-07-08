package databack.common.meta;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Signals a fatal, unrecoverable parse or validation failure for a {@code pack.mcmeta} file.
 */
public class DatapackParseException extends RuntimeException {

    public DatapackParseException(@Nonnull String message) {
        super(message);
    }

    public DatapackParseException(@Nonnull String message, @Nullable Throwable cause) {
        super(message, cause);
    }
}
