package databack.common.worldgen.vanilla.standin;

import java.util.Iterator;
import java.util.NoSuchElementException;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/// Stand-in for block positions, mutable ones and integer vectors alike. Only positions vanilla made mutable are ever
/// changed in place; everything else returns a new position. Equal by coordinates.
@ApiStatus.Internal
public final class VnBlockPos {

    public static final VnBlockPos ZERO = new VnBlockPos(0, 0, 0);

    private int x, y, z;

    public VnBlockPos() {}

    public VnBlockPos(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getZ() {
        return z;
    }

    @NotNull
    @Contract("_, _, _ -> this")
    public VnBlockPos set(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
        return this;
    }

    @NotNull
    @Contract("_ -> this")
    public VnBlockPos set(@NotNull VnBlockPos pos) {
        return set(pos.x, pos.y, pos.z);
    }

    @NotNull
    @Contract("_ -> this")
    public VnBlockPos setY(int y) {
        this.y = y;
        return this;
    }

    /// This becomes `pos` moved one block towards `direction`.
    @NotNull
    @Contract("_, _ -> this")
    public VnBlockPos setWithOffset(@NotNull VnBlockPos pos, @NotNull VnDirection direction) {
        return set(pos.x + direction.getStepX(), pos.y + direction.getStepY(), pos.z + direction.getStepZ());
    }

    /// This becomes `pos` offset by `(dx, dy, dz)`.
    @NotNull
    @Contract("_, _, _, _ -> this")
    public VnBlockPos setWithOffset(@NotNull VnBlockPos pos, int dx, int dy, int dz) {
        return set(pos.x + dx, pos.y + dy, pos.z + dz);
    }

    @NotNull
    @Contract("_ -> this")
    public VnBlockPos move(@NotNull VnDirection direction) {
        return setWithOffset(this, direction);
    }

    /// Moves `distance` blocks towards `direction`.
    @NotNull
    @Contract("_, _ -> this")
    public VnBlockPos move(@NotNull VnDirection direction, int distance) {
        return set(x + direction.getStepX() * distance, y + direction.getStepY() * distance,
            z + direction.getStepZ() * distance);
    }

    /// The position `distance` blocks towards `direction`.
    @NotNull
    @Contract(value = "_, _ -> new", pure = true)
    public VnBlockPos relative(@NotNull VnDirection direction, int distance) {
        return offset(direction.getStepX() * distance, direction.getStepY() * distance,
            direction.getStepZ() * distance);
    }

    @NotNull
    @Contract(value = "_ -> new", pure = true)
    public VnBlockPos relative(@NotNull VnDirection direction) {
        return relative(direction, 1);
    }

    /// The taxicab distance to `other`.
    @Contract(pure = true)
    public int distManhattan(@NotNull VnBlockPos other) {
        return Math.abs(x - other.x) + Math.abs(y - other.y) + Math.abs(z - other.z);
    }

    @NotNull
    @Contract(value = "_, _, _ -> new", pure = true)
    public VnBlockPos offset(int dx, int dy, int dz) {
        return new VnBlockPos(x + dx, y + dy, z + dz);
    }

    @NotNull
    @Contract(value = "_ -> new", pure = true)
    public VnBlockPos offset(@NotNull VnBlockPos by) {
        return offset(by.x, by.y, by.z);
    }

    @NotNull
    @Contract(value = "-> new", pure = true)
    public VnBlockPos above() {
        return offset(0, 1, 0);
    }

    @NotNull
    @Contract(value = "-> new", pure = true)
    public VnBlockPos below() {
        return offset(0, -1, 0);
    }

    @NotNull
    @Contract(value = "_ -> new", pure = true)
    public VnBlockPos above(int distance) {
        return offset(0, distance, 0);
    }

    @NotNull
    @Contract(value = "_ -> new", pure = true)
    public VnBlockPos below(int distance) {
        return offset(0, -distance, 0);
    }

    @NotNull
    @Contract(value = "-> new", pure = true)
    public VnBlockPos north() {
        return offset(0, 0, -1);
    }

    @NotNull
    @Contract(value = "_ -> new", pure = true)
    public VnBlockPos north(int distance) {
        return offset(0, 0, -distance);
    }

    @NotNull
    @Contract(value = "-> new", pure = true)
    public VnBlockPos south() {
        return offset(0, 0, 1);
    }

    @NotNull
    @Contract(value = "_ -> new", pure = true)
    public VnBlockPos south(int distance) {
        return offset(0, 0, distance);
    }

    @NotNull
    @Contract(value = "-> new", pure = true)
    public VnBlockPos west() {
        return offset(-1, 0, 0);
    }

    @NotNull
    @Contract(value = "_ -> new", pure = true)
    public VnBlockPos west(int distance) {
        return offset(-distance, 0, 0);
    }

    @NotNull
    @Contract(value = "-> new", pure = true)
    public VnBlockPos east() {
        return offset(1, 0, 0);
    }

    @NotNull
    @Contract(value = "_ -> new", pure = true)
    public VnBlockPos east(int distance) {
        return offset(distance, 0, 0);
    }

    @NotNull
    @Contract(value = "_ -> new", pure = true)
    public VnBlockPos atY(int y) {
        return new VnBlockPos(x, y, z);
    }

    @NotNull
    @Contract(value = "-> new", pure = true)
    public VnBlockPos mutable() {
        return new VnBlockPos(x, y, z);
    }

    @NotNull
    @Contract(value = "-> new", pure = true)
    public VnBlockPos immutable() {
        return new VnBlockPos(x, y, z);
    }

    /// Every position of the box spanned by `a` and `b` (corners in any order), x fastest, then y, then z
    /// (`feature/probes/between_closed.tsv`). The iterator reuses one position, as vanilla's does.
    @NotNull
    public static Iterable<VnBlockPos> betweenClosed(@NotNull VnBlockPos a, @NotNull VnBlockPos b) {
        int minX = Math.min(a.x, b.x), minY = Math.min(a.y, b.y), minZ = Math.min(a.z, b.z);
        int maxX = Math.max(a.x, b.x), maxY = Math.max(a.y, b.y), maxZ = Math.max(a.z, b.z);
        return () -> new Iterator<VnBlockPos>() {

            private final VnBlockPos cursor = new VnBlockPos();
            private int nextX = minX, nextY = minY, nextZ = minZ;

            @Override
            public boolean hasNext() {
                return nextZ <= maxZ;
            }

            @Override
            public VnBlockPos next() {
                if (!hasNext()) throw new NoSuchElementException();
                cursor.set(nextX, nextY, nextZ);
                if (++nextX > maxX) {
                    nextX = minX;
                    if (++nextY > maxY) {
                        nextY = minY;
                        nextZ++;
                    }
                }
                return cursor;
            }
        };
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof VnBlockPos)) return false;
        VnBlockPos other = (VnBlockPos) o;
        return x == other.x && y == other.y && z == other.z;
    }

    @Override
    public int hashCode() {
        return (y + z * 31) * 31 + x;
    }

    @Override
    public String toString() {
        return x + " " + y + " " + z;
    }
}
