package databack.mixins.early;

import net.minecraft.item.Item;

import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import com.gtnewhorizon.gtnhlib.blockstate.core.MetaBlockProperty;
import databack.common.interop.modern_block.BlockVariant;
import databack.common.interop.modern_block.ItemIdentity;
import databack.common.mixinext.ItemExt_Identity;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;

@Mixin(Item.class)
public class MixinItem_Identity implements ItemExt_Identity {

    @Unique
    private MetaBlockProperty<BlockVariant> db$itemVariantProperty;

    @Unique
    private Reference2ObjectOpenHashMap<BlockVariant, ItemIdentity> db$modernItems;

    @Override
    public <T extends BlockVariant> MetaBlockProperty<T> db$getItemVariantProperty() {
        //noinspection unchecked
        return (MetaBlockProperty<T>) db$itemVariantProperty;
    }

    @Override
    public <T extends BlockVariant> void db$setItemVariantProperty(MetaBlockProperty<T> property) {
        //noinspection unchecked
        db$itemVariantProperty = (MetaBlockProperty<BlockVariant>) property;
    }

    @Override
    public @NotNull Reference2ObjectOpenHashMap<BlockVariant, ItemIdentity> db$getModernItems() {
        if (db$modernItems == null) {
            db$modernItems = new Reference2ObjectOpenHashMap<>();
        }
        return db$modernItems;
    }
}
