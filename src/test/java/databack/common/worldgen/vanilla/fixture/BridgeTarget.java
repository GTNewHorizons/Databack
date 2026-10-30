package databack.common.worldgen.vanilla.fixture;

/// What `VanillaBridgesTest` bridges to: a static method, a constructor, and an instance method that can't be.
public final class BridgeTarget {

    public final String name;

    public BridgeTarget(String name, long count) {
        this.name = name + count;
    }

    public static long twice(long value) {
        return value * 2;
    }

    public static double half(int value) {
        return value / 2.0;
    }

    public long instance(long value) {
        return value;
    }
}
