package databack.common.worldgen.noise;

import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import databack.common.worldgen.rng.XoroshiroRandom;

/// One `NormalNoise` sample (the overworld `minecraft:offset` noise shape: 4 octaves), optimized vs vanilla-shaped.
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class NoiseBenchmark {

    private static final int N = 1024;

    private NormalNoise noise;
    private VanillaShapedNoise vanilla;
    private final double[] xs = new double[N], ys = new double[N], zs = new double[N];

    @Setup
    public void setup() {
        noise = NormalNoise.create(new XoroshiroRandom(12345L), new NormalNoise.NoiseParameters(-3, 1.0, 1.0, 1.0, 0.0));
        vanilla = new VanillaShapedNoise(noise);
        java.util.Random r = new java.util.Random(1);
        for (int i = 0; i < N; i++) {
            xs[i] = (r.nextDouble() - 0.5) * 20000;
            ys[i] = r.nextDouble() * 384 - 64;
            zs[i] = (r.nextDouble() - 0.5) * 20000;
        }
        for (int i = 0; i < N; i++) {
            if (noise.getValue(xs[i], ys[i], zs[i]) != vanilla.getValue(xs[i], ys[i], zs[i])) {
                throw new IllegalStateException("optimized noise disagrees with vanilla-shaped noise at " + i);
            }
        }
    }

    @Benchmark
    @OperationsPerInvocation(N)
    public double optimized() {
        double sum = 0;
        for (int i = 0; i < N; i++) sum += noise.getValue(xs[i], ys[i], zs[i]);
        return sum;
    }

    @Benchmark
    @OperationsPerInvocation(N)
    public double vanillaShaped() {
        double sum = 0;
        for (int i = 0; i < N; i++) sum += vanilla.getValue(xs[i], ys[i], zs[i]);
        return sum;
    }
}
