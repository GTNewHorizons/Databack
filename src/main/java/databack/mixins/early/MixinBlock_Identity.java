package databack.mixins.early;

import net.minecraft.block.Block;

import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import com.gtnewhorizon.gtnhlib.blockstate.core.MetaBlockProperty;
import databack.common.interop.modern_block.BlockVariant;
import databack.common.interop.modern_block.BlockIdentity;
import databack.common.interop.registry.ProxyBlockRegistry;
import databack.common.mixinext.BlockExt_Identity;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;

@Mixin(Block.class)
public class MixinBlock_Identity implements BlockExt_Identity {

    @Unique
    private MetaBlockProperty<BlockVariant> db$variantProperty;

    @Unique
    private Reference2ObjectOpenHashMap<BlockVariant, BlockIdentity> db$modernBlocks;

    @Override
    public <T extends BlockVariant> MetaBlockProperty<T> db$getVariantProperty() {
        //noinspection unchecked
        return (MetaBlockProperty<T>) db$variantProperty;
    }

    @Override
    public <T extends BlockVariant> void db$setVariantProperty(MetaBlockProperty<T> property) {
        //noinspection unchecked
        db$variantProperty = (MetaBlockProperty<BlockVariant>) property;
    }

    @Override
    public @NotNull Reference2ObjectOpenHashMap<BlockVariant, BlockIdentity> db$getModernBlocks() {
        if (db$modernBlocks == null) {
            db$modernBlocks = new Reference2ObjectOpenHashMap<>();

            Block self = (Block) (Object) this;

            db$modernBlocks.defaultReturnValue(new BlockIdentity(ProxyBlockRegistry.INSTANCE.getIdForObject(self), self, null));
        }

        return db$modernBlocks;
    }
}
