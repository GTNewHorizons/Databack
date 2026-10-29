package databack.common.worldgen.orevein;

import org.jetbrains.annotations.NotNull;

/// The blocks ore veins place (docs/worldgen/ore-veins.md, section 2), by their modern ids.
public enum VeinBlock {

    GRANITE("minecraft:granite"),
    COPPER_ORE("minecraft:copper_ore"),
    RAW_COPPER_BLOCK("minecraft:raw_copper_block"),
    TUFF("minecraft:tuff"),
    DEEPSLATE_IRON_ORE("minecraft:deepslate_iron_ore"),
    RAW_IRON_BLOCK("minecraft:raw_iron_block");

    @NotNull
    public final String id;

    VeinBlock(@NotNull String id) {
        this.id = id;
    }
}
