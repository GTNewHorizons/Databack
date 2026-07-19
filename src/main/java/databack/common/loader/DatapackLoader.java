package databack.common.loader;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.regex.Pattern;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.MinecraftForge;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cpw.mods.fml.relauncher.Side;
import databack.common.command.StandardDatapackOwners;
import databack.common.dto.tag.TagFile;
import databack.common.handlers.IDatapackTypeHandler;
import databack.common.loader.DatapackEvent.DatapackFinishedLoadingEvent;
import databack.common.loader.DatapackEvent.DatapackGatherEvent;
import databack.common.loader.DatapackEvent.DatapackLoadEvent;
import databack.common.loader.DatapackEvent.DatapackSyncEvent;
import databack.common.loader.io.FolderDatapackSource;
import databack.common.loader.io.IDatapackSource;
import databack.common.loader.io.ZipDatapackSource;
import databack.common.meta.DatapackParseException;
import databack.common.meta.OverlayEntry;
import databack.common.meta.PackMetadata;
import databack.common.meta.PackMetadataParser;
import databack.common.handlers.DatapackHandlerRegistry;
import databack.common.tags.TagRegistry;

/**
 * The single entry point for loading all datapacks in a world.
 *
 * <p>Orchestrates the complete loading pipeline: discovery, metadata parsing, ordering, resource
 * enumeration, and dispatch to type handlers. Stateless between runs; each call to {@link #load}
 * performs a complete, independent loading operation. All file handles are guaranteed to be
 * released before {@link #load} returns, whether normally or by exception.
 */
public final class DatapackLoader {

    public static final Logger LOGGER = LogManager.getLogger("gtnhlib-datapack");

    /**
     * The format version this loader targets. Used exclusively for overlay resolution.
     */
    public static final int LOADER_FORMAT_VERSION = 48;

    // Regex patterns for resource location component validation
    private static final Pattern VALID_COMPONENT = Pattern.compile("[a-z0-9_.\\-]+");

    private DatapackLoader() {}

    public static List<File> discoverCandidates(@Nonnull File worldSaveDir) {
        List<File> candidates = new ArrayList<>();

        File datapacksDir = new File(worldSaveDir, "datapacks");

        if (!datapacksDir.exists() || !datapacksDir.isDirectory()) {
            LOGGER.info("Datapack directory does not exist: {}", datapacksDir);
        } else {
            candidates.addAll(discover(datapacksDir));
        }

        candidates.add(worldSaveDir.toPath().resolve("..").resolve("..").resolve("..").resolve("..").resolve("misc/test-packs/minecraft").toFile());

        MinecraftForge.EVENT_BUS.post(new DatapackGatherEvent(candidates));

        return candidates;
    }

    public static List<Datapack> loadDatapacks(List<File> candidates) {
        List<Datapack> packs = new ArrayList<Datapack>(candidates.size());

        for (File candidate : candidates) {
            Datapack pack = loadCandidate(candidate);
            if (pack != null) {
                packs.add(pack);
            }
        }

        MinecraftForge.EVENT_BUS.post(new DatapackLoadEvent(packs));

        return packs;
    }

    /**
     * Executes the complete datapack loading pipeline for the given world save directory.
     *
     * <p>All {@link IDatapackSource} instances are closed before this method returns, whether
     * normally or by exception.
     *
     * @param worldSaveDir the root of the current world's save directory
     * @param worldInfo    ordering and enable/disable state for the current world
     * @throws DatapackLoadException on any fatal error during the loading pipeline
     */
    public static void load(@Nonnull File worldSaveDir, @Nonnull DatapackWorldInfo worldInfo) {
        LOGGER.info("Loading datapacks");

        List<File> candidates = discoverCandidates(worldSaveDir);

        if (candidates.isEmpty()) {
            LOGGER.info("No datapacks found; skipping datapack loading.");
            return;
        }

        List<Datapack> packs = Collections.emptyList();

        try {
            packs = loadDatapacks(candidates);

            for (Datapack pack : packs) {
                LOGGER.info("Found datapack '{}' at {}", pack.getName(), pack.getSourcePath());
            }

            // Order and disable packs (lowest priority first, highest priority last)
            worldInfo.db$syncPackDeltas(packs);
            List<Datapack> orderedPacks = worldInfo.db$order(packs);

            // Enumerate and dispatch
            Set<String> warnedTypes = new HashSet<String>();
            enumerateAndDispatch(orderedPacks, warnedTypes);
        } finally {
            // Unconditionally close all open sources
            for (Datapack pack : packs) {
                try {
                    pack.getSource().close();
                } catch (IOException e) {
                    LOGGER.error("Failed to close source for datapack '{}': {}", pack.getName(), e.getMessage(), e);
                }
            }
        }

        MinecraftForge.EVENT_BUS.post(new DatapackFinishedLoadingEvent());
    }

    /**
     * Executes the complete datapack loading pipeline for the given world, then syncs to all
     * currently online players.
     *
     * @param worldSaveDir the root of the current world's save directory
     * @param world        the server-side overworld
     * @throws DatapackLoadException on any fatal error during the loading pipeline
     */
    public static void load(@Nonnull File worldSaveDir, World world) {
        load(worldSaveDir, (DatapackWorldInfo) world.getWorldInfo());

        for (var world2 : DimensionManager.getWorlds()) {
            for (var player : world2.playerEntities) {
                if (player instanceof EntityPlayerMP playerMP) {
                    syncToPlayer(playerMP);
                }
            }
        }
    }

    public static void syncToPlayer(EntityPlayerMP player) {
        MinecraftForge.EVENT_BUS.post(new DatapackSyncEvent(player));
        DatapackHandlerRegistry.entrySet(Side.SERVER).forEach(e -> e.getValue().syncToPlayer(player));
    }

    /**
     * Notifies all registered type handlers that the world session has ended.
     *
     * <p>Exceptions thrown by individual handlers are caught, logged at ERROR level, and
     * suppressed — all handlers are notified even if an earlier one throws.
     */
    public static void unload() {
        for (Entry<String, IDatapackTypeHandler> entry : DatapackHandlerRegistry.entrySet(Side.SERVER)) {
            try {
                entry.getValue().onWorldUnload();
            } catch (Exception e) {
                LOGGER.error(
                    "Handler for type '{}' threw an exception during world unload; suppressing.",
                    entry.getKey(),
                    e);
            }
        }
    }

    /**
     * Scans {@code datapacksDir} for ZIP files and immediate subdirectories.
     *
     * @return list of candidate files/directories in filesystem order; empty if dir is empty
     */
    @Nonnull
    private static List<File> discover(@Nonnull File datapacksDir) {
        File[] children = datapacksDir.listFiles();
        if (children == null || children.length == 0) {
            return Collections.emptyList();
        }

        List<File> result = new ArrayList<File>();
        for (File child : children) {
            if (child.isDirectory()) {
                result.add(child);
            } else if (child.isFile() && child.getName().toLowerCase().endsWith(".zip")) {
                result.add(child);
            }
        }
        return result;
    }

    /**
     * Attempts to open and parse a single candidate (ZIP or folder).
     *
     * <p>Returns {@code null} (and closes the source) if {@code pack.mcmeta} is absent.
     * Throws {@link DatapackLoadException} if parsing fails.
     * The returned {@link Datapack} holds an open source; the caller must close it.
     *
     * @param candidate the zip file or directory to load
     * @return a fully parsed {@link Datapack}, or {@code null} if no {@code pack.mcmeta} found
     * @throws DatapackLoadException if parsing or I/O fails fatally
     */
    @Nullable
    private static Datapack loadCandidate(@Nonnull File candidate) throws DatapackLoadException {
        String packName = derivePackName(candidate);

        IDatapackSource source;
        try {
            if (candidate.isDirectory()) {
                source = new FolderDatapackSource(candidate);
            } else {
                source = new ZipDatapackSource(candidate);
            }
        } catch (IOException e) {
            throw new DatapackLoadException(
                "Failed to open datapack '" + packName + "' at " + candidate.getAbsolutePath(), e);
        }

        try {
            if (!source.hasEntry("pack.mcmeta")) {
                source.close();
                return null;
            }

            byte[] metaBytes;
            try {
                metaBytes = source.readEntry("pack.mcmeta");
            } catch (IOException e) {
                throw new DatapackLoadException(
                    "Failed to read pack.mcmeta from datapack '" + packName + "' at "
                        + candidate.getAbsolutePath(),
                    e);
            }

            String metaJson = new String(metaBytes, StandardCharsets.UTF_8);

            PackMetadata metadata;
            try {
                metadata = PackMetadataParser.parse(packName, metaJson);
            } catch (DatapackParseException e) {
                throw new DatapackLoadException(
                    "Failed to parse pack.mcmeta for datapack '" + packName + "' at "
                        + candidate.getAbsolutePath() + ": " + e.getMessage(),
                    e);
            }

            return new Datapack(StandardDatapackOwners.World, packName, candidate, metadata, source);
        } catch (DatapackLoadException e) {
            // Close the source before propagating the exception
            try {
                source.close();
            } catch (IOException closeEx) {
                LOGGER.error(
                    "Failed to close source for datapack '{}' after load failure.",
                    packName,
                    closeEx);
            }
            throw e;
        } catch (IOException e) {
            try {
                source.close();
            } catch (IOException closeEx) {
                LOGGER.error(
                    "Failed to close source for datapack '{}' after load failure.",
                    packName,
                    closeEx);
            }
            throw new DatapackLoadException(
                "I/O error loading datapack '" + packName + "' at " + candidate.getAbsolutePath(), e);
        }
    }

    /**
     * Iterates all packs (highest priority first), enumerates resources from overlays then base,
     * tracks claimed resource locations, and dispatches each unclaimed resource to its handler.
     *
     * @param orderedPacks    packs in lowest-to-highest priority order (index 0 = lowest)
     * @param warnedTypes     set tracking types already warned about this run
     * @throws DatapackLoadException if a handler throws, or a resource path is invalid
     */
    private static void enumerateAndDispatch(
        @Nonnull List<Datapack> orderedPacks,
        @Nonnull Set<String> warnedTypes) throws DatapackLoadException {

        for (Entry<String, IDatapackTypeHandler> e : DatapackHandlerRegistry.entrySet(Side.SERVER)) {
            try {
                e.getValue().onLoadStart();
            } catch (Exception ex) {
                throw new DatapackLoadException(
                    "Error in IDatapackTypeHandler.onLoadStart ('" + e + "')", ex);
            }
        }

        TagRegistry.INSTANCE.clearDynamic();

        // Claimed resources are tracked across all packs
        Set<ResourceId> claimed = new HashSet<>();

        // Iterate from highest priority (last index) to lowest (index 0)
        for (int i = orderedPacks.size() - 1; i >= 0; i--) {
            Datapack pack = orderedPacks.get(i);
            IDatapackSource source = pack.getSource();

            // Overlay resolution and validation
            List<String> overlayDirs = activeOverlayDirectories(pack.getMetadata(), LOADER_FORMAT_VERSION);

            // Enumerate overlays in reverse order (last-listed first = highest priority)
            for (int oi = overlayDirs.size() - 1; oi >= 0; oi--) {
                String dir = overlayDirs.get(oi);

                // Validate overlay directory name
                validateOverlayDirectoryName(pack, dir);

                String overlayDataPrefix = dir + "/data/";
                List<String> overlayEntries = source.listEntries(overlayDataPrefix);

                // Skip empty overlay silently
                if (overlayEntries.isEmpty()) {
                    continue;
                }

                for (String entryPath : overlayEntries) {
                    // Strip the "<dir>/data/" prefix
                    String relativePath = entryPath.substring(overlayDataPrefix.length());
                    ResourceId id = deriveResourceLocation(pack.getName(), relativePath);

                    if ("tags".equals(id.resourceType())) {
                        // Tags are additive — bypass the claimed set entirely
                        routeTagEntry(pack, entryPath, id, source);
                    } else {
                        if (!claimed.add(id)) {
                            // Already claimed by a higher-priority pack or earlier overlay
                            continue;
                        }
                        dispatchEntry(pack, entryPath, id, source, warnedTypes);
                    }
                }
            }

            // Enumerate base data directory
            String baseDataPrefix = "data/";
            List<String> baseEntries = source.listEntries(baseDataPrefix);

            for (String entryPath : baseEntries) {
                String relativePath = entryPath.substring(baseDataPrefix.length());
                ResourceId id = deriveResourceLocation(pack.getName(), relativePath);

                if ("tags".equals(id.resourceType())) {
                    routeTagEntry(pack, entryPath, id, source);
                } else {
                    if (!claimed.add(id)) {
                        continue;
                    }
                    dispatchEntry(pack, entryPath, id, source, warnedTypes);
                }
            }
        }

        TagRegistry.INSTANCE.resolve();

        for (Entry<String, IDatapackTypeHandler> e : DatapackHandlerRegistry.entrySet(Side.SERVER)) {
            try {
                e.getValue().onLoadFinished();
            } catch (Exception ex) {
                throw new DatapackLoadException(
                    "Error in IDatapackTypeHandler.onLoadStart ('" + e + "')", ex);
            }
        }
    }

    /**
     * Validates that an overlay directory name does not contain path traversal characters.
     */
    private static void validateOverlayDirectoryName(@Nonnull Datapack pack, @Nonnull String dir)
        throws DatapackLoadException {
        if (dir.isEmpty()) {
            throw new DatapackLoadException(
                "Datapack '" + pack.getName() + "' at " + pack.getSourcePath().getAbsolutePath()
                    + " has an empty overlay directory name");
        }
        if (dir.contains("/")) {
            throw new DatapackLoadException(
                "Datapack '" + pack.getName() + "' at " + pack.getSourcePath().getAbsolutePath()
                    + " has an overlay directory name containing '/': '" + dir + "'");
        }
        if (dir.equals("..")) {
            throw new DatapackLoadException(
                "Datapack '" + pack.getName() + "' at " + pack.getSourcePath().getAbsolutePath()
                    + " has an overlay directory name that is '..': '" + dir + "'");
        }
        // Check for ".." segments (guarding against names like "foo..bar" is not required;
        // the spec checks for ".." as a segment, which is only possible if "/" were present —
        // already caught above. This check covers the case where dir itself equals "..".
        // Since "/" is already excluded, the only "segment" is the full string itself.
    }

    /**
     * Routes a tag entry (resourceType == "tags") to {@link TagRegistry}.
     *
     * <p>The {@code id.id()} field encodes both the tag type and tag name separated by the last
     * {@code /}. For example, {@code "blocks/logs"} yields tagType {@code "blocks"} and
     * tagId {@code "<namespace>:logs"}.
     */
    private static void routeTagEntry(
        @Nonnull Datapack pack,
        @Nonnull String entryPath,
        @Nonnull ResourceId id,
        @Nonnull IDatapackSource source) throws DatapackLoadException {

        int lastSlash = id.id().lastIndexOf('/');
        if (lastSlash < 0) {
            LOGGER.warn(
                "Tag resource '{}' has no type segment in id '{}'; skipping.",
                entryPath,
                id.id());
            return;
        }

        String tagType = id.id().substring(0, lastSlash);  // e.g. "blocks", "worldgen/biome"
        String tagName = id.id().substring(lastSlash + 1); // e.g. "logs"
        String tagId   = id.namespace() + ":" + tagName;   // e.g. "minecraft:logs"

        byte[] content;
        try {
            content = source.readEntry(entryPath);
        } catch (IOException e) {
            throw new DatapackLoadException(
                "Failed to read tag entry '" + entryPath + "' from datapack '" + pack.getName() + "'",
                e);
        }

        TagFile tagFile;
        try {
            tagFile = TagFile.GSON.fromJson(
                new String(content, StandardCharsets.UTF_8), TagFile.class);
        } catch (Exception e) {
            throw new DatapackLoadException(
                "Failed to parse tag '" + entryPath + "' in pack '" + pack.getName() + "'", e);
        }

        TagRegistry.INSTANCE.accumulate(tagType, tagId, tagFile);
    }

    /**
     * Reads the entry content and dispatches it to the appropriate type handler.
     */
    private static void dispatchEntry(
        @Nonnull Datapack pack,
        @Nonnull String entryPath,
        @Nonnull ResourceId id,
        @Nonnull IDatapackSource source,
        @Nonnull Set<String> warnedTypes) throws DatapackLoadException {

        IDatapackTypeHandler handler = DatapackHandlerRegistry.getTypeHandler(id.resourceType(), Side.SERVER);

        if (handler == null) {
            // Warn once per unknown type per run
            if (warnedTypes.add(id.resourceType())) {
                LOGGER.warn(
                    "No handler registered for datapack resource type '{}' (first seen at {}); skipping.",
                    id.resourceType(),
                    id);
            }
            return;
        }

        byte[] content;
        try {
            content = source.readEntry(entryPath);
        } catch (IOException e) {
            throw new DatapackLoadException(
                "Failed to read entry '" + entryPath + "' from datapack '" + pack.getName() + "' at "
                    + pack.getSourcePath().getAbsolutePath(),
                e);
        }

        try {
            handler.handle(id, content);
        } catch (Exception e) {
            throw new DatapackLoadException(
                "Error in handler for datapack '" + pack.getName() + "' resource " + id, e);
        }
    }

    /**
     * Derives a {@link ResourceId} from a path relative to a {@code data/} root.
     *
     * <p>Example: {@code "minecraft/worldgen/biome/plains.json"} →
     * {@code ResourceId("minecraft", "worldgen", "biome/plains", ...)}.
     *
     * @param packName     the pack name, for error messages
     * @param relativePath the path relative to a {@code data/} root
     * @return the derived resource location
     * @throws DatapackLoadException if the path is invalid or contains illegal characters
     */
    @Nonnull
    private static ResourceId deriveResourceLocation(
        @Nonnull String packName,
        @Nonnull String relativePath) throws DatapackLoadException {

        List<Path> segments = new ArrayList<>();

        Paths.get(relativePath).forEach(segments::add);

        if (segments.size() < 3) {
            throw new DatapackLoadException(
                "Datapack '" + packName + "': resource path '" + relativePath
                    + "' is in invalid location: must be within [namespace]/[resource type]/* (FR-ENUM-7)");
        }

        String namespace = segments.get(0).toString();
        String resourceType = segments.get(1).toString();

        if (!VALID_COMPONENT.matcher(namespace).matches()) {
            throw new DatapackLoadException(
                "Datapack '" + packName + "': resource path '" + relativePath
                    + "' has invalid namespace '" + namespace
                    + "' (must match [a-z0-9_.-]+) (FR-ENUM-7)");
        }

        if (!VALID_COMPONENT.matcher(resourceType).matches()) {
            throw new DatapackLoadException(
                "Datapack '" + packName + "': resource path '" + relativePath
                    + "' has invalid resource type '" + resourceType
                    + "' (must match [a-z0-9_.-]+) (FR-ENUM-7)");
        }

        int startIndex = 2;

        // For some reason, worldgen types are within a nested folder. This accounts for that.
        if (resourceType.equals("worldgen")) {
            String subtype = segments.get(2).toString();

            if (!VALID_COMPONENT.matcher(subtype).matches()) {
                throw new DatapackLoadException(
                    "Datapack '" + packName + "': resource path '" + relativePath
                        + "' has invalid resource sub-type '" + subtype
                        + "' (must match [a-z0-9_.-]+) (FR-ENUM-7)");
            }

            resourceType += "/" + subtype;
            startIndex = 3;

            if (segments.size() < 3) {
                throw new DatapackLoadException(
                    "Datapack '" + packName + "': resource path '" + relativePath
                        + "' is in invalid location: must be within [namespace]/worldgen/[worldgen subtype]/* (FR-ENUM-7)");
            }
        }

        // Join with '/' explicitly to ensure cross-platform consistency.
        StringBuilder restBuilder = new StringBuilder();
        for (int si = startIndex; si < segments.size(); si++) {
            if (si > startIndex) restBuilder.append('/');
            restBuilder.append(segments.get(si).toString());
        }
        String restStr = restBuilder.toString();

        int lastDot = restStr.lastIndexOf('.');

        String id;

        if (lastDot > -1) {
            id = restStr.substring(0, lastDot);
        } else {
            id = restStr;
        }

        return new ResourceId(namespace, resourceType, id, relativePath);
    }

    /**
     * Returns the list of overlay directory names active for the given metadata and format version.
     *
     * @param metadata          the pack metadata
     * @param loaderFormatVersion the loader's format version constant
     * @return active overlay directories in original list order; empty if none are active
     */
    @Nonnull
    private static List<String> activeOverlayDirectories(
        @Nonnull PackMetadata metadata,
        int loaderFormatVersion) {

        if (metadata.getOverlays() == null) {
            return Collections.emptyList();
        }

        List<OverlayEntry> entries = metadata.getOverlays().getEntries();
        List<String> result = new ArrayList<String>();
        for (OverlayEntry entry : entries) {
            if (entry.getFormats().contains(loaderFormatVersion)) {
                result.add(entry.getDirectory());
            }
        }
        return result;
    }

    /**
     * Derives the human-readable pack name from a file or directory. For ZIP files, strips the
     * {@code .zip} extension. For directories, uses the directory name directly.
     */
    @Nonnull
    private static String derivePackName(@Nonnull File candidate) {
        String name = candidate.getName();
        if (candidate.isFile() && name.toLowerCase().endsWith(".zip")) {
            return name.substring(0, name.length() - ".zip".length());
        }
        return name;
    }
}
