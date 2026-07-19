package databack.common.loader;

import java.util.List;
import java.util.Set;

import net.minecraft.nbt.NBTTagCompound;

import org.jetbrains.annotations.NotNull;

public interface DatapackWorldInfo extends IPackOrderer {

    NBTTagCompound db$saveDatapackInfo();
    void db$loadDatapackInfo(NBTTagCompound tag);

    List<String> db$getDatapackOrder();
    Set<String> db$getDisabledPacks();

    void db$enable(String pack);
    void db$disable(String pack);

    /// Adds or removes packs from the ordering/disabled lists when new packs are added or old packs are removed.
    void db$syncPackDeltas(@NotNull List<Datapack> packs);

    @Override
    @NotNull List<Datapack> db$order(@NotNull List<Datapack> packs);
}
