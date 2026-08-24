package databack.common.interop.registry;

import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;

import org.jetbrains.annotations.NotNull;

import com.google.common.collect.Iterators;
import databack.mixins.early.AccessorRegistrySimple;
import it.unimi.dsi.fastutil.Pair;

public class ProxyItemRegistry implements IProxyRegistry<Item> {

    public static final ProxyItemRegistry INSTANCE = new ProxyItemRegistry();

    private ProxyItemRegistry () { }

    @Override
    public Item getObject(ResourceLocation id) {
        return (Item) Item.itemRegistry.getObject(id.toString());
    }

    @Override
    public ResourceLocation getIdForObject(Item object) {
        String id = Item.itemRegistry.getNameForObject(object);

        return id == null ? null : new ResourceLocation(id);
    }

    @Override
    public @NotNull Iterator<Pair<ResourceLocation, Item>> iterator() {
        Map<String, Item> registryObjects = ((AccessorRegistrySimple) Item.itemRegistry).db$getRegistryObjects();

        Iterator<Entry<String, Item>> iter = registryObjects.entrySet().iterator();

        return Iterators.transform(iter, e -> Pair.of(new ResourceLocation(e.getKey()), e.getValue()));
    }
}
