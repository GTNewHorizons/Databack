package databack.common.interop.registry;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Spliterator;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import net.minecraft.block.Block;
import net.minecraft.util.ResourceLocation;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.gtnewhorizon.gtnhlib.blockstate.core.MetaBlockProperty;
import com.gtnewhorizon.gtnhlib.blockstate.properties.IntegerBlockProperty.IntegerMetaBlockProperty;
import com.gtnewhorizon.gtnhlib.blockstate.registry.BlockPropertyRegistry;
import databack.Databack;
import databack.common.interop.modern_block.BlockVariant;
import databack.common.interop.modern_block.BlockIdentity;
import databack.common.interop.modern_block.NamedVariant;
import databack.common.interop.modern_block.NamedVariantProperty;
import databack.common.interop.modern_block.StateForms;
import databack.common.mixinext.BlockExt_Identity;
import it.unimi.dsi.fastutil.Pair;

public class BlockIdentityRegistry implements IProxyRegistry<BlockIdentity> {

    public static final Logger LOGGER = LogManager.getLogger(Databack.MODID + "|blocks|identity");

    public static final BlockIdentityRegistry INSTANCE = new BlockIdentityRegistry();

    private static final String MINECRAFT = "minecraft";

    private final HashMap<ResourceLocation, BlockIdentity> identities = new HashMap<>();
    private final StateForms stateForms = new StateForms();

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

    /// Gives each meta of `block` one of `modernIds`, chosen by `backing`'s decoded index (which wraps around). Each
    /// id is claimed: looking it up returns this block, replacing earlier claims.
    public NamedVariantProperty setVariants(Block block, IntegerMetaBlockProperty backing, String... modernIds) {
        return setVariants(block, backing, true, modernIds);
    }

    /// Like [#setVariants(Block, IntegerMetaBlockProperty, String...)], but the ids aren't claimed: this block reports
    /// them, but looking them up returns whichever block claimed them. Used for blocks that are a state of another
    /// block in modern (lit furnaces, double slabs, flowing fluids, etc).
    public NamedVariantProperty aliasVariants(Block block, IntegerMetaBlockProperty backing, String... modernIds) {
        return setVariants(block, backing, false, modernIds);
    }

    private NamedVariantProperty setVariants(Block block, IntegerMetaBlockProperty backing, boolean claim, String... modernIds) {
        ResourceLocation[] ids = new ResourceLocation[modernIds.length];
        String[] names = new String[modernIds.length];

        for (int i = 0; i < modernIds.length; i++) {
            ids[i] = new ResourceLocation(modernIds[i]);
            names[i] = ids[i].getResourcePath();
        }

        var property = new NamedVariantProperty(backing, names);

        BlockPropertyRegistry.registerProperty(block, property);
        ((BlockExt_Identity) block).db$setVariantProperty(property);

        NamedVariant[] variants = property.getVariants();

        for (int i = 0; i < variants.length; i++) {
            register(ids[i], block, variants[i], claim);
        }

        return property;
    }

    public void register(String identityId, Block block, BlockVariant variant) {
        register(new ResourceLocation(identityId), block, variant);
    }

    public void register(ResourceLocation identityId, Block block, BlockVariant variant) {
        register(identityId, block, variant, true);
    }

    private void register(ResourceLocation identityId, Block block, BlockVariant variant, boolean claim) {
        BlockExt_Identity blockExt = (BlockExt_Identity) block;

        if (blockExt.db$getVariantProperty() == null) {
            throw new IllegalStateException("Cannot register variant to Block that does not have a variant property. Call BlockIdentityRegistry.setVariantProperty first.");
        }

        BlockIdentity identity = new BlockIdentity(identityId, block, variant);

        var map = blockExt.db$getModernBlocks();

        map.put(variant, identity);
        if (claim) identities.put(identityId, identity);
    }

    public void rename(Block block, String modernId) {
        rename(block, new ResourceLocation(modernId), true);
    }

    public void rename(Block block, ResourceLocation modernId) {
        rename(block, modernId, true);
    }

    /// Names `block` as `modernId` without claiming the id (see [#aliasVariants]).
    public void alias(Block block, String modernId) {
        rename(block, new ResourceLocation(modernId), false);
    }

    private void rename(Block block, ResourceLocation modernId, boolean claim) {
        BlockExt_Identity blockExt = (BlockExt_Identity) block;

        if (blockExt.db$getVariantProperty() != null) {
            throw new IllegalStateException("Cannot rename a Block with variants.");
        }

        BlockIdentity identity = new BlockIdentity(modernId, block, null);

        blockExt.db$getModernBlocks().defaultReturnValue(identity);
        if (claim) identities.put(modernId, identity);
    }

    /// Makes `block` the 1.7 form of its modern block(s) when the modern state has `property` = `value`, e.g.
    /// `stateForm(Blocks.lit_furnace, "lit", "true")`. Only ids `block` reports but doesn't claim become forms, so this
    /// goes after [#alias] / [#aliasVariants] (or after a later block claimed the ids).
    public void stateForm(Block block, String property, String value) {
        for (BlockIdentity identity : getIdentitiesForBlock(block)) {
            BlockIdentity claimed = identities.get(identity.identityId);

            if (claimed != null && claimed.block == block) continue;

            stateForms.add(property, value, identity);
        }
    }

    /// The 1.7 form of `modernId` that `properties` select, or null to use the block that claims it.
    @Nullable
    public StateForms.Form findStateForm(ResourceLocation modernId, @Nullable Map<String, String> properties) {
        return stateForms.select(modernId, properties);
    }

    /// The state form `identity` is (`lit_furnace`'s is `lit=true`), or null if it is the block that claims its id.
    @Nullable
    public StateForms.Form stateFormOf(BlockIdentity identity) {
        return stateForms.formOf(identity);
    }

    /// Claims the name of every vanilla block that was never renamed: [VanillaBlockIdentities] renames every 1.7 name
    /// that isn't also the same block in modern, so the rest are already modern. Call after all identities are set up.
    public void claimUnrenamedVanilla() {
        for (BlockIdentity identity : domain()) {
            ResourceLocation id = identity.identityId;

            if (!MINECRAFT.equals(id.getResourceDomain())) continue;
            // Renamed or aliased blocks report another block's name
            if (!id.equals(ProxyBlockRegistry.INSTANCE.getIdForObject(identity.block))) continue;

            identities.putIfAbsent(id, identity);
        }
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

        // 1.7's names aren't modern names (minecraft:snow is a snow layer in modern), so only claimed ids resolve
        if (MINECRAFT.equals(identityId.getResourceDomain())) return null;

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
