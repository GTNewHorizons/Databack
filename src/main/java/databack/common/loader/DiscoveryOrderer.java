package databack.common.loader;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;

/**
 * The default {@link IPackOrderer} implementation that preserves discovery order.
 *
 * <p>Returns the input list unchanged (as a new list). Since discovery produces packs in
 * filesystem order with the last-discovered pack having the highest priority, no re-ordering
 * is needed for the current requirement.
 */
public class DiscoveryOrderer implements IPackOrderer {

    @Override
    @Nonnull
    public List<Datapack> db$order(@Nonnull List<Datapack> packs) {
        return new ArrayList<Datapack>(packs);
    }
}
