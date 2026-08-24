package databack.common.interop.registry;

import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import net.minecraft.block.Block;
import net.minecraft.util.ResourceLocation;

import org.jetbrains.annotations.NotNull;

import com.google.common.collect.Iterators;
import databack.mixins.early.AccessorRegistrySimple;
import it.unimi.dsi.fastutil.Pair;

public class ProxyBlockRegistry implements IProxyRegistry<Block> {

    public static final ProxyBlockRegistry INSTANCE = new ProxyBlockRegistry();

    private ProxyBlockRegistry () { }

    @Override
    public Block getObject(ResourceLocation id) {
        return (Block) Block.blockRegistry.getObject(id.toString());
    }

    @Override
    public ResourceLocation getIdForObject(Block object) {
        String id = Block.blockRegistry.getNameForObject(object);

        return id == null ? null : new ResourceLocation(id);
    }

    @Override
    public @NotNull Iterator<Pair<ResourceLocation, Block>> iterator() {
        Map<String, Block> registryObjects = ((AccessorRegistrySimple) Block.blockRegistry).db$getRegistryObjects();

        Iterator<Entry<String, Block>> iter = registryObjects.entrySet().iterator();

        return Iterators.transform(iter, e -> Pair.of(new ResourceLocation(e.getKey()), e.getValue()));
    }
}
