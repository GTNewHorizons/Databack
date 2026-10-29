package databack.common.worldgen.carver;

import java.io.IOException;
import java.util.List;
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

import databack.common.worldgen.aquifer.AquiferSystem;
import databack.common.worldgen.aquifer.VanillaAquifer;
import databack.common.worldgen.carver.standin.CvAquifer;
import databack.common.worldgen.carver.standin.CvBlockState;
import databack.common.worldgen.carver.standin.CvChunk;
import databack.common.worldgen.carver.standin.CvChunkPos;
import databack.common.worldgen.carver.standin.CvContext;
import databack.common.worldgen.carver.standin.CvStatePool;
import databack.common.worldgen.density.VanillaWorldgen;
import databack.common.worldgen.surface.SurfaceDump;
import databack.common.worldgen.vanilla.VanillaJar;

/// Time to carve one chunk with the overworld's carvers and aquifer, over the real chunks of `run.sh carver` (each
/// invocation carves a fresh copy of the next one).
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 5, time = 2)
@Measurement(iterations = 5, time = 2)
@Fork(value = 1, jvmArgsAppend = { "-Xmx2G" })
public class CarverBenchmark {

    private static final long SEED = 12345;
    private static final int[][] CHUNKS = { { 0, 0 }, { -7, 13 }, { 25, -40 }, { -300, 180 }, { 1500, 2200 },
        { 3, 3 }, { -40, 77 }, { 120, -15 }, { -512, -512 }, { 800, 64 }, { 157, -188 }, { -64, -172 } };

    private CvStatePool pool;
    private CarverSystem system;
    private AquiferSystem aquifers;
    private CvBlockState[][] before;
    private CvBlockState[] states;
    private int next;

    @Setup(Level.Trial)
    public void setup() throws IOException {
        pool = CarverFixture.pool();
        List<ConfiguredCarver> carvers = CarverFixture.configured(pool, CarverFixture.OVERWORLD_CARVERS);
        system = new CarverSystem(SEED, (x, z) -> carvers);
        aquifers = AquiferSystem.enabled(CarverFixture.SEA_LEVEL,
            VanillaAquifer.load(VanillaJar.classFile(VanillaAquifer.CLASS)),
            VanillaWorldgen.router("overworld", SEED, true), VanillaWorldgen.context(SEED, false).random());

        before = new CvBlockState[CHUNKS.length][];
        for (int i = 0; i < CHUNKS.length; i++) {
            SurfaceDump dump = SurfaceDump.read("carver",
                "chunk_" + SEED + "_" + CHUNKS[i][0] + "_" + CHUNKS[i][1] + "_surface.bin.gz");
            before[i] = new CvBlockState[CarverFixture.HEIGHT * 256];
            for (int j = 0; j < before[i].length; j++) {
                before[i][j] = pool.state(dump.block(j & 15, CarverFixture.MIN_Y + (j >> 8), (j >> 4) & 15));
            }
        }
        states = new CvBlockState[CarverFixture.HEIGHT * 256];
    }

    @Benchmark
    public CvBlockState[] chunk() {
        int[] c = CHUNKS[next];
        System.arraycopy(before[next], 0, states, 0, states.length);
        next = (next + 1) % CHUNKS.length;

        CvChunk chunk = new CvChunk(new CvChunkPos(c[0], c[1]), CarverFixture.MIN_Y, CarverFixture.HEIGHT, states);
        system.carve(chunk, new CvContext(CarverFixture.MIN_Y, CarverFixture.HEIGHT, (ch, pos, fluid) -> null),
            new CvAquifer(aquifers.chunkAquifer(c[0], c[1], CarverFixture.MIN_Y, CarverFixture.HEIGHT)));
        return states;
    }
}
