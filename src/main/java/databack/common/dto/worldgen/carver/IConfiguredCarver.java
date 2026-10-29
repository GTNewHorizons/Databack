package databack.common.dto.worldgen.carver;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import databack.common.worldgen.carver.CarverBuildContext;
import databack.common.worldgen.carver.ConfiguredCarver;

/** A configured carver: an inline one, or a reference to a configured_carver file. */
public interface IConfiguredCarver {

    /// The carver, or null (after logging a warning) when it cannot be built.
    @Nullable
    ConfiguredCarver instantiate(@NotNull CarverBuildContext ctx);
}
