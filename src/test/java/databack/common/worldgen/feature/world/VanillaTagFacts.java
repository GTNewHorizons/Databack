package databack.common.worldgen.feature.world;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.NotNull;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import databack.common.worldgen.density.VanillaWorldgen;
import databack.common.worldgen.feature.standin.FtBlockFacts;
import databack.common.worldgen.feature.standin.FtBlockState;

/// [LegacyBlockFacts] with block tags read from the extracted vanilla datapack, for tests, which load no tags.
public final class VanillaTagFacts implements FtBlockFacts {

    private static final Path TAGS = VanillaWorldgen.WORLDGEN.getParent().resolve("tags/block");

    @NotNull
    private final LegacyBlockFacts legacy;
    private final Map<String, Set<String>> tags = new HashMap<>();

    public VanillaTagFacts(@NotNull LegacyStates states) {
        this.legacy = new LegacyBlockFacts(states);
    }

    @Override
    public synchronized boolean isInTag(@NotNull String blockId, @NotNull String tagId) {
        return blocks(tagId).contains(blockId);
    }

    /// Every block of `tagId`, nested tags included; none for unknown tags.
    private Set<String> blocks(String tagId) {
        Set<String> blocks = tags.get(tagId);
        if (blocks != null) return blocks;

        blocks = new HashSet<>();
        tags.put(tagId, blocks);
        Path file = TAGS.resolve(tagId.substring(tagId.indexOf(':') + 1) + ".json");
        if (!Files.exists(file)) return blocks;

        try (Reader r = Files.newBufferedReader(file)) {
            for (JsonElement value : new JsonParser().parse(r).getAsJsonObject().getAsJsonArray("values")) {
                String entry = value.isJsonObject() ? value.getAsJsonObject().get("id").getAsString()
                    : value.getAsString();
                if (entry.startsWith("#")) {
                    blocks.addAll(blocks(entry.substring(1)));
                } else {
                    blocks.add(entry);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return blocks;
    }

    @Override
    public boolean isReplaceable(@NotNull FtBlockState state) {
        return legacy.isReplaceable(state);
    }

    @Override
    public boolean isSolidRender(@NotNull FtBlockState state) {
        return legacy.isSolidRender(state);
    }

    @Override
    public boolean isSolid(@NotNull FtBlockState state) {
        return legacy.isSolid(state);
    }
}
