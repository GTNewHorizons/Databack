package databack.common.worldgen.feature.standin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/// Stand-in for a holder set: either a list of holders, or a membership test by id (a tag, whose contents are not
/// listed).
@ApiStatus.Internal
public abstract class FtHolderSet {

    private FtHolderSet() {}

    /// Whether the set holds the value registered as `id` (namespaced).
    public abstract boolean contains(@NotNull String id);

    public abstract int size();

    @NotNull
    public abstract FtHolder get(int index);

    @NotNull
    public abstract Stream<FtHolder> stream();

    @NotNull
    @Contract("_ -> new")
    public static FtHolderSet of(@NotNull List<FtHolder> holders) {
        List<FtHolder> copy = Collections.unmodifiableList(new ArrayList<>(holders));
        return new FtHolderSet() {

            @Override
            public boolean contains(@NotNull String id) {
                for (FtHolder holder : copy) {
                    if (id.equals(holder.id)) return true;
                }
                return false;
            }

            @Override
            public int size() {
                return copy.size();
            }

            @NotNull
            @Override
            public FtHolder get(int index) {
                return copy.get(index);
            }

            @NotNull
            @Override
            public Stream<FtHolder> stream() {
                return copy.stream();
            }

            @Override
            public String toString() {
                return copy.toString();
            }
        };
    }

    /// The set of `values`, each made a holder by `holder`.
    @NotNull
    @Contract("_, _ -> new")
    public static <T> FtHolderSet direct(@NotNull Function<T, FtHolder> holder, @NotNull Collection<T> values) {
        List<FtHolder> holders = new ArrayList<>();
        for (T value : values) holders.add(holder.apply(value));
        return of(holders);
    }

    /// A set that only answers [#contains]; listing it throws.
    @NotNull
    @Contract("_, _ -> new")
    public static FtHolderSet matching(@NotNull String name, @NotNull Predicate<String> ids) {
        return new FtHolderSet() {

            @Override
            public boolean contains(@NotNull String id) {
                return ids.test(id);
            }

            @Override
            public int size() {
                throw unlisted();
            }

            @NotNull
            @Override
            public FtHolder get(int index) {
                throw unlisted();
            }

            @NotNull
            @Override
            public Stream<FtHolder> stream() {
                throw unlisted();
            }

            private UnsupportedOperationException unlisted() {
                return new UnsupportedOperationException("The contents of " + name + " are not listed");
            }

            @Override
            public String toString() {
                return name;
            }
        };
    }
}
