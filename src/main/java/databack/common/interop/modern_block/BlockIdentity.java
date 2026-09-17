package databack.common.interop.modern_block;

import java.util.BitSet;

import net.minecraft.block.Block;
import net.minecraft.util.ResourceLocation;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import databack.common.mixinext.BlockExt_Identity;
import databack.common.tags.Taggable;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/// A layer on top of [Block]s that emulates modern's blocks.
@EqualsAndHashCode
@ToString
public class BlockIdentity implements Taggable {

    public final ResourceLocation identityId;
    public final Block block;
    @Nullable
    public final BlockVariant variant;

    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private final BitSet tags = new BitSet();

    public BlockIdentity(ResourceLocation identityId, Block block, BlockVariant variant) {
        this.identityId = identityId;
        this.block = block;
        this.variant = variant;
    }

    @Override
    public @NotNull BitSet db$getTagBitSet() {
        return tags;
    }

    public int getBlockMeta(int existing) {
        var blockExt = (BlockExt_Identity) block;

        var property = blockExt.db$getVariantProperty();

        if (property != null) {
            return property.getMeta(variant, existing);
        } else {
            return existing;
        }
    }
}
