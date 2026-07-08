package databack.common.loader;

import java.util.List;
import java.util.Set;

import org.jetbrains.annotations.NotNull;

public interface DatapackWorldInfo extends IPackOrderer {

    List<String> getDatapackOrder();
    Set<String> getDisabledPacks();

    void enable(String pack);
    void disable(String pack);

    /// Adds or removes packs from the ordering/disabled lists when new packs are added or old packs are removed.
    void syncPackDeltas(@NotNull List<Datapack> packs);

    @Override
    @NotNull List<Datapack> order(@NotNull List<Datapack> packs);
}
