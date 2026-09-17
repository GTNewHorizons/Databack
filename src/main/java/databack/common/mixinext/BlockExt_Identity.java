package databack.common.mixinext;

import org.jetbrains.annotations.NotNull;

import com.gtnewhorizon.gtnhlib.blockstate.core.MetaBlockProperty;
import databack.common.interop.modern_block.BlockVariant;
import databack.common.interop.modern_block.BlockIdentity;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;

public interface BlockExt_Identity {

    <T extends BlockVariant> MetaBlockProperty<T> db$getVariantProperty();
    <T extends BlockVariant> void db$setVariantProperty(MetaBlockProperty<T> property);

    @NotNull
    Reference2ObjectOpenHashMap<BlockVariant, BlockIdentity> db$getModernBlocks();

}
