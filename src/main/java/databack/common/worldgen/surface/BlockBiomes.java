package databack.common.worldgen.surface;

/// The biome id of a block, as surface rules see it (zoomed).
@FunctionalInterface
public interface BlockBiomes {

    String biome(int x, int y, int z);
}
