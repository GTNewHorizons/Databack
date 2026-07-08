package databack.common.loader.io;

import java.io.Closeable;
import java.io.IOException;
import java.util.List;

import javax.annotation.Nonnull;

/**
 * Abstracts the on-disk representation of a pack root, hiding whether the pack is a ZIP file or a
 * filesystem folder.
 *
 * <p>Implementations must not hold resources open after {@link #close()} is called.
 * {@code pack.mcmeta} itself is never included in any listing returned by {@link #listEntries}.
 */
public interface IDatapackSource extends Closeable {

    /**
     * Returns {@code true} when an entry at exactly {@code path} exists within this source.
     *
     * @param path path relative to the pack root with forward-slash separators; no leading slash
     */
    boolean hasEntry(@Nonnull String path);

    /**
     * Returns all file entry paths within this source whose path starts with {@code pathPrefix},
     * with forward-slash separators and no leading slash.
     *
     * <p>The prefix must end with {@code /} to avoid matching entries in sibling directories.
     * The list is not guaranteed to be sorted. Only file entries are returned; directory entries
     * are never included. {@code pack.mcmeta} is never included in the returned list.
     *
     * @param pathPrefix the path prefix to filter by (e.g. {@code "data/"})
     * @return an unordered list of matching file entry paths; empty if no entries match
     */
    @Nonnull
    List<String> listEntries(@Nonnull String pathPrefix);

    /**
     * Reads the full contents of the entry at {@code path} into a fresh byte array and returns it.
     *
     * @param path path relative to the pack root with forward-slash separators; no leading slash
     * @return the complete raw byte content of the entry
     * @throws IOException when the entry does not exist or cannot be read
     */
    @Nonnull
    byte[] readEntry(@Nonnull String path) throws IOException;

    /**
     * Releases all resources held by this source. After {@code close()}, all other methods have
     * undefined behaviour.
     */
    @Override
    void close() throws IOException;
}
