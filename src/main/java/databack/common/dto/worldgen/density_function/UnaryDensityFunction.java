package databack.common.dto.worldgen.density_function;

import databack.common.context.WorldContext;
import databack.common.dto.worldgen.density_function.DensityBuffer.CubeBuffer;

import java.util.Collections;
import java.util.List;

public abstract class UnaryDensityFunction implements IDensityFunctionFactory {

    public IDensityFunctionFactory argument;

    @Override
    public List<IDensityFunctionFactory> children() {
        return Collections.singletonList(argument);
    }

    protected abstract float compute(float param);

    private class DelegateBuffer implements DensityBuffer {
        public DensityBuffer next;

        @Override
        public float get(int relX, int relY, int relZ) {
            return compute(next.get(relX, relY, relZ));
        }

        @Override
        public void discard() {
            next.discard();
            next = null;
        }
    }

    @Override
    public IDensityFunction instantiate(WorldContext ctx) {
        IDensityFunction arg = argument.instantiate(ctx);

        DelegateBuffer delegate = new DelegateBuffer();

        return new IDensityFunction() {

            @Override
            public boolean hasTrait(DensityFuncTrait trait) {
                return arg.hasTrait(trait);
            }

            @Override
            public DensityBuffer compute(int blockX, int blockY, int blockZ, DensityMask mask) {
                if (delegate.next != null) {
                    delegate.discard();
                }

                delegate.next = arg.compute(blockX, blockY, blockZ, mask);

                return delegate;
            }
        };
    }
}
