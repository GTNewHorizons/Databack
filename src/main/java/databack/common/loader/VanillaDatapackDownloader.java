package databack.common.loader;

import java.io.*;
import java.net.*;
import java.nio.charset.*;
import java.util.*;
import java.util.zip.*;

import com.google.gson.*;
import org.apache.logging.log4j.*;

/**
 * Downloads and caches the vanilla Minecraft datapack using HTTP range requests against the
 * official client JAR. Only the {@code data/} directory and {@code pack.mcmeta} are fetched;
 * the full JAR is never downloaded.
 *
 * <p>The result is cached under {@code <gameDir>/databack/vanilla-datapack-<version>/} with a
 * {@code .complete} marker file. Subsequent calls return immediately if the marker exists.
 */
public class VanillaDatapackDownloader {

    private static final Logger LOGGER = LogManager.getLogger("databack-vanilla");

    static final String MC_VERSION = "26.1";
    private static final String VERSION_MANIFEST_URL =
        "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
    static final String CACHE_DIR_NAME = "vanilla-datapack-" + MC_VERSION;

    // ZIP format signatures
    private static final int SIG_EOCD = 0x06054b50;
    private static final int SIG_CD = 0x02014b50;
    private static final int SIG_LFH = 0x04034b50;
    private static final int SIG_Z64_LOCATOR = 0x07064b50;
    private static final int SIG_Z64_EOCD = 0x06064b50;
    private static final long UINT_MAX = 0xFFFFFFFFL;

    /**
     * Entries within this many bytes of each other are downloaded in one range request.
     * Larger value = fewer requests but potentially more wasted bytes if files are scattered.
     */
    private static final long BATCH_GAP_THRESHOLD = 4L * 1024 * 1024;

    /** Maximum bytes to add past the last entry's end when computing a batch range. */
    private static final int LFH_MAX_VAR_SIZE = 512;

    private static final int TIMEOUT_MS = 60_000;

    private VanillaDatapackDownloader() {}

    /** Cached result from a previous successful call; avoids redundant I/O on world load. */
    private static volatile File cachedDir = null;

    /**
     * Returns the cached vanilla datapack directory, downloading it from Mojang if not present.
     * The returned directory is ready to use as a {@code FolderDatapackSource}.
     *
     * <p>After the first successful call the result is cached in memory, so subsequent calls
     * (e.g. from {@code DatapackLoader}) return immediately.
     *
     * @param gameDir the Minecraft game directory (the {@code .minecraft} folder)
     */
    public static File getOrDownload(File gameDir) throws IOException {
        if (cachedDir != null) return cachedDir;

        File cacheDir = new File(gameDir, "databack/" + CACHE_DIR_NAME);
        if (new File(cacheDir, ".complete").exists()) {
            cachedDir = cacheDir;
            return cachedDir;
        }

        LOGGER.info(
            "Vanilla datapack not cached. Downloading from Mojang (first-time only, may take a moment)...");
        deleteDir(cacheDir);
        cacheDir.mkdirs();

        try {
            String clientUrl = resolveClientJarUrl();
            extractDataEntries(clientUrl, cacheDir);
            new File(cacheDir, ".complete").createNewFile();
            LOGGER.info("Vanilla datapack cached to: {}", cacheDir);
        } catch (IOException e) {
            deleteDir(cacheDir);
            throw new IOException("Failed to download vanilla datapack: " + e.getMessage(), e);
        }

        cachedDir = cacheDir;
        return cachedDir;
    }

    // -------------------------------------------------------------------------
    // Resolution
    // -------------------------------------------------------------------------

    private static String resolveClientJarUrl() throws IOException {
        LOGGER.info("Fetching Minecraft version manifest...");
        JsonObject manifest = fetchJson(VERSION_MANIFEST_URL);

        for (JsonElement el : manifest.getAsJsonArray("versions")) {
            JsonObject v = el.getAsJsonObject();
            if (MC_VERSION.equals(v.get("id").getAsString())) {
                LOGGER.info("Fetching version info for {}...", MC_VERSION);
                JsonObject vJson = fetchJson(v.get("url").getAsString());
                return vJson.getAsJsonObject("downloads")
                    .getAsJsonObject("client")
                    .get("url")
                    .getAsString();
            }
        }

        throw new IOException("Minecraft version '" + MC_VERSION + "' not found in version manifest");
    }

    // -------------------------------------------------------------------------
    // ZIP extraction via range requests
    // -------------------------------------------------------------------------

    private static void extractDataEntries(String jarUrl, File outDir) throws IOException {
        long fileSize = getFileSize(jarUrl);
        LOGGER.info("Client JAR size: {} MB", fileSize / (1024 * 1024));

        // Fetch tail to locate EOCD
        int tailLen = (int) Math.min(65536L, fileSize);
        byte[] tail = fetchRange(jarUrl, fileSize - tailLen, fileSize - 1);

        int eocdRel = findEocdOffset(tail);
        long cdSize = readUInt(tail, eocdRel + 12);
        long cdOffset = readUInt(tail, eocdRel + 16);

        // Handle ZIP64
        if (cdOffset == UINT_MAX || cdSize == UINT_MAX) {
            int locRel = eocdRel - 20;
            if (locRel < 0 || readInt(tail, locRel) != SIG_Z64_LOCATOR) {
                throw new IOException("ZIP64 EOCD locator not found");
            }
            long z64Off = readLong(tail, locRel + 8);
            byte[] z64 = fetchRange(jarUrl, z64Off, z64Off + 55);
            if (readInt(z64, 0) != SIG_Z64_EOCD) {
                throw new IOException("Invalid ZIP64 EOCD signature");
            }
            cdSize = readLong(z64, 40);
            cdOffset = readLong(z64, 48);
        }

        LOGGER.info("Fetching ZIP central directory ({} KB)...", cdSize / 1024);
        byte[] cd = fetchRange(jarUrl, cdOffset, cdOffset + cdSize - 1);

        List<CdEntry> entries = parseCdEntries(cd);
        LOGGER.info("Found {} entries to extract (data/ + pack.mcmeta)", entries.size());

        if (entries.isEmpty()) {
            throw new IOException("No data/ or pack.mcmeta entries found in client JAR");
        }

        entries.sort(Comparator.comparingLong(e -> e.localOffset));
        downloadAndExtract(jarUrl, fileSize, entries, outDir);

        // Synthesize pack.mcmeta if not present in the JAR
        File meta = new File(outDir, "pack.mcmeta");
        if (!meta.exists()) {
            synthesizePackMeta(meta);
        }
    }

    /**
     * Groups entries by proximity and issues one range request per batch.
     */
    private static void downloadAndExtract(
        String url, long fileSize, List<CdEntry> entries, File outDir) throws IOException {

        Inflater inf = new Inflater(true); // raw deflate (no zlib wrapper)
        try {
            int i = 0;
            while (i < entries.size()) {
                // Expand batch: include all subsequent entries within BATCH_GAP_THRESHOLD bytes
                int batchStart = i;
                long batchEnd = entryEnd(entries.get(i));
                while (i + 1 < entries.size()) {
                    long nextStart = entries.get(i + 1).localOffset;
                    if (nextStart <= batchEnd + BATCH_GAP_THRESHOLD) {
                        i++;
                        batchEnd = Math.max(batchEnd, entryEnd(entries.get(i)));
                    } else {
                        break;
                    }
                }

                long from = entries.get(batchStart).localOffset;
                long to = Math.min(batchEnd, fileSize) - 1;
                int count = i - batchStart + 1;

                LOGGER.info(
                    "Downloading {} entries ({} KB)...", count, (to - from + 1) / 1024);

                byte[] buf = fetchRange(url, from, to);

                for (int j = batchStart; j <= i; j++) {
                    extractOne(entries.get(j), from, buf, outDir, inf);
                }

                i++;
            }
        } finally {
            inf.end();
        }
    }

    /** Conservative upper bound of an entry's byte range in the JAR. */
    private static long entryEnd(CdEntry e) {
        return e.localOffset + 30 + LFH_MAX_VAR_SIZE + e.compressedSize;
    }

    private static void extractOne(
        CdEntry e, long bufBase, byte[] buf, File outDir, Inflater inf) throws IOException {

        int rel = (int) (e.localOffset - bufBase);

        if (rel + 30 > buf.length) {
            LOGGER.warn("Entry {} is outside downloaded buffer, skipping", e.name);
            return;
        }
        if (readInt(buf, rel) != SIG_LFH) {
            LOGGER.warn("Entry {} has bad local file header signature, skipping", e.name);
            return;
        }

        int lfnLen = readUShort(buf, rel + 26);
        int lexLen = readUShort(buf, rel + 28);
        int dataOff = rel + 30 + lfnLen + lexLen;
        int dataEnd = (int) (dataOff + e.compressedSize);

        if (dataEnd > buf.length) {
            LOGGER.warn("Entry {} data is truncated in buffer, skipping", e.name);
            return;
        }

        byte[] out;
        if (e.method == 0) { // STORED
            out = Arrays.copyOfRange(buf, dataOff, dataEnd);
        } else if (e.method == 8) { // DEFLATE
            inf.reset();
            inf.setInput(buf, dataOff, (int) e.compressedSize);
            out = new byte[(int) e.uncompressedSize];
            try {
                inf.inflate(out);
            } catch (DataFormatException ex) {
                LOGGER.warn("Deflate error for {}: {}", e.name, ex.getMessage());
                return;
            }
        } else {
            LOGGER.warn("Unsupported compression method {} for {}, skipping", e.method, e.name);
            return;
        }

        File dest = new File(outDir, e.name);
        dest.getParentFile().mkdirs();
        try (FileOutputStream fos = new FileOutputStream(dest)) {
            fos.write(out);
        }
    }

    // -------------------------------------------------------------------------
    // Central Directory parsing
    // -------------------------------------------------------------------------

    private static List<CdEntry> parseCdEntries(byte[] cd) {
        List<CdEntry> result = new ArrayList<>();
        int p = 0;

        while (p + 46 <= cd.length && readInt(cd, p) == SIG_CD) {
            int method = readUShort(cd, p + 10);
            long cSize = readUInt(cd, p + 20);
            long uSize = readUInt(cd, p + 24);
            int fnLen = readUShort(cd, p + 28);
            int exLen = readUShort(cd, p + 30);
            int cmLen = readUShort(cd, p + 32);
            long offset = readUInt(cd, p + 42);
            String name = new String(cd, p + 46, fnLen, StandardCharsets.UTF_8);

            // Read ZIP64 extra fields when sentinel values are present
            if (cSize == UINT_MAX || uSize == UINT_MAX || offset == UINT_MAX) {
                int ep = p + 46 + fnLen;
                int epEnd = ep + exLen;
                while (ep + 4 <= epEnd) {
                    int hId = readUShort(cd, ep);
                    int hLen = readUShort(cd, ep + 2);
                    if (hId == 0x0001) { // ZIP64 extended information
                        int fp = ep + 4;
                        if (uSize == UINT_MAX && fp + 8 <= epEnd) {
                            uSize = readLong(cd, fp);
                            fp += 8;
                        }
                        if (cSize == UINT_MAX && fp + 8 <= epEnd) {
                            cSize = readLong(cd, fp);
                            fp += 8;
                        }
                        if (offset == UINT_MAX && fp + 8 <= epEnd) {
                            offset = readLong(cd, fp);
                        }
                        break;
                    }
                    ep += 4 + hLen;
                }
            }

            boolean wanted = !name.endsWith("/")
                && (name.startsWith("data/") || name.equals("pack.mcmeta"));
            if (wanted) {
                result.add(new CdEntry(name, offset, cSize, uSize, method));
            }

            p += 46 + fnLen + exLen + cmLen;
        }

        return result;
    }

    // -------------------------------------------------------------------------
    // HTTP helpers
    // -------------------------------------------------------------------------

    private static long getFileSize(String url) throws IOException {
        HttpURLConnection c = openConnection(url);
        c.setRequestProperty("Range", "bytes=0-0");
        c.connect();
        try {
            if (c.getResponseCode() == 206) {
                String cr = c.getHeaderField("Content-Range");
                if (cr != null && cr.contains("/")) {
                    return Long.parseLong(cr.substring(cr.lastIndexOf('/') + 1).trim());
                }
            }
            long len = c.getContentLength();
            if (len > 0) return len;
            throw new IOException("Cannot determine file size for " + url);
        } finally {
            c.disconnect();
        }
    }

    private static byte[] fetchRange(String url, long from, long to) throws IOException {
        HttpURLConnection c = openConnection(url);
        c.setRequestProperty("Range", "bytes=" + from + "-" + to);
        c.connect();
        try {
            int code = c.getResponseCode();
            if (code != 206 && code != 200) {
                throw new IOException("Range request returned " + code + " for " + url
                    + " (bytes=" + from + "-" + to + ")");
            }
            try (InputStream in = c.getInputStream()) {
                return readFully(in);
            }
        } finally {
            c.disconnect();
        }
    }

    private static JsonObject fetchJson(String url) throws IOException {
        HttpURLConnection c = openConnection(url);
        c.connect();
        try {
            int code = c.getResponseCode();
            if (code != 200) throw new IOException("GET " + url + " returned " + code);
            try (InputStreamReader r = new InputStreamReader(
                c.getInputStream(), StandardCharsets.UTF_8)) {
                return new Gson().fromJson(r, JsonObject.class);
            }
        } finally {
            c.disconnect();
        }
    }

    private static HttpURLConnection openConnection(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(TIMEOUT_MS);
        c.setReadTimeout(TIMEOUT_MS);
        c.setRequestProperty("User-Agent", "Databack-Mod/" + MC_VERSION);
        return c;
    }

    private static byte[] readFully(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream(65536);
        byte[] tmp = new byte[65536];
        int n;
        while ((n = in.read(tmp)) >= 0) buf.write(tmp, 0, n);
        return buf.toByteArray();
    }

    // -------------------------------------------------------------------------
    // ZIP binary reads (little-endian)
    // -------------------------------------------------------------------------

    private static int findEocdOffset(byte[] buf) throws IOException {
        // Scan backwards; EOCD is at least 22 bytes from the end
        for (int i = buf.length - 22; i >= 0; i--) {
            if (readInt(buf, i) == SIG_EOCD) return i;
        }
        throw new IOException("EOCD signature not found in last 64 KB; JAR may be corrupt");
    }

    private static int readUShort(byte[] b, int o) {
        return (b[o] & 0xFF) | ((b[o + 1] & 0xFF) << 8);
    }

    private static int readInt(byte[] b, int o) {
        return (b[o] & 0xFF)
            | ((b[o + 1] & 0xFF) << 8)
            | ((b[o + 2] & 0xFF) << 16)
            | ((b[o + 3] & 0xFF) << 24);
    }

    private static long readUInt(byte[] b, int o) {
        return readInt(b, o) & 0xFFFFFFFFL;
    }

    private static long readLong(byte[] b, int o) {
        return readUInt(b, o) | (readUInt(b, o + 4) << 32);
    }

    // -------------------------------------------------------------------------
    // Misc
    // -------------------------------------------------------------------------

    private static void synthesizePackMeta(File f) throws IOException {
        String json = "{\"pack\":{\"description\":\"Vanilla Minecraft "
            + MC_VERSION + " datapack\",\"pack_format\":48}}";
        try (OutputStreamWriter w = new OutputStreamWriter(
            new FileOutputStream(f), StandardCharsets.UTF_8)) {
            w.write(json);
        }
        LOGGER.info("Synthesized pack.mcmeta (not present in client JAR)");
    }

    private static void deleteDir(File d) {
        if (!d.exists()) return;
        File[] children = d.listFiles();
        if (children != null) {
            for (File c : children) {
                if (c.isDirectory()) deleteDir(c);
                else c.delete();
            }
        }
        d.delete();
    }

    // -------------------------------------------------------------------------
    // Data
    // -------------------------------------------------------------------------

    private static final class CdEntry {

        final String name;
        final long localOffset;
        final long compressedSize;
        final long uncompressedSize;
        final int method;

        CdEntry(String name, long localOffset, long compressedSize, long uncompressedSize, int method) {
            this.name = name;
            this.localOffset = localOffset;
            this.compressedSize = compressedSize;
            this.uncompressedSize = uncompressedSize;
            this.method = method;
        }
    }
}
