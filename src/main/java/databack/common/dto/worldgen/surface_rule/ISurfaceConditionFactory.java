package databack.common.dto.worldgen.surface_rule;

import org.jetbrains.annotations.NotNull;

import databack.common.worldgen.surface.SurfaceBuildContext;
import databack.common.worldgen.surface.SurfaceCondition;

/// A surface rule condition node, as in noise settings JSON.
public interface ISurfaceConditionFactory {

    @NotNull
    SurfaceCondition instantiate(@NotNull SurfaceBuildContext ctx);
}
