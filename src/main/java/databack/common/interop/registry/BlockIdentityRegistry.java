package databack.common.interop.registry;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Spliterator;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import net.minecraft.block.Block;
import net.minecraft.util.ResourceLocation;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.gtnewhorizon.gtnhlib.blockstate.core.MetaBlockProperty;
import databack.Databack;
import databack.common.interop.modern_block.BlockVariant;
import databack.common.interop.modern_block.BlockIdentity;
import databack.common.mixinext.BlockExt_Identity;
import it.unimi.dsi.fastutil.Function;
import it.unimi.dsi.fastutil.Pair;

public class BlockIdentityRegistry implements IProxyRegistry<BlockIdentity> {

    public static final Logger LOGGER = LogManager.getLogger(Databack.MODID + "|blocks|identity");

    public static final BlockIdentityRegistry INSTANCE = new BlockIdentityRegistry();

    private final BiMap<ResourceLocation, BlockIdentity> identities = HashBiMap.create();

    public <T extends Enum<T> & BlockVariant> void setVariantProperty(Block block, MetaBlockProperty<T> property, Function<T, String> idMapper) {
        //noinspection unchecked
        setVariantProperty(block, property, ((Class<T>) property.getType()).getEnumConstants(), idMapper);
    }

    public <T extends BlockVariant> void setVariantProperty(Block block, MetaBlockProperty<T> property, T[] variants, Function<T, String> idMapper) {
        ((BlockExt_Identity) block).db$setVariantProperty(property);

        for (T variant : variants) {
            register(new ResourceLocation(idMapper.apply(variant)), block, variant);
        }
    }

    public void register(String identityId, Block block, BlockVariant variant) {
        register(new ResourceLocation(identityId), block, variant);
    }

    public void register(ResourceLocation identityId, Block block, BlockVariant variant) {
        BlockExt_Identity blockExt = (BlockExt_Identity) block;

        if (blockExt.db$getVariantProperty() == null) {
            throw new IllegalStateException("Cannot register variant to Block that does not have a variant property. Call BlockIdentityRegistry.setVariantProperty first.");
        }

        BlockIdentity identity = new BlockIdentity(identityId, block, variant);

        var map = blockExt.db$getModernBlocks();

        map.put(variant, identity);
        identities.put(identityId, identity);
    }

    public void rename(Block block, String modernId) {
        rename(block, new ResourceLocation(modernId));
    }

    public void rename(Block block, ResourceLocation modernId) {
        BlockExt_Identity blockExt = (BlockExt_Identity) block;

        if (blockExt.db$getVariantProperty() != null) {
            throw new IllegalStateException("Cannot rename a Block with variants.");
        }

        BlockIdentity identity = new BlockIdentity(modernId, block, null);

        blockExt.db$getModernBlocks().defaultReturnValue(identity);
        identities.put(modernId, identity);
    }

    public BlockIdentity getBlockIdentity(Block block, int meta) {
        BlockExt_Identity blockExt = (BlockExt_Identity) block;

        var property = blockExt.db$getVariantProperty();

        if (property == null) {
            return blockExt.db$getModernBlocks().defaultReturnValue();
        } else {
            BlockVariant variant = property.getValue(meta);

            var map = blockExt.db$getModernBlocks();

            return map.get(variant);
        }
    }

    public Collection<BlockIdentity> getIdentitiesForBlock(Block block) {
        BlockExt_Identity blockExt = (BlockExt_Identity) block;

        var property = blockExt.db$getVariantProperty();

        if (property == null) {
            return Collections.singletonList(blockExt.db$getModernBlocks().defaultReturnValue());
        } else {
            return blockExt.db$getModernBlocks().values();
        }
    }

    @Override
    public BlockIdentity getObject(ResourceLocation identityId) {
        var registered = identities.get(identityId);

        if (registered != null) return registered;

        Block block = ProxyBlockRegistry.INSTANCE.getObject(identityId);

        if (block == null) return null;

        BlockExt_Identity blockExt = (BlockExt_Identity) block;

        return blockExt.db$getModernBlocks().defaultReturnValue();
    }

    @Override
    public ResourceLocation getIdForObject(BlockIdentity blockIdentity) {
        return blockIdentity.identityId;
    }

    public @NotNull List<BlockIdentity> domain() {
        @SuppressWarnings("unchecked")
        var spliter = (Spliterator<Block>) Block.blockRegistry.spliterator();

        return StreamSupport.stream(spliter, false)
            .flatMap(block -> BlockIdentityRegistry.INSTANCE.getIdentitiesForBlock(block).stream())
            .collect(Collectors.toList());
    }

    @Override
    public @NotNull Iterator<Pair<ResourceLocation, BlockIdentity>> iterator() {
        @SuppressWarnings("unchecked")
        var spliter = (Spliterator<Block>) Block.blockRegistry.spliterator();

        return StreamSupport.stream(spliter, false)
            .flatMap(block -> BlockIdentityRegistry.INSTANCE.getIdentitiesForBlock(block).stream())
            .map(i -> Pair.of(i.identityId, i))
            .iterator();
    }
}
