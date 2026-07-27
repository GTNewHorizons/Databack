package databack.common.dto.worldgen.density_function;

import databack.common.context.WorldContext;

import java.util.Collections;
import java.util.List;

public interface IDensityFunctionFactory {

    IDensityFunction instantiate(WorldContext ctx);

    /** Returns the direct IDensityFunctionFactory children of this node. Leaf nodes return an empty list. */
    default List<IDensityFunctionFactory> children() {
        return Collections.emptyList();
    }

}
