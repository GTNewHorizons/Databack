package databack.common.worldgen.surface;

import org.jetbrains.annotations.NotNull;

/// A surface rule condition (docs/worldgen/surface-rules.md section 3).
@FunctionalInterface
public interface SurfaceCondition {

    boolean test(@NotNull SurfaceContext ctx);
}
