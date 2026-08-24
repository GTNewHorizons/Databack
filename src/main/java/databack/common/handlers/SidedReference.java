package databack.common.handlers;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.relauncher.Side;

public interface SidedReference<T> {

    T get(Side side);

    default T get() {
        return get(FMLCommonHandler.instance().getEffectiveSide());
    }

    static <T> SidedReference<T> of(Supplier<T> ctor) {
        return of(ctor.get(), ctor.get());
    }

    static <T> SidedReference<T> of(Function<Side, T> ctor) {
        return of(ctor.apply(Side.CLIENT), ctor.apply(Side.SERVER));
    }

    static <T> SidedReference<T> of(T client, T server) {
        return side -> switch (side) {
            case CLIENT -> client;
            case SERVER -> server;
        };
    }
}
