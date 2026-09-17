package databack.common.tags;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ResourceLocation;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.SetMultimap;
import databack.Databack;
import databack.DatabackConfig;
import databack.common.dto.tag.TagEntry;
import databack.common.dto.tag.TagFile;
import databack.common.loader.ResourceId;
import databack.common.network.PacketEncoderSyncTagHandler;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

@SuppressWarnings("UnstableApiUsage")
public abstract class TagRegistry<Target> implements ITagRegistry<Target>, ITagHandler, ITagStagingReceiver {

    public final Logger logger;

    protected final String targetName;
    protected final String resourceType;
    protected final Class<Target> clazz;
    protected final Target[] zeroSized;

    protected final HashMap<ResourceLocation, TagImpl<Target>> tags = new HashMap<>();
    protected final Int2ObjectOpenHashMap<TagImpl<Target>> byBit = new Int2ObjectOpenHashMap<>();

    protected final SetMultimap<TagImpl<Target>, TagImpl<Target>> tagHierarchy = MultimapBuilder.hashKeys().hashSetValues().build();
    protected final SetMultimap<TagImpl<Target>, Target> tagContent = MultimapBuilder.hashKeys().hashSetValues().build();

    protected final SetMultimap<ResourceLocation, TagEntry> tagStaging = MultimapBuilder.hashKeys().hashSetValues().build();

    protected final AtomicInteger nextBit = new AtomicInteger(0);

    protected List<Target> domain;
    protected int currentGeneration;

    public TagRegistry(String targetName, String resourceType, Class<Target> clazz, Target[] zeroSized) {
        this.targetName = targetName;
        this.resourceType = resourceType;
        this.clazz = clazz;
        this.zeroSized = zeroSized;

        if (!Taggable.class.isAssignableFrom(clazz)) {
            throw new IllegalStateException("Target classes must either implement Taggable, or have it mixined onto them.");
        }

        logger = LogManager.getLogger(Databack.MODID + "|tags|" + targetName);
    }

    protected abstract List<Target> getDomain();

    protected abstract ResourceLocation getIdForTarget(Target target);
    protected abstract Target getTarget(ResourceLocation id);

    protected abstract void postEvent();

    @Override
    public @Nullable ITag<Target> getTag(ResourceLocation loc) {
        return tags.get(loc);
    }

    @Override
    public @NotNull ITag<Target> getOrCreateTag(ResourceLocation loc) {
        return tags.computeIfAbsent(loc, k -> {
            TagImpl<Target> tag = new TagImpl<>(k, nextBit.getAndIncrement());
            byBit.put(tag.getBit(), tag);
            return tag;
        });
    }

    @Override
    public List<ITag<Target>> getTags(Target target) {
        if (!clazz.isAssignableFrom(target.getClass())) {
            throw new IllegalArgumentException("Invalid target: " + target + ", expected " + clazz);
        }

        var bits = ((Taggable) target).db$getTagBitSet();

        List<ITag<Target>> tags = new ArrayList<>(bits.cardinality());

        for (int bit = bits.nextSetBit(0); bit != -1; bit = bits.nextSetBit(bit + 1)) {
            tags.add(byBit.get(bit));
        }

        return tags;
    }

    private @NotNull TagImpl<Target> validateTag(ITag<Target> tag) {
        TagImpl<Target> casted = (TagImpl<Target>) tag;

        if (casted.getGeneration() != currentGeneration) {
            throw new IllegalArgumentException("Tags must always be newly registered: something tried to use an "
                + "old tag (from generation " + casted.getGeneration() + ") during a later tag-registration"
                + "(generation " + currentGeneration + "): " + tag);
        }

        return casted;
    }

    @Override
    public void addToTag(ITag<Target> tag, Target target) {
        this.tagContent.put(validateTag(tag), target);
    }

    @Override
    public void addToTag(ITag<Target> parent, ITag<Target> child) {
        this.tagHierarchy.put(validateTag(parent), validateTag(child));
    }

    @Override
    public void removeFromTag(ITag<Target> tag, Target target) {
        this.tagContent.remove(validateTag(tag), target);
    }

    @Override
    public void removeFromTag(ITag<Target> parent, ITag<Target> child) {
        this.tagHierarchy.remove(validateTag(parent), validateTag(child));
    }

    private void dfsCollect(TagImpl<Target> node,
                             Set<TagImpl<Target>> visiting,
                             Set<TagImpl<Target>> visited,
                             Set<Target> allContents,
                             Set<TagImpl<Target>> allChildTags) {
        visiting.add(node);
        allContents.addAll(tagContent.get(node));

        for (TagImpl<Target> child : tagHierarchy.get(node)) {
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

    private void finishTag(TagImpl<Target> rootTag) {
        Set<Target> allContents = new HashSet<>();
        Set<TagImpl<Target>> allChildTags = new HashSet<>();

        dfsCollect(rootTag, new HashSet<>(), new HashSet<>(), allContents, allChildTags);

        // Bug-1 fix: set rootTag's bit on ALL transitively reachable child tags, not just
        // direct children. Previously only the immediate parent's bit was set, so
        // ROOT.includes(grandchild) always returned false.
        for (TagImpl<Target> child : allChildTags) {
            child.db$getTagBitSet().set(rootTag.getBit());
        }
        for (Target content : allContents) {
            ((Taggable) content).db$getTagBitSet().set(rootTag.getBit());
        }

        //noinspection unchecked
        rootTag.finish(allChildTags.toArray(new TagImpl[0]), allContents.toArray(zeroSized));
    }

    @Internal
    public void gatherTags() {
        logger.info("Reloading {} tags", targetName);

        // First clear: remove stale bits from the PREVIOUS domain. Needed because the domain
        // can shrink between calls (e.g. a block deregistered), and those objects would
        // otherwise retain bits from the last generation indefinitely.
        if (domain != null) {
            for (var obj : domain) {
                ((Taggable) obj).db$getTagBitSet().clear();
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
                    ITag<Target> child = getOrCreateTag(new ResourceLocation(e.id));
                    addToTag(tag, child);
                } else {
                    ITag<Target> child = getTag(new ResourceLocation(e.id));

                    if (child != null) {
                        addToTag(tag, child);
                    }
                }
            } else {
                Target target = getTarget(new ResourceLocation(e.id));

                if (e.required && target == null) {
                    logger.error("Tag {} references missing {} {}: this tag entry will be skipped", staging.getKey(), targetName, e.id);
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
            ((Taggable) obj).db$getTagBitSet().clear();
        }

        for (var tag : tags.values()) {
            finishTag(tag);
        }

        logger.info("There are {} {} tags loaded", tags.size(), targetName);

        if (DatabackConfig.enableTagDebugMode && !tags.isEmpty()) {
            logger.info("Dump of all {} tags:", targetName);

            for (var tag : tags.values()) {
                var children = tagHierarchy.get(tag).stream().map(TagImpl::toString).collect(Collectors.joining(", "));
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
