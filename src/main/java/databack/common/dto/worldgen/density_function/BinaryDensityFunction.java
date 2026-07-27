package databack.common.dto.worldgen.density_function;

import databack.common.context.WorldContext;
import databack.common.dto.worldgen.density_function.BuiltinDensityFunctions.ConstantDensityFunction;

public abstract class BinaryDensityFunction implements IDensityFunctionFactory {

    public IDensityFunctionFactory argument1, argument2;

    protected abstract float compute(float param1, float param2);

    private interface DB extends DensityBuffer {
        void left(DensityBuffer left);
        void right(DensityBuffer right);
    }

    private class DelegateBuffer implements DB {
        public DensityBuffer left, right;

        @Override
        public void left(DensityBuffer left) {
            this.left = left;
        }

        @Override
        public void right(DensityBuffer right) {
            this.right = right;
        }

        @Override
        public float get(int relX, int relY, int relZ) {
            return compute(left.get(relX, relY, relZ), right.get(relX, relY, relZ));
        }

        @Override
        public void discard() {
            if (left != null) {
                left.discard();
                left = null;
            }

            if (right != null) {
                right.discard();
                right = null;
            }
        }
    }

    private class RCDelegateBuffer implements DB {
        public DensityBuffer left;
        public final float rightConstant;

        public RCDelegateBuffer(float rightConstant) {
            this.rightConstant = rightConstant;
        }

        @Override
        public void left(DensityBuffer left) {
            this.left = left;
        }

        @Override
        public void right(DensityBuffer right) {

        }

        @Override
        public float get(int relX, int relY, int relZ) {
            return compute(left.get(relX, relY, relZ), rightConstant);
        }

        @Override
        public void discard() {
            if (left != null) {
                left.discard();
                left = null;
            }
        }
    }

    private class CRDelegateBuffer implements DB {
        public final float leftConstant;
        public DensityBuffer right;

        public CRDelegateBuffer(float leftConstant) {
            this.leftConstant = leftConstant;
        }

        @Override
        public void left(DensityBuffer left) {

        }

        @Override
        public void right(DensityBuffer right) {
            this.right = right;
        }

        @Override
        public float get(int relX, int relY, int relZ) {
            return compute(leftConstant, right.get(relX, relY, relZ));
        }

        @Override
        public void discard() {
            if (right != null) {
                right.discard();
                right = null;
            }
        }
    }

    @Override
    public IDensityFunction instantiate(WorldContext ctx) {
        IDensityFunction fn1 = argument1.instantiate(ctx);
        IDensityFunction fn2 = argument2.instantiate(ctx);

        boolean const1 = fn1.hasTrait(DensityFuncTrait.Constant);
        boolean const2 = fn2.hasTrait(DensityFuncTrait.Constant);

        float val1 = 0;
        float val2 = 0;

        if (const1) {
            DensityBuffer buffer = fn1.compute(0, 0, 0, new DensityMask().set(0, 0, 0));
            val1 = buffer.get(0, 0, 0);
            buffer.discard();
        }

        if (const2) {
            DensityBuffer buffer = fn2.compute(0, 0, 0, new DensityMask().set(0, 0, 0));
            val2 = buffer.get(0, 0, 0);
            buffer.discard();
        }

        if (const1 && const2) {
            // unlikely but possible
            return new ConstantDensityFunction(compute(val1, val2));
        }

        DB delegate;

        if (const1 && !const2) {
            delegate = new CRDelegateBuffer(val1);
        } else if (!const1 && const2) {
            delegate = new RCDelegateBuffer(val2);
        } else {
            delegate = new DelegateBuffer();
        }

        return new IDensityFunction() {

            @Override
            public boolean hasTrait(DensityFuncTrait trait) {
                return fn1.hasTrait(trait) && fn2.hasTrait(trait);
            }

            @Override
            public DensityBuffer compute(int blockX, int blockY, int blockZ, DensityMask mask) {
                delegate.discard();

                if (!const1) {
                    delegate.left(fn1.compute(blockX, blockY, blockZ, mask));
                }

                if (!const2) {
                    delegate.right(fn2.compute(blockX, blockY, blockZ, mask));
                }

                return delegate;
            }
        };
    }
}
