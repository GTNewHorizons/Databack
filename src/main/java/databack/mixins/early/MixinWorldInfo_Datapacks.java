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
    private final List<String> gtnhlib$datapackOrder = new ArrayList<>();

    @Unique
    private final HashSet<String> gtnhlib$disabledDatapacks = new HashSet<>();

    @Inject(method = "<init>(Lnet/minecraft/nbt/NBTTagCompound;)V", at = @At("TAIL"))
    public void gtnhlib$load(NBTTagCompound dataTag, CallbackInfo ci) {
        gtnhlib$datapackOrder.clear();
        gtnhlib$disabledDatapacks.clear();

        NBTTagCompound datapacks = dataTag.getCompoundTag("DataPacks");

        if (datapacks != null) {
            NBTTagList disabled = datapacks.getTagList("Disabled", NBT.TAG_STRING);
            NBTTagList order = datapacks.getTagList("Order", NBT.TAG_STRING);

            for (var t : ((AccessorNBTTagList) disabled).<NBTTagString>getTagList()) {
                gtnhlib$disabledDatapacks.add(t.func_150285_a_());
            }

            for (var t : ((AccessorNBTTagList) order).<NBTTagString>getTagList()) {
                gtnhlib$datapackOrder.add(t.func_150285_a_());
            }
        }
    }

    @SuppressWarnings("DataFlowIssue")
    @Inject(method = "<init>(Lnet/minecraft/world/storage/WorldInfo;)V", at = @At("TAIL"))
    public void gtnhlib$copy(WorldInfo source, CallbackInfo ci) {
        gtnhlib$datapackOrder.clear();
        gtnhlib$disabledDatapacks.clear();

        gtnhlib$datapackOrder.addAll(((MixinWorldInfo_Datapacks) (Object) source).gtnhlib$datapackOrder);
        gtnhlib$disabledDatapacks.addAll(((MixinWorldInfo_Datapacks) (Object) source).gtnhlib$disabledDatapacks);
    }

    @Inject(method = "updateTagCompound", at = @At("TAIL"))
    public void gtnhlib$save(NBTTagCompound baseTag, NBTTagCompound playerTag, CallbackInfo ci) {
        NBTTagCompound datapacks = new NBTTagCompound();
        baseTag.setTag("DataPacks", datapacks);

        NBTTagList disabled = new NBTTagList();
        datapacks.setTag("Disabled", disabled);

        NBTTagList order = new NBTTagList();
        datapacks.setTag("Order", order);

        for (String d : gtnhlib$disabledDatapacks) {
            disabled.appendTag(new NBTTagString(d));
        }

        for (String p : gtnhlib$datapackOrder) {
            order.appendTag(new NBTTagString(p));
        }
    }

    @Override
    public List<String> getDatapackOrder() {
        return gtnhlib$datapackOrder;
    }

    @Override
    public Set<String> getDisabledPacks() {
        return gtnhlib$disabledDatapacks;
    }

    @Override
    public void enable(String pack) {
        gtnhlib$disabledDatapacks.remove(pack);
    }

    @Override
    public void disable(String pack) {
        gtnhlib$disabledDatapacks.add(pack);
    }

    @Override
    public void syncPackDeltas(@NotNull List<Datapack> packs) {
        List<String> present = packs.stream().map(Datapack::getPackId).collect(Collectors.toList());

        gtnhlib$disabledDatapacks.removeIf(p -> !present.contains(p));
        gtnhlib$datapackOrder.removeIf(p -> !present.contains(p));

        for (String presentPack : present) {
            if (!gtnhlib$datapackOrder.contains(presentPack)) {
                gtnhlib$datapackOrder.add(presentPack);
            }
        }
    }

    @Override
    public @NotNull List<Datapack> order(@NotNull List<Datapack> packs) {
        packs = new ArrayList<>(packs);

        packs.forEach(pack -> {
            pack.setEnabled(!gtnhlib$disabledDatapacks.contains(pack.getPackId()));
        });

        // Sort the packs, putting new packs at the end
        packs.sort(Comparator.comparingInt(pack -> {
            int index = gtnhlib$datapackOrder.indexOf(pack.getPackId());

            if (index == -1) index = Integer.MAX_VALUE;

            return index;
        }));

        return packs;
    }
}
