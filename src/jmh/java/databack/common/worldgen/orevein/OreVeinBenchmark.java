package databack.common.worldgen.orevein;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import databack.common.worldgen.density.DensityRouter;
import databack.common.worldgen.density.VanillaWorldgen;
import databack.common.worldgen.surface.SurfaceSystem;

/// Time to sample the vein fields of one chunk and place its veins, over y = 0..255 of an all-solid chunk (the worst
/// case for placement). Chunks are visited in a row-by-row sweep over a 32x32 region, like ChunkDensityBenchmark.
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 5, time = 2)
@Measurement(iterations = 5, time = 2)
@Fork(value = 1, jvmArgsAppend = { "-Xmx2G" })
public class OreVeinBenchmark {

    private static final long SEED = 12345;
    private static final int ROW = 32, MIN_Y = 0, LAYERS = 256;

    private VeinFieldSampler fields;
    private OreVeinSystem system;
    private byte[] kinds;
    private int x, z;

    @Setup(Level.Trial)
    public void setup() {
        VanillaWorldgen.Settings s = VanillaWorldgen.settings("overworld");
        DensityRouter router = VanillaWorldgen.router("overworld", SEED, true);
        fields = VeinFieldSampler.create(router, s.cellWidth(), s.cellHeight(), MIN_Y, MIN_Y + LAYERS,
            s.noise.min_y, s.noise.min_y + s.noise.height);
        system = OreVeinSystem.create(VanillaWorldgen.context(SEED, false).random());
        kinds = new byte[LAYERS * 256];
        Arrays.fill(kinds, SurfaceSystem.SOLID);
    }

    @Benchmark
    public VeinFields sampleFields() {
        VeinFields result = fields.sample(x, z);
        advance();
        return result;
    }

    @Benchmark
    public VeinBlock[] sampleAndPlace() {
        VeinBlock[] result = system.place(x, z, kinds, MIN_Y, LAYERS, fields.sample(x, z));
        advance();
        return result;
    }

    private void advance() {
        if (++x == ROW) {
            x = 0;
            if (++z == ROW) z = 0;
        }
    }
}
