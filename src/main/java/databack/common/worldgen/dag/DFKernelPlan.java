package databack.common.worldgen.dag;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

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

    /**
     * Builds an ordered map (topological plan order) from barrier ID → label strings.
     * Key = outputBarrierId, or {@code "(terminal)"} for the terminal group.
     * Value = {@code String[]{kind, sourceDFType, inlinedDFTypes}} where:
     * <ul>
     *   <li>{@code kind}          — {@link BarrierKind#name()} or {@code "(terminal)"}</li>
     *   <li>{@code sourceDFType}  — barrier source factory simple class name, or {@code "(root)"}</li>
     *   <li>{@code inlinedDFTypes} — comma-separated unique simple class names of inlined node sources</li>
     * </ul>
     */
    public Map<String, String[]> buildKernelGroupLabels() {
        Map<String, String[]> labels = new LinkedHashMap<>();
        for (KernelGroup group : groups) {
            String barrierId, kind, sourceDFType;
            if (group.isTerminal()) {
                barrierId    = "(terminal)";
                kind         = "(terminal)";
                sourceDFType = "(root)";
            } else {
                barrierId    = group.output().id();
                kind         = group.output().kind().name();
                sourceDFType = group.output().getFactory().getClass().getSimpleName();
            }
            LinkedHashSet<String> inlined = new LinkedHashSet<>();
            for (InlineNode node : group.nodes()) {
                inlined.add(node.getFactory().getClass().getSimpleName());
            }
            labels.put(barrierId, new String[]{kind, sourceDFType, String.join(", ", inlined)});
        }
        return labels;
    }
}
