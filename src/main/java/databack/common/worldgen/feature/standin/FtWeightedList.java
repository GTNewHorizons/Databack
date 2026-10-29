package databack.common.worldgen.feature.standin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import databack.common.worldgen.rng.RandomSource;

/// Stand-in for a weighted list. Immutable once built.
@ApiStatus.Internal
public final class FtWeightedList {

    @NotNull
    private final List<Object> values;
    private final int[] weights;
    private final int totalWeight;

    private FtWeightedList(@NotNull List<Object> values, int @NotNull [] weights) {
        this.values = Collections.unmodifiableList(values);
        this.weights = weights;
        int total = 0;
        for (int w : weights) total += w;
        this.totalWeight = total;
    }

    @NotNull
    @Contract("-> new")
    public static Builder builder() {
        return new Builder();
    }

    public boolean isEmpty() {
        return values.isEmpty();
    }

    /// One bounded draw over the total weight, walked through the entries in order (`feature/probes/weighted.tsv`).
    /// Throws when the list is empty.
    @NotNull
    public Object getRandomOrThrow(@NotNull RandomSource random) {
        if (totalWeight <= 0) throw new IllegalStateException("Empty weighted list");

        int roll = random.nextInt(totalWeight);
        for (int i = 0; i < weights.length; i++) {
            roll -= weights[i];
            if (roll < 0) return values.get(i);
        }
        throw new AssertionError("Roll exceeded the total weight");
    }

    public static final class Builder {

        private final List<Object> values = new ArrayList<>();
        private final List<Integer> weights = new ArrayList<>();

        private Builder() {}

        @NotNull
        @Contract("_, _ -> this")
        public Builder add(@NotNull Object value, int weight) {
            values.add(value);
            weights.add(weight);
            return this;
        }

        @NotNull
        @Contract("-> new")
        public FtWeightedList build() {
            int[] w = new int[weights.size()];
            for (int i = 0; i < w.length; i++) w[i] = weights.get(i);
            return new FtWeightedList(new ArrayList<>(values), w);
        }
    }
}
