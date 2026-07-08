package databack.common.meta;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

/**
 * Stateless utility class for parsing {@code pack.mcmeta} JSON into a {@link PackMetadata} DTO.
 * All methods are static; this class is not intended to be instantiated.
 */
public final class PackMetadataParser {

    private PackMetadataParser() {}

    /**
     * Parses and validates the raw JSON content of a {@code pack.mcmeta} file.
     *
     * @param packName human-readable name of the pack, included in all error messages
     * @param json     raw UTF-8 text content of {@code pack.mcmeta}
     * @return a fully validated {@link PackMetadata}
     * @throws DatapackParseException on any violation of validation rules V-1 through V-7
     */
    @Nonnull
    public static PackMetadata parse(@Nonnull String packName, @Nonnull String json) {
        JsonObject root;
        try {
            JsonElement parsed = new JsonParser().parse(json);
            if (!parsed.isJsonObject()) {
                throw new DatapackParseException(
                    "pack.mcmeta for pack '" + packName + "' is not a JSON object");
            }
            root = parsed.getAsJsonObject();
        } catch (JsonParseException e) {
            throw new DatapackParseException(
                "pack.mcmeta for pack '" + packName + "' is not valid JSON: " + e.getMessage(), e);
        }

        PackInfo packInfo = parsePackInfo(packName, root);
        PackFilter filter = parseFilter(packName, root);
        PackOverlays overlays = parseOverlays(packName, root);
        PackFeatures features = parseFeatures(packName, root);

        return new PackMetadata(packInfo, filter, overlays, features);
    }

    @Nonnull
    private static PackInfo parsePackInfo(@Nonnull String packName, @Nonnull JsonObject root) {
        // 'pack' section must be present
        JsonElement packElement = root.get("pack");
        if (packElement == null || !packElement.isJsonObject()) {
            throw new DatapackParseException(
                "pack.mcmeta for pack '" + packName + "': missing or non-object 'pack' section (V-2)");
        }
        JsonObject packObj = packElement.getAsJsonObject();

        // 'pack_format' must be present and be a number
        JsonElement packFormatElement = packObj.get("pack_format");
        if (packFormatElement == null || !packFormatElement.isJsonPrimitive()
            || !packFormatElement.getAsJsonPrimitive().isNumber()) {
            throw new DatapackParseException(
                "pack.mcmeta for pack '" + packName + "': 'pack_format' is missing or not a number (V-3)");
        }
        int packFormat = packFormatElement.getAsInt();

        FormatRange supportedFormats = parseSupportedFormats(packName, packObj);
        JsonElement description = parseDescription(packName, packObj);

        return new PackInfo(packFormat, supportedFormats, description);
    }

    @Nonnull
    private static JsonElement parseDescription(@Nonnull String packName, @Nonnull JsonObject packObj) {
        // 'description' must be present
        JsonElement description = packObj.get("description");
        if (description == null) {
            throw new DatapackParseException(
                "pack.mcmeta for pack '" + packName + "': 'description' is missing (V-4)");
        }
        return description;
    }

    @Nullable
    private static FormatRange parseSupportedFormats(@Nonnull String packName, @Nonnull JsonObject packObj) {
        JsonElement element = packObj.get("supported_formats");
        if (element == null) {
            return null;
        }

        if (element.isJsonPrimitive()) {
            JsonPrimitive primitive = element.getAsJsonPrimitive();
            if (!primitive.isNumber()) {
                throw new DatapackParseException(
                    "pack.mcmeta for pack '" + packName
                        + "': 'supported_formats' primitive must be a number, found: "
                        + element);
            }
            int n = primitive.getAsInt();
            // FormatRange constructor validates min <= max and throws DatapackParseException if not
            return new FormatRange(n, n);
        }

        if (element.isJsonObject()) {
            JsonObject obj = element.getAsJsonObject();

            JsonElement minElement = obj.get("min_inclusive");
            JsonElement maxElement = obj.get("max_inclusive");

            if (minElement == null || !minElement.isJsonPrimitive()
                || !minElement.getAsJsonPrimitive().isNumber()) {
                throw new DatapackParseException(
                    "pack.mcmeta for pack '" + packName
                        + "': 'supported_formats.min_inclusive' is missing or not a number");
            }
            if (maxElement == null || !maxElement.isJsonPrimitive()
                || !maxElement.getAsJsonPrimitive().isNumber()) {
                throw new DatapackParseException(
                    "pack.mcmeta for pack '" + packName
                        + "': 'supported_formats.max_inclusive' is missing or not a number");
            }

            int min = minElement.getAsInt();
            int max = maxElement.getAsInt();
            // FormatRange constructor enforces V-5
            return new FormatRange(min, max);
        }

        throw new DatapackParseException(
            "pack.mcmeta for pack '" + packName
                + "': 'supported_formats' must be an integer or an object, found: "
                + element.getClass().getSimpleName());
    }

    @Nullable
    private static PackFilter parseFilter(@Nonnull String packName, @Nonnull JsonObject root) {
        JsonElement filterElement = root.get("filter");
        if (filterElement == null) {
            return null;
        }
        if (!filterElement.isJsonObject()) {
            throw new DatapackParseException(
                "pack.mcmeta for pack '" + packName + "': 'filter' must be a JSON object");
        }
        JsonObject filterObj = filterElement.getAsJsonObject();

        JsonElement blockElement = filterObj.get("block");
        if (blockElement == null) {
            // 'block' key absent; treat as empty list
            return new PackFilter(new ArrayList<ResourceLocationPattern>());
        }
        if (!blockElement.isJsonArray()) {
            throw new DatapackParseException(
                "pack.mcmeta for pack '" + packName + "': 'filter.block' must be a JSON array");
        }

        JsonArray blockArray = blockElement.getAsJsonArray();
        List<ResourceLocationPattern> patterns = new ArrayList<ResourceLocationPattern>(blockArray.size());
        for (JsonElement entry : blockArray) {
            if (!entry.isJsonObject()) {
                throw new DatapackParseException(
                    "pack.mcmeta for pack '" + packName
                        + "': each entry in 'filter.block' must be a JSON object");
            }
            JsonObject entryObj = entry.getAsJsonObject();

            String namespace = null;
            String path = null;

            JsonElement nsElement = entryObj.get("namespace");
            if (nsElement != null) {
                if (!nsElement.isJsonPrimitive() || !nsElement.getAsJsonPrimitive().isString()) {
                    throw new DatapackParseException(
                        "pack.mcmeta for pack '" + packName
                            + "': 'namespace' in filter block entry must be a string");
                }
                namespace = nsElement.getAsString();
            }

            JsonElement pathElement = entryObj.get("path");
            if (pathElement != null) {
                if (!pathElement.isJsonPrimitive() || !pathElement.getAsJsonPrimitive().isString()) {
                    throw new DatapackParseException(
                        "pack.mcmeta for pack '" + packName
                            + "': 'path' in filter block entry must be a string");
                }
                path = pathElement.getAsString();
            }

            patterns.add(new ResourceLocationPattern(namespace, path));
        }

        return new PackFilter(patterns);
    }

    @Nullable
    private static PackOverlays parseOverlays(@Nonnull String packName, @Nonnull JsonObject root) {
        JsonElement overlaysElement = root.get("overlays");
        if (overlaysElement == null) {
            return null;
        }
        if (!overlaysElement.isJsonObject()) {
            throw new DatapackParseException(
                "pack.mcmeta for pack '" + packName + "': 'overlays' must be a JSON object");
        }
        JsonObject overlaysObj = overlaysElement.getAsJsonObject();

        // 'overlays.entries' must be a JSON array
        JsonElement entriesElement = overlaysObj.get("entries");
        if (entriesElement == null) {
            // No entries key: treat as empty
            return new PackOverlays(new ArrayList<OverlayEntry>());
        }
        if (!entriesElement.isJsonArray()) {
            throw new DatapackParseException(
                "pack.mcmeta for pack '" + packName + "': 'overlays.entries' must be a JSON array (V-6)");
        }

        JsonArray entriesArray = entriesElement.getAsJsonArray();
        List<OverlayEntry> entries = new ArrayList<OverlayEntry>(entriesArray.size());
        for (JsonElement element : entriesArray) {
            entries.add(parseOverlayEntry(packName, element));
        }

        return new PackOverlays(entries);
    }

    @Nonnull
    private static OverlayEntry parseOverlayEntry(@Nonnull String packName, @Nonnull JsonElement element) {
        // Each overlay entry must have 'formats' and 'directory'
        if (!element.isJsonObject()) {
            throw new DatapackParseException(
                "pack.mcmeta for pack '" + packName
                    + "': each overlay entry must be a JSON object (V-7)");
        }
        JsonObject entryObj = element.getAsJsonObject();

        JsonElement formatsElement = entryObj.get("formats");
        if (formatsElement == null) {
            throw new DatapackParseException(
                "pack.mcmeta for pack '" + packName
                    + "': overlay entry is missing 'formats' field (V-7)");
        }

        JsonElement directoryElement = entryObj.get("directory");
        if (directoryElement == null || !directoryElement.isJsonPrimitive()
            || !directoryElement.getAsJsonPrimitive().isString()) {
            throw new DatapackParseException(
                "pack.mcmeta for pack '" + packName
                    + "': overlay entry is missing or has non-string 'directory' field (V-7)");
        }
        String directory = directoryElement.getAsString();

        FormatRange formats = parseFormatRangeElement(packName, "overlay formats", formatsElement);

        return new OverlayEntry(formats, directory);
    }

    /**
     * Parses a format range value that may be either an integer or an object with
     * {@code min_inclusive} / {@code max_inclusive} fields.
     */
    @Nonnull
    private static FormatRange parseFormatRangeElement(
        @Nonnull String packName,
        @Nonnull String fieldName,
        @Nonnull JsonElement element) {

        if (element.isJsonPrimitive()) {
            JsonPrimitive primitive = element.getAsJsonPrimitive();
            if (!primitive.isNumber()) {
                throw new DatapackParseException(
                    "pack.mcmeta for pack '" + packName + "': '" + fieldName
                        + "' primitive must be a number");
            }
            int n = primitive.getAsInt();
            return new FormatRange(n, n);
        }

        if (element.isJsonObject()) {
            JsonObject obj = element.getAsJsonObject();

            JsonElement minElement = obj.get("min_inclusive");
            JsonElement maxElement = obj.get("max_inclusive");

            if (minElement == null || !minElement.isJsonPrimitive()
                || !minElement.getAsJsonPrimitive().isNumber()) {
                throw new DatapackParseException(
                    "pack.mcmeta for pack '" + packName + "': '" + fieldName
                        + ".min_inclusive' is missing or not a number");
            }
            if (maxElement == null || !maxElement.isJsonPrimitive()
                || !maxElement.getAsJsonPrimitive().isNumber()) {
                throw new DatapackParseException(
                    "pack.mcmeta for pack '" + packName + "': '" + fieldName
                        + ".max_inclusive' is missing or not a number");
            }

            int min = minElement.getAsInt();
            int max = maxElement.getAsInt();
            return new FormatRange(min, max);
        }

        throw new DatapackParseException(
            "pack.mcmeta for pack '" + packName + "': '" + fieldName
                + "' must be an integer or an object, found: "
                + element.getClass().getSimpleName());
    }

    @Nullable
    private static PackFeatures parseFeatures(@Nonnull String packName, @Nonnull JsonObject root) {
        JsonElement featuresElement = root.get("features");
        if (featuresElement == null) {
            return null;
        }
        if (!featuresElement.isJsonObject()) {
            throw new DatapackParseException(
                "pack.mcmeta for pack '" + packName + "': 'features' must be a JSON object");
        }
        JsonObject featuresObj = featuresElement.getAsJsonObject();

        JsonElement enabledElement = featuresObj.get("enabled");
        if (enabledElement == null) {
            return new PackFeatures(new ArrayList<String>());
        }
        if (!enabledElement.isJsonArray()) {
            throw new DatapackParseException(
                "pack.mcmeta for pack '" + packName + "': 'features.enabled' must be a JSON array");
        }

        JsonArray enabledArray = enabledElement.getAsJsonArray();
        List<String> enabled = new ArrayList<String>(enabledArray.size());
        for (JsonElement entry : enabledArray) {
            if (!entry.isJsonPrimitive() || !entry.getAsJsonPrimitive().isString()) {
                throw new DatapackParseException(
                    "pack.mcmeta for pack '" + packName
                        + "': each entry in 'features.enabled' must be a string");
            }
            enabled.add(entry.getAsString());
        }

        return new PackFeatures(enabled);
    }
}
