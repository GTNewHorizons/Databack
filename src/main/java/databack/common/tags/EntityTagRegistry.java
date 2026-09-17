package databack.common.tags;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.google.common.collect.Iterators;
import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.SetMultimap;
import databack.Databack;
import databack.DatabackConfig;
import databack.common.dto.tag.TagEntry;
import databack.common.dto.tag.TagFile;
import databack.common.interop.registry.ProxyEntityRegistry;
import databack.common.loader.ResourceId;
import databack.common.network.PacketEncoderSyncTagHandler;
import databack.common.tags.TagEvent.RegisterEntityTagsEvent;
import it.unimi.dsi.fastutil.Pair;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterators;

@SuppressWarnings("UnstableApiUsage")
public class EntityTagRegistry implements ITagRegistry<Class<? extends Entity>>, ITagHandler, ITagStagingReceiver {

    static final Map<Class<? extends Entity>, BitSet> ENTITY_BITS = new HashMap<>();

    public final Logger logger = LogManager.getLogger(Databack.MODID + "|tags|entities");

    protected final String resourceType = "tags/entity_type";

    static @NotNull BitSet getEntityBits(Class<? extends Entity> target) {
        return ENTITY_BITS.computeIfAbsent(target, $ -> new BitSet());
    }

    protected final HashMap<ResourceLocation, EntityTagImpl> tags = new HashMap<>();
    protected final Int2ObjectOpenHashMap<EntityTagImpl> byBit = new Int2ObjectOpenHashMap<>();

    protected final Multimap<EntityTagImpl, EntityTagImpl> tagHierarchy = MultimapBuilder.hashKeys().arrayListValues().build();
    protected final Multimap<EntityTagImpl, Class<? extends Entity>> tagContent = MultimapBuilder.hashKeys().arrayListValues().build();

    protected final SetMultimap<ResourceLocation, TagEntry> tagStaging = MultimapBuilder.hashKeys().hashSetValues().build();

    protected final AtomicInteger nextBit = new AtomicInteger(0);

    protected List<Class<? extends Entity>> domain;
    protected int currentGeneration;

    public EntityTagRegistry() {}

    protected List<Class<? extends Entity>> getDomain() {
        return ObjectIterators.pour(Iterators.transform(ProxyEntityRegistry.INSTANCE.iterator(), Pair::right));
    }

    protected void postEvent() {
        MinecraftForge.EVENT_BUS.post(new RegisterEntityTagsEvent(this));
    }

    protected ResourceLocation getIdForTarget(Class<? extends Entity> entity) {
        return ProxyEntityRegistry.INSTANCE.getIdForObject(entity);
    }

    protected Class<? extends Entity> getTarget(ResourceLocation id) {
        return ProxyEntityRegistry.INSTANCE.getObject(id);
    }

    @Override
    public @Nullable IEntityTag getTag(ResourceLocation loc) {
        return tags.get(loc);
    }

    @Override
    public @NotNull IEntityTag getOrCreateTag(ResourceLocation loc) {
        return tags.computeIfAbsent(loc, k -> {
            EntityTagImpl tag = new EntityTagImpl(k, nextBit.getAndIncrement());
            byBit.put(tag.getBit(), tag);
            return tag;
        });
    }

    @Override
    public List<ITag<Class<? extends Entity>>> getTags(Class<? extends Entity> entityClass) {
        var bits = getEntityBits(entityClass);

        List<ITag<Class<? extends Entity>>> tags = new ArrayList<>(bits.cardinality());

        for (int bit = bits.nextSetBit(0); bit != -1; bit = bits.nextSetBit(bit + 1)) {
            tags.add(byBit.get(bit));
        }

        return tags;
    }

    private @NotNull EntityTagImpl validateTag(ITag<Class<? extends Entity>> tag) {
        EntityTagImpl casted = (EntityTagImpl) tag;

        if (casted.getGeneration() != currentGeneration) {
            throw new IllegalArgumentException("Tags must always be newly registered: something tried to use an "
                + "old tag (from generation " + casted.getGeneration() + ") during a later tag-registration"
                + "(generation " + currentGeneration + "): " + tag);
        }

        return casted;
    }

    @Override
    public void addToTag(ITag<Class<? extends Entity>> tag, Class<? extends Entity> entityClass) {
        this.tagContent.put(validateTag(tag), entityClass);
    }

    @Override
    public void addToTag(ITag<Class<? extends Entity>> parent, ITag<Class<? extends Entity>> child) {
        this.tagHierarchy.put(validateTag(parent), validateTag(child));
    }

    @Override
    public void removeFromTag(ITag<Class<? extends Entity>> tag, Class<? extends Entity> target) {
        this.tagContent.remove(validateTag(tag), target);
    }

    @Override
    public void removeFromTag(ITag<Class<? extends Entity>> parent, ITag<Class<? extends Entity>> child) {
        this.tagHierarchy.remove(validateTag(parent), validateTag(child));
    }

    private void dfsCollect(EntityTagImpl node,
        Set<EntityTagImpl> visiting,
        Set<EntityTagImpl> visited,
        Set<Class<? extends Entity>> allContents,
        Set<EntityTagImpl> allChildTags) {
        visiting.add(node);
        allContents.addAll(tagContent.get(node));

        for (EntityTagImpl child : tagHierarchy.get(node)) {
            // Bug-2 fix: cycle detection works correctly even through shared/diamond nodes
            // because we use visiting (gray) set rather than a per-path stack. A gray node
            // encountered here is always a true ancestor in the current DFS path.
            if (visiting.contains(child)) {
                throw new IllegalStateException(
                    "Cyclic dependency detected in tag hierarchy: " + child
                        + " is an ancestor of itself via " + node);
            }
            // Bug-3 fix: always set immediate-parent bit before the visited check so that
            // diamond dependencies (multiple parents for the same child) all register their bits.
            child.db$getTagBitSet().set(node.getBit());
            allChildTags.add(child);
            if (!visited.contains(child)) {
                dfsCollect(child, visiting, visited, allContents, allChildTags);
            }
        }

        visiting.remove(node);
        visited.add(node);
    }

    private void finishTag(EntityTagImpl rootTag) {
        Set<Class<? extends Entity>> allContents = new HashSet<>();
        Set<EntityTagImpl> allChildTags = new HashSet<>();

        dfsCollect(rootTag, new HashSet<>(), new HashSet<>(), allContents, allChildTags);

        // Bug-1 fix: set rootTag's bit on ALL transitively reachable child tags, not just
        // direct children. Previously only the immediate parent's bit was set, so
        // ROOT.includes(grandchild) always returned false.
        for (EntityTagImpl child : allChildTags) {
            child.db$getTagBitSet().set(rootTag.getBit());
        }
        for (Class<? extends Entity> content : allContents) {
            getEntityBits(content).set(rootTag.getBit());
        }

        //noinspection unchecked
        rootTag.finish(allChildTags.toArray(new EntityTagImpl[0]), allContents.toArray(new Class[0]));
    }

    @Internal
    public void gatherTags() {
        logger.info("Reloading entity tags");

        // First clear: remove stale bits from the PREVIOUS domain. Needed because the domain
        // can change between calls, and those objects would otherwise retain bits from the last generation
        // indefinitely.
        if (domain != null) {
            for (var obj : domain) {
                getEntityBits(obj).clear();
            }
        }

        this.tags.clear();
        this.tagHierarchy.clear();
        this.tagContent.clear();

        domain = getDomain();

        this.currentGeneration = TagImpl.CURRENT_GENERATION.incrementAndGet();
        nextBit.set(0);

        for (var staging : tagStaging.entries()) {
            var tag = getOrCreateTag(staging.getKey());

            var e = staging.getValue();

            if (e.isTagRef) {
                if (e.required) {
                    IEntityTag child = getOrCreateTag(new ResourceLocation(e.id));
                    addToTag(tag, child);
                } else {
                    IEntityTag child = getTag(new ResourceLocation(e.id));

                    if (child != null) {
                        addToTag(tag, child);
                    }
                }
            } else {
                Class<? extends Entity> target = getTarget(new ResourceLocation(e.id));

                if (e.required && target == null) {
                    logger.error("Tag {} references missing entity {}: this tag entry will be skipped", staging.getKey(), e.id);
                    continue;
                }

                addToTag(tag, target);
            }
        }

        postEvent();

        // Second clear: reset all NEW domain objects after event posting, before finishTag
        // runs. Ensures finishTag starts from a blank slate even for new objects or objects
        // that were mutated during the event.
        for (var obj : domain) {
            getEntityBits(obj).clear();
        }

        for (var tag : tags.values()) {
            finishTag(tag);
        }

        logger.info("There are {} entity tags loaded", tags.size());

        if (DatabackConfig.enableTagDebugMode && !tags.isEmpty()) {
            logger.info("Dump of all entity tags:");

            for (var tag : tags.values()) {
                var children = tagHierarchy.get(tag).stream().map(EntityTagImpl::toString).collect(Collectors.joining(", "));
                var targets = tagContent.get(tag).stream().map(t -> this.getIdForTarget(t).toString()).collect(Collectors.joining(", "));

                logger.info("[{}]: Children=[{}] Targets=[{}]", tag, children, targets);
            }
        }
    }

    @Override
    public void handle(@NotNull ResourceId id, byte @NotNull [] content) {
        TagFile file = TagFile.GSON.fromJson(new String(content, StandardCharsets.UTF_8), TagFile.class);

        ResourceLocation loc = new ResourceLocation(id.namespace(), id.id());

        if (file.replace) {
            tagStaging.removeAll(loc);
        }

        tagStaging.putAll(loc, file.values);
    }

    @Override
    public void onLoadStart() {
        tagStaging.clear();
    }

    @Override
    public void syncToPlayer(EntityPlayerMP player) {
        PacketEncoderSyncTagHandler.createPacket(resourceType, tagStaging).sendToPlayer(player);
    }

    @Override
    public void receiveTagStaging(SetMultimap<ResourceLocation, TagEntry> staging) {
        tagStaging.clear();
        tagStaging.putAll(staging);
        gatherTags();
    }

    @Override
    public boolean doesPathClaiming() {
        return false;
    }
}
