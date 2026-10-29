package databack.common.worldgen.carver.standin;

import java.util.HashSet;
import java.util.Set;

import org.jetbrains.annotations.ApiStatus;

import com.gtnewhorizon.gtnhlib.util.CoordinatePacker;

/// Stand-in for the blocks of a chunk already carved, shared by every carver of the chunk. Only the carvers read
/// it, so any consistent coordinates do.
@ApiStatus.Internal
public final class CvCarvingMask {

    private final Set<Long> carved = new HashSet<>();

    public boolean get(int x, int y, int z) {
        return carved.contains(CoordinatePacker.pack(x, y, z));
    }

    public void set(int x, int y, int z) {
        carved.add(CoordinatePacker.pack(x, y, z));
    }
}
