package databack.common.worldgen.feature;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/// One order for a decoration step's features across every biome: each biome's features keep their relative order,
/// and a feature's position is its seed index ([FeatureSeeds#featureSeed]). Ties go to the feature seen first, biome
/// by biome. Features are compared with `equals` (identity, for holders).
///
/// Not vanilla's own order, so seed indices may differ from vanilla's; which features run, and in what order within a
/// biome, do not.
public final class FeatureOrder {

    private FeatureOrder() {}

    /// `biomes` holds each biome's features for the step, in order. When biomes disagree on an order (a cycle), the
    /// features left over follow in first-seen order.
    @NotNull
    @Contract(pure = true)
    public static <T> List<T> of(@NotNull List<List<T>> biomes) {
        Set<T> seen = new LinkedHashSet<>();
        Map<T, Set<T>> after = new HashMap<>();
        Map<T, Integer> before = new HashMap<>();
        for (List<T> biome : biomes) {
            for (int i = 0; i < biome.size(); i++) {
                T feature = biome.get(i);
                seen.add(feature);
                before.putIfAbsent(feature, 0);
                if (i == 0) continue;

                T previous = biome.get(i - 1);
                if (previous != feature && after.computeIfAbsent(previous, k -> new LinkedHashSet<>()).add(feature)) {
                    before.merge(feature, 1, Integer::sum);
                }
            }
        }

        List<T> order = new ArrayList<>(seen.size());
        Set<T> placed = new HashSet<>();
        boolean progress = true;
        while (order.size() < seen.size()) {
            T next = null;
            for (T feature : seen) {
                if (!placed.contains(feature) && (before.get(feature) == 0 || !progress)) {
                    next = feature;
                    break;
                }
            }
            if (next == null) {
                progress = false;
                continue;
            }

            progress = true;
            order.add(next);
            placed.add(next);
            for (T later : after.getOrDefault(next, Collections.emptySet())) {
                before.merge(later, -1, Integer::sum);
            }
        }
        return order;
    }
}
