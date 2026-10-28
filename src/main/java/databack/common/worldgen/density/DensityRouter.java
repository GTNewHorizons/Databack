package databack.common.worldgen.density;

/// The seeded runtime form of a `noise_router`. Field order matches the order the reference harness writes them in.
public final class DensityRouter {

    public final DensityNode barrierNoise;
    public final DensityNode fluidLevelFloodednessNoise;
    public final DensityNode fluidLevelSpreadNoise;
    public final DensityNode lavaNoise;
    public final DensityNode temperature;
    public final DensityNode vegetation;
    public final DensityNode continents;
    public final DensityNode erosion;
    public final DensityNode depth;
    public final DensityNode ridges;
    public final DensityNode preliminarySurfaceLevel;
    public final DensityNode finalDensity;
    public final DensityNode veinToggle;
    public final DensityNode veinRidged;
    public final DensityNode veinGap;

    public DensityRouter(
        DensityNode barrierNoise, DensityNode fluidLevelFloodednessNoise,
        DensityNode fluidLevelSpreadNoise, DensityNode lavaNoise, DensityNode temperature,
        DensityNode vegetation, DensityNode continents, DensityNode erosion, DensityNode depth,
        DensityNode ridges, DensityNode preliminarySurfaceLevel, DensityNode finalDensity,
        DensityNode veinToggle, DensityNode veinRidged, DensityNode veinGap
    ) {
        this.barrierNoise = barrierNoise;
        this.fluidLevelFloodednessNoise = fluidLevelFloodednessNoise;
        this.fluidLevelSpreadNoise = fluidLevelSpreadNoise;
        this.lavaNoise = lavaNoise;
        this.temperature = temperature;
        this.vegetation = vegetation;
        this.continents = continents;
        this.erosion = erosion;
        this.depth = depth;
        this.ridges = ridges;
        this.preliminarySurfaceLevel = preliminarySurfaceLevel;
        this.finalDensity = finalDensity;
        this.veinToggle = veinToggle;
        this.veinRidged = veinRidged;
        this.veinGap = veinGap;
    }

    /// All fields, in [#FIELD_NAMES] order.
    public DensityNode[] all() {
        return new DensityNode[] { barrierNoise, fluidLevelFloodednessNoise, fluidLevelSpreadNoise, lavaNoise,
            temperature, vegetation, continents, erosion, depth, ridges, preliminarySurfaceLevel, finalDensity,
            veinToggle, veinRidged, veinGap };
    }

    public static final String[] FIELD_NAMES = { "barrierNoise", "fluidLevelFloodednessNoise", "fluidLevelSpreadNoise",
        "lavaNoise", "temperature", "vegetation", "continents", "erosion", "depth", "ridges", "preliminarySurfaceLevel",
        "finalDensity", "veinToggle", "veinRidged", "veinGap" };
}
