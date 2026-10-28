package databack.common.biome;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.Map;
import java.util.OptionalInt;
import java.util.TreeMap;
import java.util.function.IntPredicate;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import databack.common.annotation.NotThreadSafe;

/// The numeric biome id assigned to each [ModernBiome], shared by every world and kept across restarts: chunks store
/// these numbers, so an assignment never changes once made.
@NotThreadSafe
public final class ModernBiomeIds {

    private static final Logger LOGGER = LogManager.getLogger("databack-biomes");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    @Nullable
    private final File file;
    private final Map<String, Integer> ids = new TreeMap<>();

    private ModernBiomeIds(@Nullable File file) {
        this.file = file;
    }

    /// Loads the assignments from `file` (none if it does not exist). [#assign] writes new ones back to it.
    @NotNull
    public static ModernBiomeIds load(@NotNull File file) {
        ModernBiomeIds result = new ModernBiomeIds(file);
        if (!file.isFile()) return result;

        try (Reader r = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            JsonObject json = GSON.fromJson(r, JsonObject.class);
            if (json != null) json.entrySet().forEach(e -> result.ids.put(e.getKey(), e.getValue().getAsInt()));
        } catch (IOException | RuntimeException e) {
            LOGGER.error("Could not read modern biome ids from {}; existing worlds may show the wrong biomes", file, e);
        }

        return result;
    }

    /// Assignments kept in memory only.
    @NotNull
    static ModernBiomeIds inMemory() {
        return new ModernBiomeIds(null);
    }

    /// The id assigned to `biome`, if any.
    @Nullable
    public Integer get(@NotNull String biome) {
        return ids.get(biome);
    }

    /// Whether any biome has been assigned `numericId`.
    public boolean isAssigned(int numericId) {
        return ids.containsValue(numericId);
    }

    @NotNull
    public Map<String, Integer> all() {
        return Collections.unmodifiableMap(ids);
    }

    /// Assigns `biome` the lowest id below `limit` that `free` accepts and no other biome has been assigned, replacing
    /// any previous assignment (e.g. one another mod's biome has since taken), and saves. Empty, with the previous
    /// assignment kept, when there is none.
    @NotNull
    public OptionalInt assign(@NotNull String biome, int limit, @NotNull IntPredicate free) {
        Integer previous = ids.remove(biome);

        for (int id = 0; id < limit; id++) {
            if (isAssigned(id) || !free.test(id)) continue;

            ids.put(biome, id);
            save();
            return OptionalInt.of(id);
        }

        if (previous != null) ids.put(biome, previous);
        return OptionalInt.empty();
    }

    private void save() {
        if (file == null) return;

        JsonObject json = new JsonObject();
        ids.forEach(json::addProperty);

        try {
            File parent = file.getParentFile();
            if (parent != null) Files.createDirectories(parent.toPath());
            try (Writer w = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8)) {
                GSON.toJson(json, w);
            }
        } catch (IOException e) {
            LOGGER.error("Could not save modern biome ids to {}", file, e);
        }
    }
}
