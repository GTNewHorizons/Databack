package databack.common.worldgen.feature.standin;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import databack.common.dto.worldgen.int_provider.IIntProvider;
import databack.common.worldgen.rng.RandomSource;
import databack.common.worldgen.rng.RandomSourceAdapter;

/// Stand-in for an int provider: a datapack one, or one vanilla's code builds itself.
@ApiStatus.Internal
public abstract class FtIntProvider {

    public abstract int sample(@NotNull RandomSource random);

    /// The smallest value [#sample] can give. Unknown for datapack providers; throws then.
    public int minInclusive() {
        throw new UnsupportedOperationException("The bounds of a datapack int provider are unknown");
    }

    /// The largest value [#sample] can give. Unknown for datapack providers; throws then.
    public int maxInclusive() {
        throw new UnsupportedOperationException("The bounds of a datapack int provider are unknown");
    }

    @NotNull
    @Contract("_ -> new")
    public static FtIntProvider of(@NotNull IIntProvider provider) {
        return new FtIntProvider() {

            @Override
            public int sample(@NotNull RandomSource random) {
                return provider.get(new RandomSourceAdapter(random));
            }
        };
    }

    /// Always `value`, with no draws.
    @NotNull
    @Contract("_ -> new")
    public static FtIntProvider of(int value) {
        return new Bounded(value, value) {

            @Override
            public int sample(@NotNull RandomSource random) {
                return value;
            }
        };
    }

    /// `-size..size`, peaked at 0: the difference of two bounded draws (`feature/probes/trapezoid_int.tsv`).
    @NotNull
    @Contract("_ -> new")
    public static FtIntProvider triangle(int size) {
        return new Bounded(-size, size) {

            @Override
            public int sample(@NotNull RandomSource random) {
                return random.nextInt(size + 1) - random.nextInt(size + 1);
            }
        };
    }

    private abstract static class Bounded extends FtIntProvider {

        private final int min, max;

        Bounded(int min, int max) {
            this.min = min;
            this.max = max;
        }

        @Override
        public int minInclusive() {
            return min;
        }

        @Override
        public int maxInclusive() {
            return max;
        }
    }
}
