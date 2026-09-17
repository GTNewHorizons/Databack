package databack.common.interop.modern_block;

import java.util.BitSet;

import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import databack.common.mixinext.ItemExt_Identity;
import databack.common.tags.Taggable;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/// A layer on top of [Item]s that emulates modern's item registry.
/// For ItemBlocks, [variant] encodes the block variant (same as [BlockIdentity]).
@EqualsAndHashCode
@ToString
public class ItemIdentity implements Taggable {

    public final ResourceLocation identityId;
    public final Item item;
    @Nullable
    public final BlockVariant variant;

    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private final BitSet tags = new BitSet();

    public ItemIdentity(ResourceLocation identityId, Item item, @Nullable BlockVariant variant) {
        this.identityId = identityId;
        this.item = item;
        this.variant = variant;
    }

    @Override
    public @NotNull BitSet db$getTagBitSet() {
        return tags;
    }

    public int getItemDamage() {
        if (variant == null) return 0;
        var property = ((ItemExt_Identity) item).db$getItemVariantProperty();
        return property != null ? property.getMeta(variant, 0) : variant.ordinal();
    }
}
