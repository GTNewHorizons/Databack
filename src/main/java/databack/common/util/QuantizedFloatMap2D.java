package databack.common.util;

import com.gtnewhorizon.gtnhlib.space.XYZAddressable;
import com.gtnewhorizon.gtnhlib.space.XZAddressable;
import com.gtnewhorizon.gtnhlib.util.CoordinatePacker;
import com.gtnewhorizon.gtnhlib.util.CoordinatePacker2D;
import it.unimi.dsi.fastutil.longs.Long2FloatOpenHashMap;

public class QuantizedFloatMap2D extends Long2FloatOpenHashMap {

    private final float resolution;

    public QuantizedFloatMap2D(float resolution) {
        this.resolution = resolution;
    }

    private int quantize(float value) {
        return (int) (value * resolution + 0.5f);
    }

    public float get(float x, float z) {
        int posX = quantize(x);
        int posZ = quantize(z);

        return super.get(CoordinatePacker2D.packChunk(posX, posZ));
    }

    public float remove(float x, float z) {
        int posX = quantize(x);
        int posZ = quantize(z);

        return super.remove(CoordinatePacker2D.packChunk(posX, posZ));
    }

    public boolean containsKey(float x, float z) {
        int posX = quantize(x);
        int posZ = quantize(z);

        return super.containsKey(CoordinatePacker2D.packChunk(posX, posZ));
    }

    public float put(float x, float z, float v) {
        int posX = quantize(x);
        int posZ = quantize(z);

        return super.put(CoordinatePacker2D.packChunk(posX, posZ), v);
    }
}
