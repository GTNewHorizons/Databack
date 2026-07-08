package databack.common.meta;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.google.gson.JsonElement;

/**
 * Holds the mandatory {@code pack} section of a {@code pack.mcmeta} file.
 * Immutable value class.
 */
public final class PackInfo {

    private final int packFormat;

    @Nullable
    private final FormatRange supportedFormats;

    @Nonnull
    private final JsonElement description;

    /**
     * @param packFormat       the declared format integer
     * @param supportedFormats the optional supported formats range; may be null
     * @param description      the description element; must not be null
     */
    public PackInfo(int packFormat, @Nullable FormatRange supportedFormats, @Nonnull JsonElement description) {
        this.packFormat = packFormat;
        this.supportedFormats = supportedFormats;
        this.description = description;
    }

    public int getPackFormat() {
        return packFormat;
    }

    @Nullable
    public FormatRange getSupportedFormats() {
        return supportedFormats;
    }

    @Nonnull
    public JsonElement getDescription() {
        return description;
    }
}
