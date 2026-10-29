package databack.common.worldgen.feature.world;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.block.material.Material;
import net.minecraft.util.ResourceLocation;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import databack.common.annotation.ThreadSafe;
import databack.common.interop.modern_block.BlockIdentity;
import databack.common.interop.registry.BlockIdentityRegistry;
import databack.common.tags.BuiltinTagRegistries;
import databack.common.tags.ITag;
import databack.common.tags.TagRegistry;
import databack.common.worldgen.feature.standin.FtBlockFacts;
import databack.common.worldgen.feature.standin.FtBlockState;

/// Block facts from 1.7: tags from the block tag registry, behaviour from each state's 1.7 block's material.
@ThreadSafe
public final class LegacyBlockFacts implements FtBlockFacts {

    private static final Logger LOGGER = LogManager.getLogger("databack|features");

    @NotNull
    private final LegacyStates states;
    private final Set<String> warned = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public LegacyBlockFacts(@NotNull LegacyStates states) {
        this.states = states;
    }

    /// Unknown tags and blocks are in no tag, nor is anything before the tags are loaded; unknown tags are logged
    /// once.
    @Override
    public boolean isInTag(@NotNull String blockId, @NotNull String tagId) {
        TagRegistry<BlockIdentity> tags = BuiltinTagRegistries.blocks();
        ITag<BlockIdentity> tag = tags == null ? null : tags.getTag(new ResourceLocation(tagId));
        if (tag == null) {
            if (warned.add(tagId)) LOGGER.warn("Unknown block tag #{} in a feature; nothing is in it", tagId);
            return false;
        }

        BlockIdentity identity = BlockIdentityRegistry.INSTANCE.getObject(new ResourceLocation(blockId));
        return identity != null && tag.includes(identity);
    }

    @Override
    public boolean isReplaceable(@NotNull FtBlockState state) {
        return material(state).isReplaceable();
    }

    @Override
    public boolean isSolid(@NotNull FtBlockState state) {
        return material(state).blocksMovement();
    }

    private Material material(FtBlockState state) {
        return states.toLegacy(state).getBlock().getMaterial();
    }
}
