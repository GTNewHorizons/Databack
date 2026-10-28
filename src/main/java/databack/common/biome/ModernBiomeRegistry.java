package databack.common.biome;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;

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
///
/// The server decides which biomes exist and their numbers ([#activate]); a client connected to a remote server
/// mirrors that exactly ([#synchronize]).
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
    @NotNull
    private final Consumer<ModernBiome> configure;

    private final Map<String, ModernBiome> interned = new HashMap<>();
    private final Set<String> active = new HashSet<>();
    /// 1.7 biomes moved aside for an active [ModernBiome] of the same id, by that id.
    private final Map<String, BiomeGenBase> displaced = new HashMap<>();

    /// `biomeList` is the global biome list; ids at or above `idLimit` are never assigned. `configure` is applied to
    /// every active biome after each [#activate] or [#synchronize], e.g. to set its properties from the datapacks.
    public ModernBiomeRegistry(
        @NotNull ModernBiomeIds ids, @NotNull ProxyBiomeRegistry proxy, BiomeGenBase @NotNull [] biomeList,
        int idLimit, boolean modernOnly, @NotNull Consumer<ModernBiome> configure
    ) {
        this.ids = ids;
        this.proxy = proxy;
        this.biomeList = biomeList;
        this.idLimit = Math.min(idLimit, biomeList.length);
        this.modernOnly = modernOnly;
        this.configure = configure;
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
        int known = interned.size();
        for (String id : sorted) {
            if (!active.contains(id)) activate(id);
        }

        configureActive();
        LOGGER.info("{} of {} datapack biomes use modern biomes ({} new this session); the rest use 1.7 biomes",
            active.size(), datapackBiomes.size(), interned.size() - known);
    }

    /// Makes exactly the biomes in `served` (datapack id to numeric id, as a server's [#activeIds]) active, with those
    /// numbers. Never assigns or saves ids. A biome whose number is taken by another biome stays unavailable (logged).
    public void synchronize(@NotNull Map<String, Integer> served) {
        for (String id : new ArrayList<>(active)) {
            Integer number = served.get(id);
            if (number == null || number != interned.get(id).biomeID) deactivate(interned.get(id));
        }

        for (Map.Entry<String, Integer> e : new TreeMap<>(served).entrySet()) {
            if (active.contains(e.getKey())) continue;

            ModernBiome biome = interned.get(e.getKey());
            int number = e.getValue();

            if (biome == null || biome.biomeID != number) {
                if (number < 0 || number >= biomeList.length || biomeList[number] != null) {
                    LOGGER.warn("Server biome id {} of {} is unusable here; {} is unavailable", number, e.getKey(),
                        e.getKey());
                    continue;
                }
                biome = new ModernBiome(number, e.getKey());
                interned.put(e.getKey(), biome);
            } else if (!claimSlot(biome)) {
                continue;
            }

            register(biome);
        }

        configureActive();
        LOGGER.info("Using the server's {} modern biomes ({} of them available here)", served.size(), active.size());
    }

    /// The active biomes' numeric ids, by datapack id.
    @NotNull
    public Map<String, Integer> activeIds() {
        Map<String, Integer> result = new TreeMap<>();
        for (String id : active) result.put(id, interned.get(id).biomeID);
        return result;
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
        } else if (!claimSlot(biome)) {
            return;
        }

        register(biome);
    }

    /// Puts an interned biome back into its slot of the global list. False (logged) when another biome holds it.
    private boolean claimSlot(ModernBiome biome) {
        BiomeGenBase holder = biomeList[biome.biomeID];
        if (holder != null && holder != biome) {
            LOGGER.warn("Biome id {} of {} is now used by {}; {} is unavailable", biome.biomeID,
                biome.getDatapackId(), holder.biomeName, biome.getDatapackId());
            return false;
        }

        biomeList[biome.biomeID] = biome;
        return true;
    }

    /// Registers a biome that holds its slot under its datapack id, moving a same-named 1.7 biome aside.
    private void register(ModernBiome biome) {
        String id = biome.getDatapackId();
        ResourceLocation key = new ResourceLocation(id);

        BiomeGenBase existing = proxy.getObject(key);
        if (existing != null && !(existing instanceof ModernBiome)) {
            displaced.put(id, existing);
            proxy.putBiomeReplacing(legacyId(key), existing);
        }

        proxy.putBiomeReplacing(key, biome);
        active.add(id);
    }

    private void configureActive() {
        for (String id : active) configure.accept(interned.get(id));
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
