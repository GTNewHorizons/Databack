package databack.common.interop.registry;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Spliterator;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;

import org.jetbrains.annotations.NotNull;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.gtnewhorizon.gtnhlib.blockstate.core.MetaBlockProperty;
import databack.common.interop.modern_block.BlockVariant;
import databack.common.interop.modern_block.ItemIdentity;
import databack.common.mixinext.ItemExt_Identity;
import it.unimi.dsi.fastutil.Function;
import it.unimi.dsi.fastutil.Pair;

public class ItemIdentityRegistry implements IProxyRegistry<ItemIdentity> {

    public static final ItemIdentityRegistry INSTANCE = new ItemIdentityRegistry();

    private final BiMap<ResourceLocation, ItemIdentity> identities = HashBiMap.create();

    private ItemIdentityRegistry() {}

    public <T extends Enum<T> & BlockVariant> void setVariantProperty(Block block, MetaBlockProperty<T> property, Function<T, String> idMapper) {
        //noinspection unchecked
        setVariantProperty(block, property, ((Class<T>) property.getType()).getEnumConstants(), idMapper);
    }

    public <T extends BlockVariant> void setVariantProperty(Block block, MetaBlockProperty<T> property, T[] variants, Function<T, String> idMapper) {
        Item item = Item.getItemFromBlock(block);
        if (item == null) return;

        ((ItemExt_Identity) item).db$setItemVariantProperty(property);

        for (T variant : variants) {
            ResourceLocation id = new ResourceLocation(idMapper.apply(variant));
            register(item, new ItemIdentity(id, item, variant));
        }
    }

    public void rename(Block block, String modernId) {
        rename(block, new ResourceLocation(modernId));
    }

    public void rename(Block block, ResourceLocation modernId) {
        Item item = Item.getItemFromBlock(block);
        if (item == null) return;

        ItemExt_Identity ext = (ItemExt_Identity) item;

        if (ext.db$getItemVariantProperty() != null) {
            throw new IllegalStateException("Cannot rename an Item with variants.");
        }

        ItemIdentity identity = new ItemIdentity(modernId, item, null);
        ext.db$getModernItems().defaultReturnValue(identity);
        identities.put(modernId, identity);
    }

    private void register(Item item, ItemIdentity identity) {
        identities.put(identity.identityId, identity);
        ((ItemExt_Identity) item).db$getModernItems().put(identity.variant, identity);
    }

    public ItemIdentity getItemIdentity(Item item, int damage) {
        ItemExt_Identity ext = (ItemExt_Identity) item;
        var property = ext.db$getItemVariantProperty();
        if (property == null) return ext.db$getModernItems().defaultReturnValue();
        return ext.db$getModernItems().get(property.getValue(damage));
    }

    public Collection<ItemIdentity> getIdentitiesForItem(Item item) {
        ItemExt_Identity ext = (ItemExt_Identity) item;
        var property = ext.db$getItemVariantProperty();

        if (property == null) {
            var def = ext.db$getModernItems().defaultReturnValue();
            if (def == null) return Collections.emptyList();
            return Collections.singletonList(def);
        } else {
            return ext.db$getModernItems().values();
        }
    }

    public @NotNull List<ItemIdentity> domain() {
        @SuppressWarnings("unchecked")
        var spliter = (Spliterator<Item>) Item.itemRegistry.spliterator();

        return StreamSupport.stream(spliter, false)
            .flatMap(item -> ItemIdentityRegistry.INSTANCE.getIdentitiesForItem(item).stream())
            .collect(Collectors.toList());
    }

    @Override
    public ItemIdentity getObject(ResourceLocation id) {
        var registered = identities.get(id);

        if (registered != null) return registered;

        Item item = (Item) Item.itemRegistry.getObject(id.toString());

        if (item == null) return null;

        return ((ItemExt_Identity) item).db$getModernItems().defaultReturnValue();
    }

    @Override
    public ResourceLocation getIdForObject(ItemIdentity identity) {
        return identity.identityId;
    }

    @Override
    public @NotNull Iterator<Pair<ResourceLocation, ItemIdentity>> iterator() {
        @SuppressWarnings("unchecked")
        var spliter = (Spliterator<Item>) Item.itemRegistry.spliterator();

        return StreamSupport.stream(spliter, false)
            .flatMap(item -> ItemIdentityRegistry.INSTANCE.getIdentitiesForItem(item).stream())
            .map(i -> Pair.of(i.identityId, i))
            .iterator();
    }
}
