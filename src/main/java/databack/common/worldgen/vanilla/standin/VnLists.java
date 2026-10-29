package databack.common.worldgen.vanilla.standin;

import java.util.List;
import java.util.NoSuchElementException;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/// The list methods Java 21 added, for vanilla code on older runtimes ([databack.common.worldgen.vanilla
/// .VanillaMappings.Builder#redirectInstance]).
@ApiStatus.Internal
public final class VnLists {

    private VnLists() {}

    /// Throws [NoSuchElementException] when `list` is empty.
    public static Object getFirst(@NotNull List<?> list) {
        if (list.isEmpty()) throw new NoSuchElementException();
        return list.get(0);
    }

    /// Throws [NoSuchElementException] when `list` is empty.
    public static Object getLast(@NotNull List<?> list) {
        if (list.isEmpty()) throw new NoSuchElementException();
        return list.get(list.size() - 1);
    }
}
