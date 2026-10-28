package databack.common.worldgen.biome.preset;

import java.util.Locale;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import databack.common.worldgen.vanilla.VanillaMappings;

/// What vanilla's preset-building classes may use, and what each use becomes (see [VanillaMappings]).
final class PresetMappings {

    private static final String BIOME_PACKAGE = "net/minecraft/world/level/biome/";

    static final VanillaMappings MAPPINGS = VanillaMappings.builder()
        .mapClass(BIOME_PACKAGE + "Climate", PresetClimate.class)
        .mapClass(BIOME_PACKAGE + "Climate$Parameter", PresetParameter.class)
        .mapClass(BIOME_PACKAGE + "Climate$ParameterPoint", PresetPoint.class)
        .mapClass(BIOME_PACKAGE + "Climate$ParameterList", PresetParameterList.class)
        .mapClass(BIOME_PACKAGE + "MultiNoiseBiomeSourceParameterList$Preset$SourceProvider",
            PresetSourceProvider.class)
        .mapClass("com/mojang/datafixers/util/Pair", PresetPair.class)
        .mapClass("net/minecraft/resources/ResourceKey", String.class)
        .passthrough("java/util/function/Consumer", "java/util/function/Function")
        .redirectStatic("java/util/List", "of", PresetLists.class)
        .constantFields(BIOME_PACKAGE + "Biomes", "Lnet/minecraft/resources/ResourceKey;", PresetMappings::biomeId)
        .constantFields("net/minecraft/SharedConstants", "Z", flag -> Boolean.FALSE)
        .build();

    private PresetMappings() {}

    /// The id of the biome key constant `field`, e.g. `Biomes.PLAINS`. Every vanilla biome key's id is its constant's
    /// name in lower case, in the `minecraft` namespace.
    @NotNull
    @Contract(pure = true)
    static String biomeId(@NotNull String field) {
        return "minecraft:" + field.toLowerCase(Locale.ROOT);
    }
}
