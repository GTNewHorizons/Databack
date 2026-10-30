package databack.common.worldgen.biome;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import databack.common.annotation.ThreadSafe;
import databack.common.worldgen.biome.zoom.ZoomSource;

/// A `minecraft:multi_noise` biome source: the biome of each quart cell is the closest entry to the cell's climate
/// (spec sections 3 and 4). Block-level biomes come from zooming over it (`BiomeZoom`).
@ThreadSafe
public final class MultiNoiseBiomeSource implements ZoomSource<String> {

    @NotNull
    private final List<ClimateEntry> entries;
    @NotNull
    private final BiomeSearch search;
    @NotNull
    private final ClimateSampler sampler;
    /// Each thread's target buffer and tree query, reused across cells.
    private final ThreadLocal<Scratch> scratch = ThreadLocal.withInitial(Scratch::new);

    public MultiNoiseBiomeSource(
        @NotNull List<ClimateEntry> entries, @NotNull BiomeSearch search, @NotNull ClimateSampler sampler
    ) {
        this.entries = new ArrayList<>(entries);
        this.search = search;
        this.sampler = sampler;
    }

    /// A source that searches `entries` with a [ClimateTree].
    @NotNull
    @Contract("_, _ -> new")
    public static MultiNoiseBiomeSource create(@NotNull List<ClimateEntry> entries, @NotNull ClimateSampler sampler) {
        return new MultiNoiseBiomeSource(entries, new ClimateTree(entries), sampler);
    }

    /// The biome id of quart cell `(quartX, quartY, quartZ)`, or null when the source has no entries.
    @Nullable
    @Override
    public String getNoiseBiome(int quartX, int quartY, int quartZ) {
        Scratch scratch = this.scratch.get();
        sampler.sample(quartX, quartY, quartZ, scratch.target);

        int index = search instanceof ClimateTree tree ? tree.closest(scratch.target, scratch.query)
            : search.closest(scratch.target);
        return index < 0 ? null : entries.get(index).biome();
    }

    private static final class Scratch {

        final long[] target = new long[ClimateEntry.PARAMETER_COUNT];
        final ClimateTree.Query query = new ClimateTree.Query();
    }
}
