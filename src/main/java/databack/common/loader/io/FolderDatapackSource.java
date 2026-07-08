package databack.common.loader.io;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;

/**
 * Implements {@link IDatapackSource} over a filesystem directory.
 *
 * <p>Delegates existence checks to {@link File#exists()} and path enumeration to a recursive
 * directory walk. Reads file contents into a byte array on demand. Has no persistent file handles;
 * {@link #close()} is a no-op. {@code pack.mcmeta} is excluded from all {@link #listEntries}
 * results.
 */
public class FolderDatapackSource implements IDatapackSource {

    private static final String PACK_MCMETA = "pack.mcmeta";

    @Nonnull
    private final File root;

    /**
     * @param root the root directory of the datapack
     */
    public FolderDatapackSource(@Nonnull File root) {
        this.root = root;
    }

    @Override
    public boolean hasEntry(@Nonnull String path) {
        return new File(root, path.replace('/', File.separatorChar)).exists();
    }

    @Override
    @Nonnull
    public List<String> listEntries(@Nonnull String pathPrefix) {
        List<String> result = new ArrayList<String>();
        collectEntries(root, "", pathPrefix, result);
        return result;
    }

    /**
     * Recursively collects file entries under {@code dir}, building paths relative to {@code root}
     * using {@code /} separators.
     */
    private void collectEntries(
        @Nonnull File dir,
        @Nonnull String relativePrefix,
        @Nonnull String pathPrefix,
        @Nonnull List<String> result) {

        File[] children = dir.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            String relativePath = relativePrefix.isEmpty() ? child.getName()
                : relativePrefix + "/" + child.getName();

            if (child.isDirectory()) {
                collectEntries(child, relativePath, pathPrefix, result);
            } else {
                // Exclude pack.mcmeta from all listings
                if (relativePath.equals(PACK_MCMETA) || relativePath.endsWith("/" + PACK_MCMETA)) {
                    continue;
                }
                if (relativePath.startsWith(pathPrefix)) {
                    result.add(relativePath);
                }
            }
        }
    }

    @Override
    @Nonnull
    public byte[] readEntry(@Nonnull String path) throws IOException {
        File file = new File(root, path.replace('/', File.separatorChar));
        return Files.readAllBytes(file.toPath());
    }

    @Override
    public void close() {
        // No-op: folder source holds no persistent file handles
    }
}
