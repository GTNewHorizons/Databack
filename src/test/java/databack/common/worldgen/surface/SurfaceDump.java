package databack.common.worldgen.surface;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;

import org.junit.jupiter.api.Assertions;

/// A chunk the reference harness dumped (`run.sh surface`): block names by position, properties dropped.
final class SurfaceDump {

    final int minY;
    final int height;
    private final String[] palette;
    private final short[] indices;

    private SurfaceDump(int minY, int height, String[] palette, short[] indices) {
        this.minY = minY;
        this.height = height;
        this.palette = palette;
        this.indices = indices;
    }

    static SurfaceDump read(String name) throws IOException {
        InputStream raw = SurfaceDump.class.getResourceAsStream("/vanilla_reference/surface/" + name);
        Assertions.assertNotNull(raw, "Missing reference data " + name + " (run misc/vanilla-reference/run.sh surface)");

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

    String block(int x, int y, int z) {
        return palette[indices[((y - minY) * 16 + z) * 16 + x]];
    }

    /// The layout the surface system sees: open blocks are air or fluid, everything else solid.
    byte[] kinds() {
        byte[] kinds = new byte[indices.length];
        for (int i = 0; i < kinds.length; i++) {
            String name = palette[indices[i]];
            kinds[i] = name.equals("minecraft:air") || name.equals("minecraft:cave_air") ? SurfaceSystem.AIR
                : name.equals("minecraft:water") || name.equals("minecraft:lava") ? SurfaceSystem.FLUID
                : SurfaceSystem.SOLID;
        }
        return kinds;
    }
}
