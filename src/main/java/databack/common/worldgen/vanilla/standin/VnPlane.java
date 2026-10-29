package databack.common.worldgen.vanilla.standin;

import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import databack.common.worldgen.rng.RandomSource;

/// Stand-in for the horizontal plane of directions: north, east, south, west, in that order
/// (`feature/probes/plane.tsv`).
@ApiStatus.Internal
public final class VnPlane implements Iterable<VnDirection> {

    public static final VnPlane HORIZONTAL = new VnPlane(VnDirection.NORTH, VnDirection.EAST, VnDirection.SOUTH,
        VnDirection.WEST);

    @NotNull
    private final List<VnDirection> directions;

    private VnPlane(VnDirection... directions) {
        this.directions = Collections.unmodifiableList(Arrays.asList(directions));
    }

    @NotNull
    @Override
    public Iterator<VnDirection> iterator() {
        return directions.iterator();
    }

    @NotNull
    public Stream<VnDirection> stream() {
        return directions.stream();
    }

    /// One bounded draw over the directions.
    @NotNull
    public VnDirection getRandomDirection(@NotNull RandomSource random) {
        return directions.get(random.nextInt(directions.size()));
    }
}
