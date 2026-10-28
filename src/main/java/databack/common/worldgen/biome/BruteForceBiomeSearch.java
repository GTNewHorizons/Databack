package databack.common.worldgen.biome;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.NotNull;

import databack.common.annotation.ThreadSafe;

/// The reference [BiomeSearch]: checks every entry, in list order.
@ThreadSafe
public final class BruteForceBiomeSearch implements BiomeSearch {

    @NotNull
    private final List<ClimateEntry> entries;

    public BruteForceBiomeSearch(@NotNull List<ClimateEntry> entries) {
        this.entries = new ArrayList<>(entries);
    }

    @Override
    public int closest(long @NotNull [] target) {
        int best = -1;
        long bestDistance = Long.MAX_VALUE;

        for (int i = 0; i < entries.size(); i++) {
            long distance = entries.get(i).distance(target);
            if (best < 0 || distance < bestDistance) {
                best = i;
                bestDistance = distance;
            }
        }

        return best;
    }
}
