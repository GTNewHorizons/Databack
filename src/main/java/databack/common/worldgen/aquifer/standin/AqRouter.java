package databack.common.worldgen.aquifer.standin;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/// Stand-in for the noise router: the fields the aquifer reads.
@ApiStatus.Internal
public final class AqRouter {

    private final AqFunction barrier, floodedness, spread, lava, erosion, depth;

    public AqRouter(
        @NotNull AqFunction barrier, @NotNull AqFunction floodedness, @NotNull AqFunction spread,
        @NotNull AqFunction lava, @NotNull AqFunction erosion, @NotNull AqFunction depth
    ) {
        this.barrier = barrier;
        this.floodedness = floodedness;
        this.spread = spread;
        this.lava = lava;
        this.erosion = erosion;
        this.depth = depth;
    }

    public AqFunction barrierNoise() {
        return barrier;
    }

    public AqFunction fluidLevelFloodednessNoise() {
        return floodedness;
    }

    public AqFunction fluidLevelSpreadNoise() {
        return spread;
    }

    public AqFunction lavaNoise() {
        return lava;
    }

    public AqFunction erosion() {
        return erosion;
    }

    public AqFunction depth() {
        return depth;
    }
}
