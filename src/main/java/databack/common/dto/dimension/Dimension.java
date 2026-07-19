package databack.common.dto.dimension;

import org.jetbrains.annotations.NotNull;

@SuppressWarnings({ "unused", "NotNullFieldNotInitialized" })
public class Dimension {

    /** Dimension type ID (e.g. {@code "minecraft:overworld"}). */
    @NotNull
    public String type;

    @NotNull
    public BuiltinDimensionGenerators.DimensionGenerator generator;
}
