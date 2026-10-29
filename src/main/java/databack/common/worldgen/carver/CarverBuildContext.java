package databack.common.worldgen.carver;

import java.util.function.Function;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.google.gson.JsonElement;

import databack.common.worldgen.carver.standin.CvBlockSet;

/// What configured carver DTOs need to become [ConfiguredCarver]s.
public final class CarverBuildContext {

    @NotNull
    public final VanillaCarvers carvers;
    @NotNull
    private final Function<JsonElement, CvBlockSet> blockSets;
    @NotNull
    private final Function<String, ConfiguredCarver> references;

    /// `blockSets` turns a `replaceable` value (block ids or a tag) into a set; `references` resolves a configured
    /// carver id, or gives null if it is unknown.
    public CarverBuildContext(
        @NotNull VanillaCarvers carvers, @NotNull Function<JsonElement, CvBlockSet> blockSets,
        @NotNull Function<String, ConfiguredCarver> references
    ) {
        this.carvers = carvers;
        this.blockSets = blockSets;
        this.references = references;
    }

    @NotNull
    public CvBlockSet blockSet(@Nullable JsonElement replaceable) {
        return blockSets.apply(replaceable);
    }

    @Nullable
    public ConfiguredCarver reference(@NotNull String id) {
        return references.apply(id);
    }
}
