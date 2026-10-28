package databack.common.worldgen.density;

import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

/// Time to compute `final_density` for one whole chunk column.
///
/// Chunks are visited in a row-by-row sweep (a new chunk every invocation) over a fixed 32x32 chunk region, roughly
/// like a player exploring, so the column cache sees realistic reuse. A lap is far bigger than the column cache, so
/// only the previous row's columns are ever reused.
///
/// Modes, each adding to the previous one:
/// - `vanilla`: the exact (stateful) evaluator, which replays the spec's evaluation order step by step: per-block
///   interpolator updates, full dimension height, no tree optimization, no column sharing (still with the faster
///   noise sampler, see NoiseBenchmark)
/// - `fastpath`: the pure evaluator: corner columns with per-position memos of shared subtrees, array-at-a-time cell
///   evaluation copied straight to the output
/// - `optimized`: + build-time optimizer
/// - `clipped`: + only y = 0..255 (what the 1.7 world needs)
/// - `shared`: + corner columns shared between neighbouring chunks
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 5, time = 2)
@Measurement(iterations = 5, time = 2)
@Fork(value = 1, jvmArgsAppend = { "-Xmx2G" })
public class ChunkDensityBenchmark {

    @Param({ "overworld" })
    public String dimension;

    @Param({ "vanilla", "fastpath", "optimized", "clipped", "shared" })
    public String mode;

    private ChunkDensitySampler sampler;

    @Setup(Level.Trial)
    public void setup() {
        VanillaWorldgen.Settings s = VanillaWorldgen.settings(dimension);
        boolean optimize = !mode.equals("vanilla") && !mode.equals("fastpath");
        boolean clip = mode.equals("clipped") || mode.equals("shared");
        boolean share = mode.equals("shared");

        DensityRouter router = VanillaWorldgen.router(dimension, 12345L, optimize);
        int minY = s.noise.min_y, maxY = s.noise.min_y + s.noise.height;
        if (clip) {
            minY = Math.max(minY, 0);
            maxY = Math.min(maxY, 256);
        }

        sampler = new ChunkDensitySampler(
            router.finalDensity, s.cellWidth(), s.cellHeight(), minY, maxY, minY, maxY, share ? 4096 : 0,
            mode.equals("vanilla"));
    }

    @State(Scope.Thread)
    public static class Cursor {

        private static final int ROW = 32;
        private static int nextRow = 0;

        double[] buffer;
        int x, z, row;

        @Setup(Level.Trial)
        public void setup(ChunkDensityBenchmark bench) {
            buffer = bench.sampler.newBuffer();
            synchronized (Cursor.class) {
                // Each thread sweeps its own band of rows, far from the others.
                row = 1000 * nextRow++;
            }
        }

        void advance() {
            if (++x == ROW) {
                x = 0;
                // Wrap around so every mode measures the same chunks: cost depends heavily on the terrain.
                if (++z == ROW) z = 0;
            }
        }
    }

    @Benchmark
    public double[] chunk(Cursor cursor) {
        sampler.sample(cursor.x, cursor.row + cursor.z, cursor.buffer);
        cursor.advance();
        return cursor.buffer;
    }
}
