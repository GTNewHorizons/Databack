package databack.common.worldgen.biome;

import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import databack.common.dto.dimension.BuiltinDimensionGenerators.MultiNoiseBiomes;
import databack.common.dto.dimension.BuiltinDimensionGenerators.MultiNoisePresetIdRef;
import databack.common.dto.dimension.BuiltinDimensionGenerators.MultiNoisePresetInlineRef;
import databack.common.dto.worldgen.multi_noise_biome_source.MultiNoiseBiomeSourceParameterList;
import databack.common.handlers.MultiNoiseBiomeSourceParameterListHandler;
import databack.common.worldgen.biome.preset.VanillaBiomePresets;

/// Resolves a `minecraft:multi_noise` biome source's parameter list: its inline `biomes`, or the preset its `preset`
/// names (directly or through a `worldgen/multi_noise_biome_source_parameter_list`; spec section 2).
public final class MultiNoiseParameters {

    private static final Logger LOGGER = LogManager.getLogger("databack-biome-source");

    private MultiNoiseParameters() {}

    /// Resolves against the loaded datapacks and the vanilla presets. Empty (after logging) when nothing usable is
    /// found; the source then generates no biomes.
    @NotNull
    @Contract("_, _ -> new")
    public static List<ClimateEntry> resolve(@NotNull MultiNoiseBiomes source, @NotNull String name) {
        return resolve(source, name, MultiNoiseParameters::parameterList, VanillaBiomePresets::get);
    }

    @Nullable
    private static MultiNoiseBiomeSourceParameterList parameterList(String id) {
        return MultiNoiseBiomeSourceParameterListHandler.RT.getHandler().getParameterList(id);
    }

    /// Resolves with the given lookups: `parameterLists` finds a parameter list resource by id; `presets` builds a
    /// preset by id (both null when unknown).
    @NotNull
    @Contract("_, _, _, _ -> new")
    static List<ClimateEntry> resolve(
        @NotNull MultiNoiseBiomes source, @NotNull String name,
        @NotNull Function<String, @Nullable MultiNoiseBiomeSourceParameterList> parameterLists,
        @NotNull Function<String, @Nullable List<ClimateEntry>> presets
    ) {
        if (source.biomes != null) return InlineClimateEntries.convert(source.biomes, name);

        String presetId = presetId(source, name, parameterLists);
        if (presetId == null) return Collections.emptyList();

        List<ClimateEntry> entries = presets.apply(presetId);
        return entries == null ? Collections.emptyList() : entries;
    }

    @Nullable
    private static String presetId(
        MultiNoiseBiomes source, String name, Function<String, MultiNoiseBiomeSourceParameterList> parameterLists
    ) {
        if (source.preset instanceof MultiNoisePresetInlineRef inline) return inline.parameterList.preset;

        if (source.preset instanceof MultiNoisePresetIdRef ref) {
            MultiNoiseBiomeSourceParameterList list = parameterLists.apply(ref.id);
            if (list != null) return list.preset;
            LOGGER.warn("Biome source {}: unknown parameter list {}; it will generate no biomes", name, ref.id);
            return null;
        }

        LOGGER.warn("Biome source {} has neither biomes nor a preset; it will generate no biomes", name);
        return null;
    }
}
