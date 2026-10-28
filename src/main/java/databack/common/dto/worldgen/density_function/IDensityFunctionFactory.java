package databack.common.dto.worldgen.density_function;

import java.util.Collections;
import java.util.List;

import databack.common.worldgen.density.DensityBuildContext;
import databack.common.worldgen.density.DensityNode;

public interface IDensityFunctionFactory {

    /// Builds the seeded runtime function. Implementations should build their children through
    /// [DensityBuildContext#build] (not by calling `instantiate` directly) so identical subtrees are shared.
    DensityNode instantiate(DensityBuildContext ctx);

    /** Returns the direct IDensityFunctionFactory children of this node. Leaf nodes return an empty list. */
    default List<IDensityFunctionFactory> children() {
        return Collections.emptyList();
    }

}
