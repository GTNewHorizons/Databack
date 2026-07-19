package databack.common.util;

import com.gtnewhorizon.gtnhlib.space.XYZAddressable;
import com.gtnewhorizon.gtnhlib.util.CoordinatePacker;
import it.unimi.dsi.fastutil.longs.Long2FloatOpenHashMap;

public class QuantizedFloatMap3D extends Long2FloatOpenHashMap {

    private final int resolution;

    public QuantizedFloatMap3D(int resolution) {
        this.resolution = resolution;
    }

    private int quantize(float value) {
        return (int) (value * resolution + 0.5f);
    }

    public float get(float x, float y, float z) {
        int posX = quantize(x);
        int posY = quantize(y);
        int posZ = quantize(z);

        return super.get(CoordinatePacker.pack(posX, posY, posZ));
    }

    public float remove(float x, float y, float z) {
        int posX = quantize(x);
        int posY = quantize(y);
        int posZ = quantize(z);

        return super.remove(CoordinatePacker.pack(posX, posY, posZ));
    }

    public boolean containsKey(float x, float y, float z) {
        int posX = quantize(x);
        int posY = quantize(y);
        int posZ = quantize(z);

        return super.containsKey(CoordinatePacker.pack(posX, posY, posZ));
    }

    public float put(float x, float y, float z, float val) {
        int posX = quantize(x);
        int posY = quantize(y);
        int posZ = quantize(z);

        return super.put(CoordinatePacker.pack(posX, posY, posZ), val);
    }
}
