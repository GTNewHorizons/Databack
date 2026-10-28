package databack.common.worldgen.vanilla;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import databack.common.loader.VanillaDatapackDownloader;

/// The 26.1 client jar that `downloadVanillaDatapack` caches; the build passes its path in.
public final class VanillaJar {

    public static final Path JAR = Paths.get(System.getProperty("databack.vanillaJar",
        System.getProperty("user.home") + "/.gradle/caches/minecraft-client-jars/minecraft-26.1-client.jar"));

    private VanillaJar() {}

    public static byte[] classFile(String entry) throws IOException {
        assertTrue(Files.exists(JAR), "Missing vanilla jar " + JAR + " (run the downloadVanillaDatapack task)");
        try (ZipFile zip = new ZipFile(JAR.toFile())) {
            ZipEntry e = zip.getEntry(entry);
            assertTrue(e != null, "Missing " + entry + " in " + JAR);
            try (InputStream in = zip.getInputStream(e)) {
                return readAll(in);
            }
        }
    }

    /// Lays out every class `VanillaDatapackDownloader` fetches the way it caches them.
    public static void cacheClasses(File datapackDir) throws IOException {
        for (String entry : VanillaDatapackDownloader.VANILLA_CLASSES) {
            Path dest = VanillaDatapackDownloader.getClassFile(datapackDir, entry).toPath();
            Files.createDirectories(dest.getParent());
            Files.write(dest, classFile(entry));
        }
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) >= 0) out.write(buf, 0, n);
        return out.toByteArray();
    }
}
