package databack.common.dto.worldgen.density_function;

import java.util.Arrays;
import java.util.List;

import databack.common.worldgen.density.DensityBuildContext;
import databack.common.worldgen.density.DensityNode;
import java.util.Objects;

import lombok.ToString;

@ToString
public abstract class BinaryDensityFunction implements IDensityFunctionFactory {

    public final IDensityFunctionFactory argument1, argument2;

    public BinaryDensityFunction(IDensityFunctionFactory argument1, IDensityFunctionFactory argument2) {
        this.argument1 = argument1;
        this.argument2 = argument2;
    }

    @Override
    public List<IDensityFunctionFactory> children() {
        return Arrays.asList(argument1, argument2);
    }

    /// Builds the runtime function from the built arguments.
    protected abstract DensityNode combine(DensityNode a, DensityNode b);

    @Override
    public DensityNode instantiate(DensityBuildContext ctx) {
        return combine(ctx.build(argument1), ctx.build(argument2));
    }

    // Lombok's equals would consider e.g. abs(x) == square(x), which breaks the build cache.
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || o.getClass() != getClass()) return false;
        BinaryDensityFunction other = (BinaryDensityFunction) o;
        return Objects.equals(argument1, other.argument1) && Objects.equals(argument2, other.argument2);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode() * 31 + Objects.hash(argument1, argument2);
    }
}
