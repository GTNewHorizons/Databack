package databack.common.worldgen.carver.standin;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import databack.common.dto.worldgen.height_provider.IHeightProvider;
import databack.common.worldgen.rng.RandomSourceAdapter;

/// Stand-in for a height provider: a datapack one, over the context's height range
/// (`carver/probes/providers.tsv`).
@ApiStatus.Internal
public final class CvHeightProvider {

    @NotNull
    private final IHeightProvider provider;

    public CvHeightProvider(@NotNull IHeightProvider provider) {
        this.provider = provider;
    }

    public int sample(@NotNull CvRandom random, @NotNull CvContext context) {
        return provider.get(new RandomSourceAdapter(random.source()), context.getMinGenY(), context.getGenDepth());
    }
}
