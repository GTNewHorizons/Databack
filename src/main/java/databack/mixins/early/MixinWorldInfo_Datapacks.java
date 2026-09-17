package databack.mixins.early;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.world.storage.WorldInfo;
import net.minecraftforge.common.util.Constants.NBT;

import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import databack.common.loader.Datapack;
import databack.common.loader.DatapackWorldInfo;

@Mixin(WorldInfo.class)
public class MixinWorldInfo_Datapacks implements DatapackWorldInfo {

    @Unique
    private final List<String> db$datapackOrder = new ArrayList<>();

    @Unique
    private final HashSet<String> db$disabledDatapacks = new HashSet<>();

    public void db$loadDatapackInfo(NBTTagCompound tag) {
        db$datapackOrder.clear();
        db$disabledDatapacks.clear();

        NBTTagList disabled = tag.getTagList("Disabled", NBT.TAG_STRING);
        NBTTagList order = tag.getTagList("Order", NBT.TAG_STRING);

        for (var t : ((AccessorNBTTagList) disabled).<NBTTagString>getTagList()) {
            db$disabledDatapacks.add(t.func_150285_a_());
        }

        for (var t : ((AccessorNBTTagList) order).<NBTTagString>getTagList()) {
            db$datapackOrder.add(t.func_150285_a_());
        }
    }

    @SuppressWarnings("DataFlowIssue")
    @Inject(method = "<init>(Lnet/minecraft/world/storage/WorldInfo;)V", at = @At("TAIL"))
    public void db$copy(WorldInfo source, CallbackInfo ci) {
        db$datapackOrder.clear();
        db$disabledDatapacks.clear();

        db$datapackOrder.addAll(((MixinWorldInfo_Datapacks) (Object) source).db$datapackOrder);
        db$disabledDatapacks.addAll(((MixinWorldInfo_Datapacks) (Object) source).db$disabledDatapacks);
    }

    public NBTTagCompound db$saveDatapackInfo() {
        NBTTagCompound tag = new NBTTagCompound();

        NBTTagList disabled = new NBTTagList();
        tag.setTag("Disabled", disabled);

        NBTTagList order = new NBTTagList();
        tag.setTag("Order", order);

        for (String d : db$disabledDatapacks) {
            disabled.appendTag(new NBTTagString(d));
        }

        for (String p : db$datapackOrder) {
            order.appendTag(new NBTTagString(p));
        }

        return tag;
    }

    @Override
    public List<String> db$getDatapackOrder() {
        return db$datapackOrder;
    }

    @Override
    public Set<String> db$getDisabledPacks() {
        return db$disabledDatapacks;
    }

    @Override
    public void db$enable(String pack) {
        db$disabledDatapacks.remove(pack);
    }

    @Override
    public void db$disable(String pack) {
        db$disabledDatapacks.add(pack);
    }

    @Override
    public void db$syncPackDeltas(@NotNull List<Datapack> packs) {
        List<String> present = packs.stream().map(Datapack::getPackId).collect(Collectors.toList());

        db$disabledDatapacks.removeIf(p -> !present.contains(p));
        db$datapackOrder.removeIf(p -> !present.contains(p));

        for (String presentPack : present) {
            if (!db$datapackOrder.contains(presentPack)) {
                db$datapackOrder.add(presentPack);
            }
        }
    }

    @Override
    public @NotNull List<Datapack> db$order(@NotNull List<Datapack> packs) {
        packs = new ArrayList<>(packs);

        packs.forEach(pack -> {
            pack.setEnabled(!db$disabledDatapacks.contains(pack.getPackId()));
        });

        // Sort the packs, putting new packs at the end
        packs.sort(Comparator.comparingInt(pack -> {
            int index = db$datapackOrder.indexOf(pack.getPackId());

            if (index == -1) index = Integer.MAX_VALUE;

            return index;
        }));

        return packs;
    }
}
