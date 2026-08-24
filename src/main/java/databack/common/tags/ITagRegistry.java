package databack.common.tags;

import java.util.List;

import net.minecraft.util.ResourceLocation;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import databack.Databack;

/// A tag registrar is a container for tag relationships. A tag is identified by a domain/namespace and a path via a
/// [ResourceLocation]. A tag can contain other tags, or a non-tag target. Targets include things like blocks, items,
/// biomes, entities, but they can be anything. The default implementation does not include modern minecraft's block
/// tags since those come from data packs.
/// <p>
/// Tags are registered from within an event (see the {@link TagRegistry#postEvent()} overrides below) because they have
/// a strict lifecycle. Each gather generation produces a new set of [ITag] objects, though tags are interned within the
/// generation.
public interface ITagRegistry<Target> {

    Logger LOGGER = LogManager.getLogger(Databack.MODID + "|tags");

    @Nullable
    ITag<Target> getTag(ResourceLocation loc);
    @NotNull
    ITag<Target> getOrCreateTag(ResourceLocation loc);

    List<ITag<Target>> getTags(Target target);

    void addToTag(ITag<Target> tag, Target target);
    void addToTag(ITag<Target> parent, ITag<Target> child);

    void removeFromTag(ITag<Target> tag, Target target);
    void removeFromTag(ITag<Target> parent, ITag<Target> child);

}
