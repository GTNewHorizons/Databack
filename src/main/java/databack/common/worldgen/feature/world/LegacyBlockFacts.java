package databack.common.worldgen.feature.world;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.block.material.Material;
import net.minecraft.util.ResourceLocation;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.gtnewhorizon.gtnhlib.util.data.ImmutableBlockMeta;

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
    /// See [#isInTag].
    @Nullable
    private volatile TagAnswers answers;

    public LegacyBlockFacts(@NotNull LegacyStates states) {
        this.states = states;
    }

    /// Unknown tags and blocks are in no tag, nor is anything before the tags are loaded; unknown tags are logged
    /// once. Answers are kept until the tags are reloaded.
    @Override
    public boolean isInTag(@NotNull String blockId, @NotNull String tagId) {
        TagRegistry<BlockIdentity> tags = BuiltinTagRegistries.blocks();
        if (tags == null) return false;

        TagAnswers answers = this.answers;
        if (answers == null || answers.tags != tags) this.answers = answers = new TagAnswers(tags);

        return answers.byTag.computeIfAbsent(tagId, k -> new ConcurrentHashMap<>())
            .computeIfAbsent(blockId, k -> lookUp(tags, blockId, tagId));
    }

    private boolean lookUp(TagRegistry<BlockIdentity> tags, String blockId, String tagId) {
        ITag<BlockIdentity> tag = tags.getTag(new ResourceLocation(tagId));
        if (tag == null) {
            if (warned.add(tagId)) LOGGER.warn("Unknown block tag #{} in a feature; nothing is in it", tagId);
            return false;
        }

        BlockIdentity identity = BlockIdentityRegistry.INSTANCE.getObject(new ResourceLocation(blockId));
        return identity != null && tag.includes(identity);
    }

    /// Tag membership by tag, then block, for the tags it was read from: a reload makes new tags.
    private static final class TagAnswers {

        @NotNull
        final TagRegistry<BlockIdentity> tags;
        final ConcurrentHashMap<String, ConcurrentHashMap<String, Boolean>> byTag = new ConcurrentHashMap<>();

        TagAnswers(@NotNull TagRegistry<BlockIdentity> tags) {
            this.tags = tags;
        }
    }

    @Override
    public boolean isReplaceable(@NotNull FtBlockState state) {
        return material(state).isReplaceable();
    }

    @Override
    public boolean isSolid(@NotNull FtBlockState state) {
        return material(state).blocksMovement();
    }

    /// Blocks without a 1.7 counterpart are not.
    @Override
    public boolean isSolidRender(@NotNull FtBlockState state) {
        ImmutableBlockMeta block = states.toLegacy(state);
        return block != null && block.getBlock().isOpaqueCube();
    }

    /// Air's for blocks without a 1.7 counterpart.
    private Material material(FtBlockState state) {
        ImmutableBlockMeta block = states.toLegacy(state);
        return block == null ? Material.air : block.getBlock().getMaterial();
    }
}
