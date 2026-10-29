package databack.common.worldgen.carver.standin;

import java.util.function.Predicate;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/// Stand-in for a set of blocks (a carver's `replaceable` tag), over the states' payloads ([CvStatePool]).
@ApiStatus.Internal
public final class CvBlockSet {

    @NotNull
    private final CvStatePool pool;
    @NotNull
    private final Predicate<Object> contains;

    public CvBlockSet(@NotNull CvStatePool pool, @NotNull Predicate<Object> contains) {
        this.pool = pool;
        this.contains = contains;
    }

    boolean contains(@NotNull CvBlockState state) {
        return contains.test(pool.payload(state));
    }
}
