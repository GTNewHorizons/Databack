package databack.common.worldgen.feature.standin;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import databack.common.dto.worldgen.height_provider.IHeightProvider;
import databack.common.worldgen.rng.RandomSource;
import databack.common.worldgen.rng.RandomSourceAdapter;

/// Stand-in for a height provider: a datapack one, over the context's height range.
@ApiStatus.Internal
public final class FtHeightProvider {

    @NotNull
    private final IHeightProvider provider;

    public FtHeightProvider(@NotNull IHeightProvider provider) {
        this.provider = provider;
    }

    public int sample(@NotNull RandomSource random, @NotNull FtGenerationContext context) {
        return provider.get(new RandomSourceAdapter(random), context.getMinGenY(), context.getGenDepth());
    }
}
