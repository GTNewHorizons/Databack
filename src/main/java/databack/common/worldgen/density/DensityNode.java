package databack.common.worldgen.density;

import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.UnaryOperator;

/// An immutable, seeded density function: one node of a `worldgen/density_function` tree.
///
/// [#sample] evaluates the node at a single block position, where every caching marker is transparent (spec 2).
/// Chunk evaluation, where markers have state, is done by [ChunkDensitySampler], which compiles the tree into its own
/// per-thread structures.
///
/// Every node carries its **bound estimate** `[lo, hi]` (spec 1). Estimates are behaviour, not a safety net: `min` and
/// `max` use them to skip their second argument. A node's estimate is fixed when it is constructed; rewrites that
/// replace a node keep the replaced node's estimate (see [#withBounds]), so rewriting never changes a skip decision.
///
/// Nodes compare structurally ([#equals]): same type, same parameters, same estimate and equal inputs. Structurally
/// equal markers share their state during chunk evaluation (spec 5.1).
public abstract class DensityNode {

    static final DensityNode[] NO_INPUTS = new DensityNode[0];

    /// Bound estimate.
    public final double lo, hi;

    private final int hash;

    /// @param paramHash hash of this node's own parameters; inputs and bounds are mixed in here
    protected DensityNode(double lo, double hi, int paramHash, DensityNode... inputs) {
        this.lo = lo;
        this.hi = hi;
        int h = getClass().hashCode() * 31 + paramHash;
        for (DensityNode in : inputs) h = h * 31 + in.hashCode();
        h = h * 31 + Double.hashCode(lo);
        this.hash = h * 31 + Double.hashCode(hi);
    }

    /// The value at a block position, with every marker transparent.
    public abstract double sample(int x, int y, int z);

    /// Direct inputs, in the order the spec lists them (which is also the order they are evaluated and traversed in).
    public DensityNode[] inputs() {
        return NO_INPUTS;
    }

    /// The same node with other inputs (same count and order as [#inputs]) and the same bound estimate. Leaves
    /// return themselves.
    public DensityNode withInputs(DensityNode[] inputs) {
        return this;
    }

    /// The same node with another bound estimate.
    public abstract DensityNode withBounds(double lo, double hi);

    /// Whether `other` (always of the same class) has the same parameters, ignoring inputs and bounds.
    protected abstract boolean sameParams(DensityNode other);

    /// Bottom-up rewrite: rebuilds every node from its rewritten inputs, then applies `f` to it. Shared nodes are
    /// visited once, so the result keeps the sharing of the original.
    public final DensityNode rewrite(UnaryOperator<DensityNode> f) {
        return rewrite(f, new IdentityHashMap<>());
    }

    /// As [#rewrite(UnaryOperator)], sharing `done` (original to rewritten) with other rewrites, so nodes shared
    /// between several roots stay shared.
    final DensityNode rewrite(UnaryOperator<DensityNode> f, Map<DensityNode, DensityNode> done) {
        DensityNode result = done.get(this);
        if (result != null) return result;

        DensityNode[] in = inputs();
        DensityNode rebuilt = this;
        if (in.length > 0) {
            DensityNode[] next = new DensityNode[in.length];
            boolean changed = false;
            for (int i = 0; i < in.length; i++) {
                next[i] = in[i].rewrite(f, done);
                changed |= next[i] != in[i];
            }
            if (changed) rebuilt = withInputs(next);
        }

        result = f.apply(rebuilt);
        done.put(this, result);
        return result;
    }

    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || o.getClass() != getClass()) return false;
        DensityNode other = (DensityNode) o;
        if (other.hash != hash || !DensityMath.same(lo, other.lo) || !DensityMath.same(hi, other.hi)) return false;
        return sameParams(other) && Arrays.equals(inputs(), other.inputs());
    }

    @Override
    public final int hashCode() {
        return hash;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(getClass().getSimpleName()).append('(');
        DensityNode[] in = inputs();
        for (int i = 0; i < in.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(in[i]);
        }
        return sb.append(')').toString();
    }
}
