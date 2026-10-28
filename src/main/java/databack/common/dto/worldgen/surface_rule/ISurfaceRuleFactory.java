package databack.common.dto.worldgen.surface_rule;

import org.jetbrains.annotations.NotNull;

import databack.common.worldgen.surface.SurfaceBuildContext;
import databack.common.worldgen.surface.SurfaceRule;

/// A `surface_rule` node (rule types), as in noise settings JSON.
public interface ISurfaceRuleFactory {

    @NotNull
    SurfaceRule instantiate(@NotNull SurfaceBuildContext ctx);
}
