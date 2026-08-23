package databack.common.worldgen.dag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.github.bsideup.jabel.Desugar;
import databack.common.dto.worldgen.density_function.BuiltinDensityFunctions.CacheOnceUnary;
import databack.common.dto.worldgen.density_function.BuiltinDensityFunctions.ConstantFunc;
import databack.common.dto.worldgen.density_function.BuiltinDensityFunctions.FlatCacheUnary;
import databack.common.dto.worldgen.density_function.BuiltinDensityFunctions.InterpolatedFunc;
import databack.common.dto.worldgen.density_function.BuiltinDensityFunctions.MulBinary;
import databack.common.dto.worldgen.density_function.BuiltinDensityFunctions.NoiseFunc;
import databack.common.dto.worldgen.density_function.IDensityFunctionFactory;
import databack.common.worldgen.dag.DFDagBuilder2.BarrierDAGNode;
import databack.common.worldgen.dag.DFDagBuilder2.InlinedDAGNode;
import databack.common.worldgen.dag.DFDagBuilder2.PartitionedDAGNode;
import databack.common.worldgen.dag.codegen.VulkanCodeGen;
import lombok.Getter;
import mcgpu.core.hwaccel.buffer.BufferDataType;
import mcgpu.core.hwaccel.buffer.ConstantBuffer;
import mcgpu.core.hwaccel.buffer.GPUBuffer;
import mcgpu.core.hwaccel.buffer.GPUBufferBinding;

public class DFDagBuilder2Test {

    @BeforeEach
    public void initBackend() {
        // VulkanCodeGen.init() is idempotent for test re-runs would throw, but registration
        // only happens once per JVM since VULKAN is a singleton; guard with try/catch.
        try {
            VulkanCodeGen.init();
        } catch (IllegalStateException ignored) {
            // already initialised
        }
    }

    // ---- Factory helpers ----

    private static ConstantFunc constant(float v) { return new ConstantFunc(v); }

    private static FlatCacheUnary flatCache(IDensityFunctionFactory arg) { return new FlatCacheUnary(arg); }

    private static CacheOnceUnary cacheOnce(IDensityFunctionFactory arg) { return new CacheOnceUnary(arg); }

    private static InterpolatedFunc interpolated(IDensityFunctionFactory arg) { return new InterpolatedFunc(arg); }

    private static MulBinary mul(IDensityFunctionFactory a, IDensityFunctionFactory b) { return new MulBinary(a, b); }

    private static NoiseFunc noise(String id) { return new NoiseFunc(id, 1f, 1f); }

    // ---- Stub ConstantBuffer ----

    @Desugar
    private record StubGPUBuffer(BufferDataType dataType, int lenX, int lenY, int lenZ,
                                  ByteBuffer data) implements GPUBuffer {

        @Override
        public GPUBufferBinding getBinding() { return GPUBufferBinding.Constants; }

        @Override
        public int getBufferOffset() { return 0; }

        @Override
        public BufferDataType getDataType() { return dataType; }

        @Override
        public int getLenX() { return lenX; }

        @Override
        public int getLenY() { return lenY; }

        @Override
        public int getLenZ() { return lenZ; }
    }

    private static class StubConstantBuffer implements ConstantBuffer {

        public final List<StubGPUBuffer> buffers = new ArrayList<>();

        @Override
        public GPUBuffer addConstant(BufferDataType dataType, ByteBuffer data) {
            ByteBuffer copy = ByteBuffer.allocateDirect(data.remaining());
            copy.put(data);
            StubGPUBuffer buffer = new StubGPUBuffer(dataType, data.remaining() / dataType.width(), 1, 1, copy);
            buffers.add(buffer);
            return buffer;
        }
    }

    // ---- Tests ----

    @Test
    public void inlineOnlyTree_producesInlinedRoot() {
        // mul(constant, constant) → no barriers, pure inline tree.
        IDensityFunctionFactory func = mul(constant(2f), constant(3f));
        PartitionedDAGNode root = DFDagBuilder2.constructDAG(CodeGenerationBackend.VULKAN, func);
        // Root wraps CacheAllInCellUnary (injected by constructDAG) → always inline in this test.
        assertInstanceOf(InlinedDAGNode.class, root);
        assertEquals(CellSize.BLOCKS, root.outputShape());
    }

    @Test
    public void flatCache_producesBarrierRoot() {
        // FlatCacheUnary wrapping a constant.
        IDensityFunctionFactory func = flatCache(constant(1f));
        PartitionedDAGNode root = DFDagBuilder2.constructDAG(CodeGenerationBackend.VULKAN, func);
        // CacheAllInCell wrapper is inline, its child is the FlatCache barrier.
        assertInstanceOf(InlinedDAGNode.class, root);
        // The FlatCache child of CacheAllInCell should be a BarrierDAGNode.
        PartitionedDAGNode flatCacheNode = root.inputs().values().iterator().next();
        assertInstanceOf(BarrierDAGNode.class, flatCacheNode);
        assertEquals(CellSize.COLUMNS, flatCacheNode.outputShape());
    }

    @Test
    public void cacheOnce_atBlocksShape_becomesBarrier() {
        // CacheOnce(noise) at BLOCKS context.
        IDensityFunctionFactory func = cacheOnce(noise("foo"));
        PartitionedDAGNode root = DFDagBuilder2.constructDAG(CodeGenerationBackend.VULKAN, func);
        // CacheAllInCell wrapper is inline; CacheOnce is its child and is at BLOCKS → barrier.
        PartitionedDAGNode cacheOnceNode = root.inputs().values().iterator().next();
        assertInstanceOf(BarrierDAGNode.class, cacheOnceNode);
        assertEquals(CellSize.BLOCKS, cacheOnceNode.outputShape());
    }

    @Test
    public void cacheOnceInsideFlatCache_becomesInline() {
        // FlatCache partitions its child at COLUMNS → CacheOnce at COLUMNS → outputShape null → inline.
        IDensityFunctionFactory func = flatCache(cacheOnce(noise("bar")));
        PartitionedDAGNode root = DFDagBuilder2.constructDAG(CodeGenerationBackend.VULKAN, func);
        PartitionedDAGNode flatCacheNode = root.inputs().values().iterator().next();
        assertInstanceOf(BarrierDAGNode.class, flatCacheNode, "FlatCache should be a barrier");
        // CacheOnce inside FlatCache gets COLUMNS hint → falls through to inline.
        PartitionedDAGNode cacheOncePartitioned = flatCacheNode.inputs().values().iterator().next();
        assertInstanceOf(InlinedDAGNode.class, cacheOncePartitioned,
            "CacheOnce in COLUMNS context should be inlined");
    }

    @Test
    public void interpolated_expandsToTwoBarriers() {
        // InterpolatedFunc(noise) at BLOCKS → creates SAMPLE + INTERP chain.
        IDensityFunctionFactory func = interpolated(noise("baz"));
        PartitionedDAGNode root = DFDagBuilder2.constructDAG(CodeGenerationBackend.VULKAN, func);

        // Collect barriers in topological order.
        List<BarrierDAGNode> barriers = new ArrayList<>();
        collectBarriers(root, barriers);

        assertEquals(2, barriers.size(), "InterpolatedFunc should expand to exactly 2 barriers");
        assertEquals(CellSize.BLOCKS_REDUCED, barriers.get(0).outputShape(), "SAMPLE barrier is BLOCKS_REDUCED");
        assertEquals(CellSize.BLOCKS, barriers.get(1).outputShape(), "INTERP barrier is BLOCKS");
        assertTrue(barriers.get(0).id.contains("Sample"), "First barrier is the SAMPLE kernel");
        assertTrue(barriers.get(1).id.contains("Interp"), "Second barrier is the INTERP kernel");
    }

    @Test
    public void simpleComposition_barrierIds_areUnique() {
        // Two different noise functions → should get distinct barrier IDs if both are CacheOnce.
        IDensityFunctionFactory func = mul(cacheOnce(noise("n1")), cacheOnce(noise("n2")));
        PartitionedDAGNode root = DFDagBuilder2.constructDAG(CodeGenerationBackend.VULKAN, func);

        List<BarrierDAGNode> barriers = new ArrayList<>();
        collectBarriers(root, barriers);

        assertEquals(2, barriers.size());
        assertTrue(!barriers.get(0).id.equals(barriers.get(1).id), "Barrier IDs must be unique");
    }

    // ---- Helper ----

    private static void collectBarriers(PartitionedDAGNode node, List<BarrierDAGNode> out) {
        if (node instanceof BarrierDAGNode) {
            BarrierDAGNode b = (BarrierDAGNode) node;
            for (PartitionedDAGNode child : b.inputs.values()) {
                collectBarriers(child, out);
            }
            if (!out.contains(b)) out.add(b);
        } else {
            for (PartitionedDAGNode child : node.inputs().values()) {
                collectBarriers(child, out);
            }
        }
    }
}
