package databack.common.worldgen.dag.codegen;

import databack.common.worldgen.Expr;
import databack.common.worldgen.dag.CellSize;
import databack.common.worldgen.dag.DFDagBuilder2.PartitionedDAGNode;
import mcgpu.core.hwaccel.shader.KernelBuilder;

@SuppressWarnings("rawtypes")
public interface CodeGenContext {

    KernelBuilder getKernel();

    /// Returns the [CellSize] of the kernel currently being built.
    /// Used by barrier emitGetter implementations to compute the correct index expression
    /// when the consuming kernel's shape differs from the barrier's output shape.
    CellSize getKernelShape();

    Expr<Float> compute(PartitionedDAGNode node, Expr<Integer> x, Expr<Integer> y, Expr<Integer> z);

    /// Registers a named noise table for this kernel.
    /// Injects {@link databack.common.worldgen.dag.codegen.PerlinGlsl#PERLIN_FUNCTION} into the
    /// preamble on the first call (for any noise ID in this kernel).
    /// Idempotent: subsequent calls with the same ID return the cached GLSL push-constant reference.
    ///
    /// @param noiseId logical noise identifier (e.g. {@code "minecraft:temperature"})
    /// @return GLSL push-constant reference string (e.g. {@code "pc.constantOffset0"})
    String registerNoise(String noiseId);
}
