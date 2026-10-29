package databack.common.worldgen.carver.standin;

import java.util.BitSet;
import java.util.HashSet;
import java.util.Set;

import org.jetbrains.annotations.ApiStatus;

import com.gtnewhorizon.gtnhlib.util.CoordinatePacker;

/// Stand-in for the blocks of a chunk already carved, shared by every carver of the chunk. Only the carvers read
/// it, so any consistent coordinates do. Coordinates within the chunk's local box (x and z in 0..15, y in its layers)
/// live in a bitset; any others in a set.
@ApiStatus.Internal
public final class CvCarvingMask {

    private final int minY, layers;
    private final BitSet local;
    private final Set<Long> other = new HashSet<>();

    public CvCarvingMask(int minY, int layers) {
        this.minY = minY;
        this.layers = layers;
        this.local = new BitSet(layers * 256);
    }

    public boolean get(int x, int y, int z) {
        int index = index(x, y, z);
        return index >= 0 ? local.get(index) : other.contains(CoordinatePacker.pack(x, y, z));
    }

    public void set(int x, int y, int z) {
        int index = index(x, y, z);
        if (index >= 0) local.set(index);
        else other.add(CoordinatePacker.pack(x, y, z));
    }

    private int index(int x, int y, int z) {
        int layer = y - minY;
        if ((x | z) >>> 4 != 0 || layer < 0 || layer >= layers) return -1;
        return (layer * 16 + z) * 16 + x;
    }
}
