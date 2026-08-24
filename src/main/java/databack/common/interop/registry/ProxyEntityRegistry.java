package databack.common.interop.registry;

import java.util.Iterator;

import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;

import org.jetbrains.annotations.NotNull;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.Iterators;
import it.unimi.dsi.fastutil.Pair;

public class ProxyEntityRegistry implements IProxyRegistry<Class<? extends Entity>> {

    public static final ProxyEntityRegistry INSTANCE = new ProxyEntityRegistry();

    private final BiMap<ResourceLocation, Class<? extends Entity>> entities = HashBiMap.create();

    private ProxyEntityRegistry() { }

    public void putEntity(ResourceLocation id, Class<? extends Entity> entity) {
        LOGGER.info("Detected entity registration: id={} entity={}", id, entity);
        entities.put(id, entity);
    }

    @Override
    public Class<? extends Entity> getObject(ResourceLocation id) {
        return entities.get(id);
    }

    @Override
    public ResourceLocation getIdForObject(Class<? extends Entity> entity) {
        return entities.inverse().get(entity);
    }

    @Override
    public @NotNull Iterator<Pair<ResourceLocation, Class<? extends Entity>>> iterator() {
        return Iterators.transform(entities.entrySet().iterator(), e -> Pair.of(e.getKey(), e.getValue()));
    }
}
