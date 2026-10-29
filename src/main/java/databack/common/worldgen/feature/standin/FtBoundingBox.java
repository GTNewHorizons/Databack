package databack.common.worldgen.feature.standin;

import java.util.Optional;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import databack.common.worldgen.vanilla.standin.VnBlockPos;

/// Stand-in for a box of blocks, bounds inclusive.
@ApiStatus.Internal
public final class FtBoundingBox {

    private final int minX, minY, minZ, maxX, maxY, maxZ;

    public FtBoundingBox(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        this.minX = Math.min(minX, maxX);
        this.minY = Math.min(minY, maxY);
        this.minZ = Math.min(minZ, maxZ);
        this.maxX = Math.max(minX, maxX);
        this.maxY = Math.max(minY, maxY);
        this.maxZ = Math.max(minZ, maxZ);
    }

    /// The smallest box holding every one of `positions`, or none if there are none.
    @NotNull
    public static Optional<FtBoundingBox> encapsulatingPositions(@NotNull Iterable<VnBlockPos> positions) {
        FtBoundingBox box = null;
        for (VnBlockPos p : positions) {
            box = box == null ? new FtBoundingBox(p.getX(), p.getY(), p.getZ(), p.getX(), p.getY(), p.getZ())
                : new FtBoundingBox(Math.min(box.minX, p.getX()), Math.min(box.minY, p.getY()),
                    Math.min(box.minZ, p.getZ()), Math.max(box.maxX, p.getX()), Math.max(box.maxY, p.getY()),
                    Math.max(box.maxZ, p.getZ()));
        }
        return Optional.ofNullable(box);
    }

    /// The box grown by `x`, `y` and `z` blocks on each side.
    @NotNull
    @Contract(value = "_, _, _ -> new", pure = true)
    public FtBoundingBox inflatedBy(int x, int y, int z) {
        return new FtBoundingBox(minX - x, minY - y, minZ - z, maxX + x, maxY + y, maxZ + z);
    }

    public boolean isInside(@NotNull VnBlockPos pos) {
        return pos.getX() >= minX && pos.getX() <= maxX && pos.getY() >= minY && pos.getY() <= maxY
            && pos.getZ() >= minZ && pos.getZ() <= maxZ;
    }

    public int getXSpan() {
        return maxX - minX + 1;
    }

    public int getYSpan() {
        return maxY - minY + 1;
    }

    public int getZSpan() {
        return maxZ - minZ + 1;
    }

    public int minX() {
        return minX;
    }

    public int minY() {
        return minY;
    }

    public int minZ() {
        return minZ;
    }

    public int maxX() {
        return maxX;
    }

    public int maxY() {
        return maxY;
    }

    public int maxZ() {
        return maxZ;
    }
}
