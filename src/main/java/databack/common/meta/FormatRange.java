package databack.common.meta;

/**
 * Represents an inclusive integer range used in format compatibility declarations.
 */
public final class FormatRange {

    private final int minInclusive;
    private final int maxInclusive;

    /**
     * @throws DatapackParseException if {@code minInclusive > maxInclusive}
     */
    public FormatRange(int minInclusive, int maxInclusive) {
        if (minInclusive > maxInclusive) {
            throw new DatapackParseException(
                "FormatRange is invalid: min (" + minInclusive + ") > max (" + maxInclusive + ")");
        }
        this.minInclusive = minInclusive;
        this.maxInclusive = maxInclusive;
    }

    public int getMinInclusive() {
        return minInclusive;
    }

    public int getMaxInclusive() {
        return maxInclusive;
    }

    public boolean contains(int version) {
        return minInclusive <= version && version <= maxInclusive;
    }
}
