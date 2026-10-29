package databack.common.worldgen.carver;

import org.jetbrains.annotations.NotNull;

import databack.common.worldgen.carver.standin.CvCarver;
import databack.common.worldgen.carver.standin.CvCarverConfiguration;

/// A carver type with its configuration: one entry of a biome's `carvers`.
public final class ConfiguredCarver {

    @NotNull
    public final CvCarver carver;
    @NotNull
    public final CvCarverConfiguration config;

    public ConfiguredCarver(@NotNull CvCarver carver, @NotNull CvCarverConfiguration config) {
        this.carver = carver;
        this.config = config;
    }
}
