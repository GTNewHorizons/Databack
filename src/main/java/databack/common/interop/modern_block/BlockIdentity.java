package databack.common.interop.modern_block;

import java.util.BitSet;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.block.Block;
import net.minecraft.util.ResourceLocation;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import databack.common.mixinext.BlockExt_Identity;
import databack.common.tags.Taggable;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/// A layer on top of [Block]s that emulates modern's blocks. Several 1.7 forms can share one modern id (lit
/// furnaces, double slabs, EFR duplicates); identities are equal by id alone and share their tags, so tag checks and
/// block predicates treat every form as the modern block.
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString
public class BlockIdentity implements Taggable {

    private static final ConcurrentHashMap<ResourceLocation, BitSet> TAGS_BY_ID = new ConcurrentHashMap<>();

    @EqualsAndHashCode.Include
    public final ResourceLocation identityId;
    public final Block block;
    @Nullable
    public final BlockVariant variant;

    @ToString.Exclude
    private final BitSet tags;

    public BlockIdentity(ResourceLocation identityId, Block block, BlockVariant variant) {
        this.identityId = Objects.requireNonNull(identityId, "BlockIdentity's id was null: " + block + ", " + variant);
        this.block = block;
        this.variant = variant;
        this.tags = TAGS_BY_ID.computeIfAbsent(identityId, id -> new BitSet());
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
