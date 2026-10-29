package databack.common.interop.registry;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import net.minecraft.util.ResourceLocation;

import org.jetbrains.annotations.NotNull;

import databack.common.interop.modern_block.BlockIdentity;
import databack.common.loader.VanillaDatapackDownloader;

/// Checks the block identities against the 26.1 block list, to catch typos and 1.7 names that were never mapped.
public final class BlockIdentityValidator {

    private BlockIdentityValidator() {}

    /// The outcome of a check.
    public static final class Result {

        /// `minecraft:` identities that aren't 26.1 blocks
        @NotNull
        public final List<BlockIdentity> unknown;
        /// 26.1 blocks that no 1.7 block reports
        @NotNull
        public final Set<String> unmapped;

        Result(@NotNull List<BlockIdentity> unknown, @NotNull Set<String> unmapped) {
            this.unknown = unknown;
            this.unmapped = unmapped;
        }
    }

    @NotNull
    public static Result check(@NotNull Collection<BlockIdentity> identities, @NotNull Set<String> modernBlocks) {
        List<BlockIdentity> unknown = new ArrayList<>();
        Set<String> reported = new HashSet<>();

        for (BlockIdentity identity : identities) {
            ResourceLocation id = identity.identityId;

            if (!"minecraft".equals(id.getResourceDomain())) continue;

            String name = id.toString();

            if (!modernBlocks.contains(name)) {
                unknown.add(identity);
            }

            reported.add(name);
        }

        Set<String> unmapped = new TreeSet<>(modernBlocks);
        unmapped.removeAll(reported);

        return new Result(unknown, unmapped);
    }

    /// Checks every registered block against the block list cached with the vanilla datapack, logging problems.
    public static void validate() {
        File datapackDir = VanillaDatapackDownloader.getCachedDir();

        if (datapackDir == null) {
            BlockIdentityRegistry.LOGGER.warn("Vanilla datapack isn't available, skipping block identity validation");
            return;
        }

        Set<String> modernBlocks;

        try {
            modernBlocks = new HashSet<>(Files.readAllLines(
                VanillaDatapackDownloader.getBlockListFile(datapackDir).toPath(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            BlockIdentityRegistry.LOGGER.warn("Could not read the 26.1 block list, skipping block identity validation", e);
            return;
        }

        Result result = check(BlockIdentityRegistry.INSTANCE.domain(), modernBlocks);

        for (BlockIdentity unknown : result.unknown) {
            BlockIdentityRegistry.LOGGER.warn(
                "Block identity {} (from {}) is not a 26.1 block",
                unknown.identityId, ProxyBlockRegistry.INSTANCE.getIdForObject(unknown.block));
        }

        BlockIdentityRegistry.LOGGER.info(
            "{} of {} 26.1 blocks have no 1.7 block: {}",
            result.unmapped.size(), modernBlocks.size(), result.unmapped);
    }
}
