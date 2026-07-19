package databack.common.dto.worldgen.world_preset;

import java.util.Map;

import org.jetbrains.annotations.NotNull;

import databack.common.dto.dimension.Dimension;

/**
 * A world preset, which maps dimension IDs to their full {@link Dimension} definitions.
 *
 * <p>World presets take priority over the {@code dimension/} folder when active.
 */
@SuppressWarnings({ "unused", "NotNullFieldNotInitialized" })
public class WorldPreset {

    /** Maps dimension resource IDs (e.g. {@code "minecraft:overworld"}) to their definitions. */
    @NotNull
    public Map<String, Dimension> dimensions;
}
