package databack.common.worldgen.feature.standin;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/// What block states are, as the world being populated sees it: their tags, and the behaviour vanilla asks of a
/// state with no world at hand. One instance serves every state; [#install] replaces it.
@ApiStatus.Internal
public interface FtBlockFacts {

    /// `blockId` and `tagId` are namespaced, without `#`.
    boolean isInTag(@NotNull String blockId, @NotNull String tagId);

    /// Whether placing a block may overwrite the state.
    boolean isReplaceable(@NotNull FtBlockState state);

    /// Whether the state is solid: entities cannot move through it.
    boolean isSolid(@NotNull FtBlockState state);

    /// The installed facts. Throws when none are.
    @NotNull
    static FtBlockFacts get() {
        FtBlockFacts facts = Holder.facts;
        if (facts == null) throw new IllegalStateException("No block facts installed");
        return facts;
    }

    static void install(@NotNull FtBlockFacts facts) {
        Holder.facts = facts;
    }

    final class Holder {

        private static volatile FtBlockFacts facts;

        private Holder() {}
    }
}
