package databack.common.worldgen.carver.standin;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import databack.common.dto.worldgen.float_provider.IFloatProvider;
import databack.common.worldgen.rng.RandomSourceAdapter;

/// Stand-in for a float provider: a datapack one (`carver/probes/providers.tsv`).
@ApiStatus.Internal
public final class CvFloatProvider {

    @NotNull
    private final IFloatProvider provider;

    public CvFloatProvider(@NotNull IFloatProvider provider) {
        this.provider = provider;
    }

    public float sample(@NotNull CvRandom random) {
        return provider.get(new RandomSourceAdapter(random.source()));
    }
}
