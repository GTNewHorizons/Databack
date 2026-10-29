package databack.common.worldgen.feature.standin;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/// Stand-in for an inclusive range of ints.
@ApiStatus.Internal
public final class FtIntRange {

    private final int min, max;

    public FtIntRange(int min, int max) {
        this.min = min;
        this.max = max;
    }

    @NotNull
    public Comparable<Integer> minInclusive() {
        return min;
    }

    @NotNull
    public Comparable<Integer> maxInclusive() {
        return max;
    }
}
