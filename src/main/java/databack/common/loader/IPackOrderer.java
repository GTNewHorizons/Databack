package databack.common.loader;

import java.util.List;

import javax.annotation.Nonnull;

/**
 * Defines the replaceable ordering step in the datapack loading pipeline.
 *
 * <p>Given the list of validated {@link Datapack} objects in discovery order, returns them in
 * the desired priority order (lowest priority at index 0, highest priority at the last index).
 * Implementations must not modify the input list, and must not drop or duplicate any element.
 */
public interface IPackOrderer {

    /**
     * @param packs list of validated datapacks in discovery order; not null, may be empty
     * @return a new list of the same packs in priority order (index 0 = lowest priority)
     */
    @Nonnull
    List<Datapack> db$order(@Nonnull List<Datapack> packs);
}
