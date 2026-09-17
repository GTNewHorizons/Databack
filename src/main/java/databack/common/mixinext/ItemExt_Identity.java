package databack.common.mixinext;

import org.jetbrains.annotations.NotNull;

import com.gtnewhorizon.gtnhlib.blockstate.core.MetaBlockProperty;
import databack.common.interop.modern_block.BlockVariant;
import databack.common.interop.modern_block.ItemIdentity;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;

public interface ItemExt_Identity {

    <T extends BlockVariant> MetaBlockProperty<T> db$getItemVariantProperty();
    <T extends BlockVariant> void db$setItemVariantProperty(MetaBlockProperty<T> property);

    @NotNull
    Reference2ObjectOpenHashMap<BlockVariant, ItemIdentity> db$getModernItems();

}
