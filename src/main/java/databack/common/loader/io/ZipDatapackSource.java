package databack.common.loader.io;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.annotation.Nonnull;

/**
 * Implements {@link IDatapackSource} over a {@code .zip} file.
 *
 * <p>Opens the zip on construction, delegates existence checks and path enumeration to the zip
 * entry index, and reads entry contents into a byte array. Closes the underlying {@link ZipFile}
 * on {@link #close()}. {@code pack.mcmeta} is excluded from all {@link #listEntries} results.
 */
public class ZipDatapackSource implements IDatapackSource {

    private static final String PACK_MCMETA = "pack.mcmeta";

    private final ZipFile zipFile;

    /**
     * Opens the given zip file.
     *
     * @param zipFile the zip file to open
     * @throws IOException if the file cannot be opened as a zip
     */
    public ZipDatapackSource(@Nonnull File zipFile) throws IOException {
        this.zipFile = new ZipFile(zipFile);
    }

    @Override
    public boolean hasEntry(@Nonnull String path) {
        return zipFile.getEntry(path) != null;
    }

    @Override
    @Nonnull
    public List<String> listEntries(@Nonnull String pathPrefix) {
        List<String> result = new ArrayList<String>();
        Enumeration<? extends ZipEntry> entries = zipFile.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            if (entry.isDirectory()) {
                continue;
            }
            String name = entry.getName();
            // Exclude pack.mcmeta from all listings
            if (name.equals(PACK_MCMETA) || name.endsWith("/" + PACK_MCMETA)) {
                continue;
            }
            if (name.startsWith(pathPrefix)) {
                result.add(name);
            }
        }
        return result;
    }

    @Override
    @Nonnull
    public byte[] readEntry(@Nonnull String path) throws IOException {
        ZipEntry entry = zipFile.getEntry(path);
        if (entry == null) {
            throw new IOException("Entry not found in zip: " + path);
        }
        InputStream in = zipFile.getInputStream(entry);
        try {
            return readAllBytes(in);
        } finally {
            in.close();
        }
    }

    @Override
    public void close() throws IOException {
        zipFile.close();
    }

    private static byte[] readAllBytes(InputStream in) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int read;
        while ((read = in.read(chunk)) != -1) {
            buffer.write(chunk, 0, read);
        }
        return buffer.toByteArray();
    }
}
