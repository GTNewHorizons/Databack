package databack.common.dto.worldgen.density_function;

import java.util.function.Consumer;

/// A buffer that acts like a 16x16x16 buffer.
/// Some buffers may not actually be 16x16x16, and may instead contain a differently sized array.
/// The coordinates passed to [#get(int, int, int)] are always \[0, 16). They are relative to the cube position that was
/// passed into [IDensityFunction#compute(int, int, int, DensityMask)].
/// The value for voxels that are false in the density function mask is undefined.
/// By convention, buffers are X,Y,Z major (so index should be roughly `Z << 8 | Y << 4 | X`). This is for CPU cache
/// efficiency.
public interface DensityBuffer {

    float get(int relX, int relY, int relZ);

    /// Called after the buffer has been fully read.
    /// Returns the object to a pool, if it came from one.
    /// The result of [#get(int, int, int)] is undefined after this is called.
    void discard();

    class ConstantBuffer implements DensityBuffer {

        public static final ConstantBuffer ZERO = new ConstantBuffer(0f);
        public static final ConstantBuffer ONE = new ConstantBuffer(1f);

        private final float value;

        public ConstantBuffer(float value) {
            this.value = value;
        }

        @Override
        public float get(int relX, int relY, int relZ) {
            return value;
        }

        @Override
        public void discard() {}
    }

    class CubeBuffer implements DensityBuffer {
        public final float[] data = new float[4096];

        private final Consumer<CubeBuffer> release;

        public CubeBuffer(Consumer<CubeBuffer> release) {
            this.release = release;
        }

        public void set(int relX, int relY, int relZ, float value) {
            data[relZ << 8 | relY << 4 | relX] = value;
        }

        public void copyFrom(DensityBuffer source) {
            if (source instanceof CubeBuffer cubeBuffer) {
                System.arraycopy(cubeBuffer.data, 0, this.data, 0, 4096);
            } else {
                for (int z = 0; z < 16; z++) {
                    for (int y = 0; y < 16; y++) {
                        for (int x = 0; x < 16; x++) {
                            this.set(x, y, z, source.get(x, y, z));
                        }
                    }
                }
            }
        }

        public void copyFrom(DensityBuffer source, DensityMask mask) {
            if (source instanceof CubeBuffer cubeBuffer) {
                int start = mask.nextSetBit(0);

                while (start != -1) {
                    int end = mask.nextClearBit(start);

                    System.arraycopy(cubeBuffer.data, start, this.data, start, end - start);

                    start = mask.nextSetBit(end);
                }
            } else {
                for (int z = 0; z < 16; z++) {
                    for (int y = 0; y < 16; y++) {
                        for (int x = 0; x < 16; x++) {
                            if (mask.isSet(x, y, z)) {
                                this.set(x, y, z, source.get(x, y, z));
                            }
                        }
                    }
                }
            }
        }

        @Override
        public float get(int relX, int relY, int relZ) {
            return data[relZ << 8 | relY << 4 | relX];
        }

        @Override
        public void discard() {
            if (release != null) release.accept(this);
        }
    }
}
