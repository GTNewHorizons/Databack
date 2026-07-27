package databack.common.worldgen.dag.codegen;

import databack.common.worldgen.dag.DFKernelPlan;
import databack.common.worldgen.dag.KernelGroup;

import java.util.ArrayList;
import java.util.List;

/**
 * Entry-point for compiling a {@link DFKernelPlan} (produced by
 * {@link databack.common.worldgen.dag.DFDagBuilder}) into a list of
 * {@link GeneratedKernel}s ready for GPU submission.
 * <p>
 * Groups are compiled in the order they appear in the plan, which is guaranteed
 * to be topological (all dependencies before their consumers).
 */
public final class DFKernelCodegen {

    /**
     * Compiles every {@link KernelGroup} in the plan and returns the results in the same order.
     *
     * @param plan the execution plan produced by {@link databack.common.worldgen.dag.DFDagBuilder#build}
     * @return list of compiled kernels, one per group, in topological order
     */
    public static List<GeneratedKernel> compile(DFKernelPlan plan) {
        List<GeneratedKernel> result = new ArrayList<>(plan.groups().size());
        for (KernelGroup group : plan.groups()) {
            result.add(KernelBodyEmitter.emit(group));
        }
        return result;
    }

    private DFKernelCodegen() {}
}
