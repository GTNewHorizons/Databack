package databack.common.biome;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;

import net.minecraft.util.ResourceLocation;
import net.minecraft.world.biome.BiomeGenBase;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import databack.common.annotation.NotThreadSafe;
import databack.common.interop.registry.ProxyBiomeRegistry;

/// Makes a [ModernBiome] available for each datapack biome that has no 1.7 biome.
///
/// [ModernBiome]s are interned: each datapack id gets one instance, with one numeric id ([ModernBiomeIds]) for good,
/// since 1.7 never expects a biome's id to change. A biome is in the global biome list and [ProxyBiomeRegistry] only
/// while the loaded datapacks define it ([#activate]); otherwise its slot is empty.
///
/// In modern-only mode every datapack biome gets a [ModernBiome], and a 1.7 biome registered under the same id (e.g.
/// `minecraft:plains`) is moved to `<namespace>:legacy/<path>` while the [ModernBiome] is active.
@NotThreadSafe
public final class ModernBiomeRegistry {

    private static final Logger LOGGER = LogManager.getLogger("databack-biomes");

    @NotNull
    private final ModernBiomeIds ids;
    @NotNull
    private final ProxyBiomeRegistry proxy;
    private final BiomeGenBase @NotNull [] biomeList;
    private final int idLimit;
    private final boolean modernOnly;

    private final Map<String, ModernBiome> interned = new HashMap<>();
    private final Set<String> active = new HashSet<>();
    /// 1.7 biomes moved aside for an active [ModernBiome] of the same id, by that id.
    private final Map<String, BiomeGenBase> displaced = new HashMap<>();

    /// `biomeList` is the global biome list; ids at or above `idLimit` are never assigned.
    public ModernBiomeRegistry(
        @NotNull ModernBiomeIds ids, @NotNull ProxyBiomeRegistry proxy, BiomeGenBase @NotNull [] biomeList,
        int idLimit, boolean modernOnly
    ) {
        this.ids = ids;
        this.proxy = proxy;
        this.biomeList = biomeList;
        this.idLimit = Math.min(idLimit, biomeList.length);
        this.modernOnly = modernOnly;
    }

    /// Makes exactly the [ModernBiome]s the datapack biome ids `datapackBiomes` need active. Biomes without a free
    /// numeric id stay unavailable (logged).
    public void activate(@NotNull Collection<String> datapackBiomes) {
        Set<String> needed = new HashSet<>();
        for (String id : datapackBiomes) {
            if (needsModernBiome(id)) needed.add(id);
        }

        for (String id : new ArrayList<>(active)) {
            if (!needed.contains(id)) deactivate(interned.get(id));
        }

        List<String> sorted = new ArrayList<>(needed);
        sorted.sort(null);
        for (String id : sorted) {
            if (!active.contains(id)) activate(id);
        }
    }

    /// The active [ModernBiome] for a datapack id, or null.
    @Nullable
    public ModernBiome get(@NotNull String datapackId) {
        return active.contains(datapackId) ? interned.get(datapackId) : null;
    }

    private boolean needsModernBiome(String id) {
        if (modernOnly) return true;

        BiomeGenBase existing = proxy.getObject(new ResourceLocation(id));
        return existing == null || existing instanceof ModernBiome;
    }

    private void activate(String id) {
        ModernBiome biome = interned.get(id);

        if (biome == null) {
            biome = create(id);
            if (biome == null) return;
            interned.put(id, biome);
        } else if (biomeList[biome.biomeID] != null && biomeList[biome.biomeID] != biome) {
            LOGGER.warn("Biome id {} of {} is now used by {}; {} is unavailable", biome.biomeID, id,
                biomeList[biome.biomeID].biomeName, id);
            return;
        } else {
            biomeList[biome.biomeID] = biome;
        }

        ResourceLocation key = new ResourceLocation(id);
        BiomeGenBase existing = proxy.getObject(key);
        if (existing != null && !(existing instanceof ModernBiome)) {
            displaced.put(id, existing);
            proxy.putBiomeReplacing(legacyId(key), existing);
        }

        proxy.putBiomeReplacing(key, biome);
        active.add(id);
    }

    @Nullable
    private ModernBiome create(String id) {
        Integer saved = ids.get(id);
        if (saved != null && saved < idLimit && biomeList[saved] == null) return new ModernBiome(saved, id);

        if (saved != null) {
            LOGGER.warn("Biome id {} of {} is now used by another biome; giving it a new id. Chunks generated with "
                + "the old id will show the other biome", saved, id);
        }

        OptionalInt assigned = ids.assign(id, idLimit, numeric -> biomeList[numeric] == null);
        if (!assigned.isPresent()) {
            LOGGER.error("No free biome id for {}; it will be replaced by a fallback biome. Installing EndlessIDs "
                + "raises the biome id limit", id);
            return null;
        }

        return new ModernBiome(assigned.getAsInt(), id);
    }

    private void deactivate(ModernBiome biome) {
        String id = biome.getDatapackId();
        ResourceLocation key = new ResourceLocation(id);

        if (biomeList[biome.biomeID] == biome) biomeList[biome.biomeID] = null;
        proxy.removeBiome(key);

        BiomeGenBase original = displaced.remove(id);
        if (original != null) proxy.putBiomeReplacing(key, original);

        active.remove(id);
    }

    private static ResourceLocation legacyId(ResourceLocation id) {
        return new ResourceLocation(id.getResourceDomain(), "legacy/" + id.getResourcePath());
    }
}
