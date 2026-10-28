package databack.common.worldgen.biome;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import databack.common.annotation.ThreadSafe;

/// A fast [BiomeSearch]: a static bounding volume hierarchy over the entries.
///
/// Each entry is a box in seven dimensions: its six parameter ranges, plus its offset as a degenerate range on a
/// seventh axis whose target is always 0, so [ClimateEntry#distance] is the plain squared distance to the box. Every
/// tree node holds the union of its entries' boxes; the distance to that union is a lower bound for every entry below
/// it. A query descends nearest child first and skips a subtree when its bound is larger than the best distance found
/// so far, or equal to it while every entry below comes later in the list than the best one. That makes the answer
/// exactly [BruteForceBiomeSearch]'s, ties included.
///
/// The bound only holds without `long` overflow, so lists or targets with values beyond [#SAFE_LIMIT] fall back to
/// brute force (realistic climate values are a few times 10^4).
@ThreadSafe
public final class ClimateTree implements BiomeSearch {

    /// Largest absolute quantized value the tree handles: differences then stay below 2^30, and a sum of seven squared
    /// differences below 2^63.
    static final long SAFE_LIMIT = 1L << 29;

    private static final int AXES = ClimateEntry.PARAMETER_COUNT + 1;
    private static final int OFFSET_AXIS = ClimateEntry.PARAMETER_COUNT;
    private static final int LEAF_SIZE = 8;

    @NotNull
    private final BruteForceBiomeSearch fallback;
    private final boolean safe;

    /// Entries in tree order: boxes at `[i * AXES + axis]`, and each one's index in the original list.
    private final long[] entryMin;
    private final long[] entryMax;
    private final int[] entryIndex;

    /// Nodes: union boxes at `[node * AXES + axis]`; children (-1 for leaves); a leaf's entry range in tree order; and
    /// the lowest original index below each node.
    private final long[] nodeMin;
    private final long[] nodeMax;
    private final int[] left;
    private final int[] right;
    private final int[] leafStart;
    private final int[] leafEnd;
    private final int[] lowestIndex;

    public ClimateTree(@NotNull List<ClimateEntry> entries) {
        this.fallback = new BruteForceBiomeSearch(entries);
        this.safe = entries.stream().allMatch(ClimateTree::isSafe);

        int n = entries.size();
        this.entryMin = new long[n * AXES];
        this.entryMax = new long[n * AXES];
        this.entryIndex = new int[n];

        Builder builder = new Builder(entries);
        builder.build();

        for (int i = 0; i < n; i++) {
            int original = builder.order[i];
            entryIndex[i] = original;
            box(entries.get(original), entryMin, entryMax, i * AXES);
        }

        int nodes = builder.nodeCount;
        this.nodeMin = Arrays.copyOf(builder.nodeMin, nodes * AXES);
        this.nodeMax = Arrays.copyOf(builder.nodeMax, nodes * AXES);
        this.left = Arrays.copyOf(builder.left, nodes);
        this.right = Arrays.copyOf(builder.right, nodes);
        this.leafStart = Arrays.copyOf(builder.leafStart, nodes);
        this.leafEnd = Arrays.copyOf(builder.leafEnd, nodes);
        this.lowestIndex = Arrays.copyOf(builder.lowestIndex, nodes);
    }

    @Override
    public int closest(long @NotNull [] target) {
        if (entryIndex.length == 0) return -1;
        if (!safe || !isSafe(target)) return fallback.closest(target);

        Best best = new Best();
        visit(0, target, best);
        return best.index;
    }

    private void visit(int node, long[] target, Best best) {
        if (left[node] < 0) {
            scanLeaf(node, target, best);
            return;
        }

        int a = left[node];
        int b = right[node];
        long da = boxDistance(nodeMin, nodeMax, a * AXES, target);
        long db = boxDistance(nodeMin, nodeMax, b * AXES, target);

        boolean aFirst = da < db || (da == db && lowestIndex[a] < lowestIndex[b]);
        int first = aFirst ? a : b;
        int second = aFirst ? b : a;
        long secondDistance = aFirst ? db : da;

        if (best.mayImprove(aFirst ? da : db, lowestIndex[first])) visit(first, target, best);
        if (best.mayImprove(secondDistance, lowestIndex[second])) visit(second, target, best);
    }

    private void scanLeaf(int node, long[] target, Best best) {
        for (int i = leafStart[node]; i < leafEnd[node]; i++) {
            long distance = boxDistance(entryMin, entryMax, i * AXES, target);
            best.offer(distance, entryIndex[i]);
        }
    }

    /// Squared distance from `target` (with 0 on the offset axis) to the box at `base`.
    private static long boxDistance(long[] min, long[] max, int base, long[] target) {
        long sum = 0;
        for (int axis = 0; axis < AXES; axis++) {
            long value = axis == OFFSET_AXIS ? 0 : target[axis];
            long d = ClimateEntry.axisDistance(value, min[base + axis], max[base + axis]);
            sum += d * d;
        }
        return sum;
    }

    private static void box(ClimateEntry entry, long[] min, long[] max, int base) {
        for (int p = 0; p < ClimateEntry.PARAMETER_COUNT; p++) {
            min[base + p] = entry.min(p);
            max[base + p] = entry.max(p);
        }
        min[base + OFFSET_AXIS] = entry.offset();
        max[base + OFFSET_AXIS] = entry.offset();
    }

    @Contract(pure = true)
    private static boolean isSafe(ClimateEntry entry) {
        if (Math.abs(entry.offset()) > SAFE_LIMIT) return false;
        for (int p = 0; p < ClimateEntry.PARAMETER_COUNT; p++) {
            if (Math.abs(entry.min(p)) > SAFE_LIMIT || Math.abs(entry.max(p)) > SAFE_LIMIT) return false;
        }
        return true;
    }

    @Contract(pure = true)
    private static boolean isSafe(long[] target) {
        for (int p = 0; p < ClimateEntry.PARAMETER_COUNT; p++) {
            if (Math.abs(target[p]) > SAFE_LIMIT) return false;
        }
        return true;
    }

    /// The best entry found so far in one query; lowest index wins ties.
    private static final class Best {

        private long distance = Long.MAX_VALUE;
        private int index = Integer.MAX_VALUE;

        /// Whether a subtree whose bound is `bound` and whose lowest index is `lowest` can hold a better entry.
        boolean mayImprove(long bound, int lowest) {
            return bound < distance || (bound == distance && lowest < index);
        }

        void offer(long candidate, int candidateIndex) {
            if (candidate < distance || (candidate == distance && candidateIndex < index)) {
                distance = candidate;
                index = candidateIndex;
            }
        }
    }

    /// Builds the tree top-down: a node with more than [#LEAF_SIZE] entries is split at the median of its entries'
    /// box centres along the axis where those centres are most spread out.
    private static final class Builder {

        private final long[] min;
        private final long[] max;
        final int[] order;

        long[] nodeMin = new long[16 * AXES];
        long[] nodeMax = new long[16 * AXES];
        int[] left = new int[16];
        int[] right = new int[16];
        int[] leafStart = new int[16];
        int[] leafEnd = new int[16];
        int[] lowestIndex = new int[16];
        int nodeCount;

        Builder(List<ClimateEntry> entries) {
            int n = entries.size();
            this.min = new long[n * AXES];
            this.max = new long[n * AXES];
            this.order = new int[n];

            for (int i = 0; i < n; i++) {
                box(entries.get(i), min, max, i * AXES);
                order[i] = i;
            }
        }

        void build() {
            if (order.length > 0) buildNode(0, order.length);
        }

        /// Builds the node for `order[start, end)` and returns its id.
        private int buildNode(int start, int end) {
            int node = allocate();
            bound(node, start, end);

            if (end - start <= LEAF_SIZE) {
                left[node] = -1;
                right[node] = -1;
                leafStart[node] = start;
                leafEnd[node] = end;
                return node;
            }

            sortByCentre(start, end, widestAxis(start, end));
            int middle = (start + end) >>> 1;

            int a = buildNode(start, middle);
            int b = buildNode(middle, end);
            left[node] = a;
            right[node] = b;
            leafStart[node] = start;
            leafEnd[node] = end;
            return node;
        }

        private void bound(int node, int start, int end) {
            int base = node * AXES;
            Arrays.fill(nodeMin, base, base + AXES, Long.MAX_VALUE);
            Arrays.fill(nodeMax, base, base + AXES, Long.MIN_VALUE);
            int lowest = Integer.MAX_VALUE;

            for (int i = start; i < end; i++) {
                int entry = order[i];
                lowest = Math.min(lowest, entry);
                for (int axis = 0; axis < AXES; axis++) {
                    nodeMin[base + axis] = Math.min(nodeMin[base + axis], min[entry * AXES + axis]);
                    nodeMax[base + axis] = Math.max(nodeMax[base + axis], max[entry * AXES + axis]);
                }
            }

            lowestIndex[node] = lowest;
        }

        private int widestAxis(int start, int end) {
            int widest = 0;
            long widestSpread = -1;

            for (int axis = 0; axis < AXES; axis++) {
                long lo = Long.MAX_VALUE, hi = Long.MIN_VALUE;
                for (int i = start; i < end; i++) {
                    long centre = centre2(order[i], axis);
                    lo = Math.min(lo, centre);
                    hi = Math.max(hi, centre);
                }
                if (hi - lo > widestSpread) {
                    widest = axis;
                    widestSpread = hi - lo;
                }
            }

            return widest;
        }

        /// Sorts `order[start, end)` by box centre on `axis`, then by original index, so builds are deterministic.
        private void sortByCentre(int start, int end, int axis) {
            List<Integer> slice = new ArrayList<>(end - start);
            for (int i = start; i < end; i++) slice.add(order[i]);

            slice.sort(Comparator.<Integer>comparingLong(e -> centre2(e, axis)).thenComparingInt(e -> e));
            for (int i = start; i < end; i++) order[i] = slice.get(i - start);
        }

        /// Twice the box centre, to stay in integers.
        private long centre2(int entry, int axis) {
            return min[entry * AXES + axis] + max[entry * AXES + axis];
        }

        private int allocate() {
            if (nodeCount == left.length) grow();
            return nodeCount++;
        }

        private void grow() {
            int capacity = left.length * 2;
            nodeMin = Arrays.copyOf(nodeMin, capacity * AXES);
            nodeMax = Arrays.copyOf(nodeMax, capacity * AXES);
            left = Arrays.copyOf(left, capacity);
            right = Arrays.copyOf(right, capacity);
            leafStart = Arrays.copyOf(leafStart, capacity);
            leafEnd = Arrays.copyOf(leafEnd, capacity);
            lowestIndex = Arrays.copyOf(lowestIndex, capacity);
        }
    }
}
