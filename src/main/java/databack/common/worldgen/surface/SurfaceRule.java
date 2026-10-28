package databack.common.worldgen.surface;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.gtnewhorizon.gtnhlib.util.data.ImmutableBlockMeta;

/// A surface rule (docs/worldgen/surface-rules.md section 2): the block to place, or null for "no result".
@FunctionalInterface
public interface SurfaceRule {

    @Nullable
    ImmutableBlockMeta apply(@NotNull SurfaceContext ctx);
}
