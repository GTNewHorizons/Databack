package databack.common.worldgen.dag;

import java.util.List;

/**
 * The complete GPU execution plan for a density function tree.
 * Groups are in topological order — each group's {@code reads} are always produced
 * by an earlier group in the list. Submit them to the GPU scheduler in sequence.
 */
public final class DFKernelPlan {

    private final List<KernelGroup> groups;

    public DFKernelPlan(List<KernelGroup> groups) {
        this.groups = groups;
    }

    public List<KernelGroup> groups() { return groups; }

    /** The final group in the plan — produces the actual density values. */
    public KernelGroup terminal() {
        return groups.get(groups.size() - 1);
    }
}
