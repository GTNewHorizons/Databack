package databack.common.worldgen.biome.zoom;

/// The biomes a zoom picks from: one per quart cell. Stands in for vanilla's noise biome source interface in rewritten
/// biome zoom code, so its single method keeps vanilla's name.
@FunctionalInterface
public interface ZoomSource<T> {

    /// The biome of quart cell `(quartX, quartY, quartZ)`.
    T getNoiseBiome(int quartX, int quartY, int quartZ);
}
