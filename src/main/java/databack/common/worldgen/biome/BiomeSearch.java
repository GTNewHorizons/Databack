package databack.common.worldgen.biome;

import org.jetbrains.annotations.NotNull;

import databack.common.annotation.ThreadSafe;

/// Finds the entry of a parameter list closest to a target point (spec section 4). Every implementation must give
/// exactly the answers [BruteForceBiomeSearch] gives.
@ThreadSafe
public interface BiomeSearch {

    /// The index of the entry with the smallest [ClimateEntry#distance] to `target` (quantized, indexed like
    /// [ClimateEntry]'s parameters); on a tie, the lowest index. -1 when the list is empty.
    int closest(long @NotNull [] target);
}
