package databack.common.worldgen.surface;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;

import org.junit.jupiter.api.Assertions;

/// A chunk the reference harness dumped (`run.sh surface`, `run.sh aquifer`): block names by position, properties
/// dropped.
public final class SurfaceDump {

    public final int minY;
    public final int height;
    private final String[] palette;
    private final short[] indices;

    private SurfaceDump(int minY, int height, String[] palette, short[] indices) {
        this.minY = minY;
        this.height = height;
        this.palette = palette;
        this.indices = indices;
    }

    static SurfaceDump read(String name) throws IOException {
        return read("surface", name);
    }

    /// A dump in `vanilla_reference/<dir>/`.
    public static SurfaceDump read(String dir, String name) throws IOException {
        InputStream raw = SurfaceDump.class.getResourceAsStream("/vanilla_reference/" + dir + "/" + name);
        Assertions.assertNotNull(raw,
            "Missing reference data " + name + " (run misc/vanilla-reference/run.sh " + dir + ")");

        try (DataInputStream in = new DataInputStream(new GZIPInputStream(raw))) {
            int minY = in.readInt(), height = in.readInt();
            String[] palette = new String[in.readInt()];
            for (int i = 0; i < palette.length; i++) {
                String full = in.readUTF();
                int bracket = full.indexOf('[');
                palette[i] = bracket < 0 ? full : full.substring(0, bracket);
            }
            short[] indices = new short[height * 256];
            for (int i = 0; i < indices.length; i++) indices[i] = in.readShort();
            return new SurfaceDump(minY, height, palette, indices);
        }
    }

    public String block(int x, int y, int z) {
        return palette[indices[((y - minY) * 16 + z) * 16 + x]];
    }

    /// The layout the surface system sees: open blocks are air or fluid, everything else solid.
    public byte[] kinds() {
        byte[] kinds = new byte[indices.length];
        for (int i = 0; i < kinds.length; i++) {
            String name = palette[indices[i]];
            kinds[i] = name.equals("minecraft:air") || name.equals("minecraft:cave_air") ? SurfaceSystem.AIR
                : name.equals("minecraft:water") ? SurfaceSystem.FLUID
                : name.equals("minecraft:lava") ? SurfaceSystem.LAVA
                : SurfaceSystem.SOLID;
        }
        return kinds;
    }
}
