package databack.common.interop.modern_block;

import java.util.function.IntBinaryOperator;
import java.util.function.IntUnaryOperator;

import com.gtnewhorizon.gtnhlib.blockstate.core.BlockPropertyTrait;
import com.gtnewhorizon.gtnhlib.blockstate.properties.IntegerBlockProperty.IntegerMetaBlockProperty;

/// An integer meta property for layouts that aren't a single bit range (e.g. "metas 10 and 15 are the stem").
public class FunctionalMetaProperty implements IntegerMetaBlockProperty {

    private final String name;
    private final IntUnaryOperator decode;
    private final IntBinaryOperator encode;

    /// @param decode meta -> value
    /// @param encode (value, existing meta) -> meta
    public FunctionalMetaProperty(String name, IntUnaryOperator decode, IntBinaryOperator encode) {
        this.name = name;
        this.decode = decode;
        this.encode = encode;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public boolean hasTrait(BlockPropertyTrait trait) {
        return switch (trait) {
            case SupportsWorld, SupportsStacks, OnlyNeedsMeta, WorldMutable, StackMutable -> true;
            default -> false;
        };
    }

    @Override
    public int getMetaPrimitive(int value, int existing) {
        return encode.applyAsInt(value, existing);
    }

    @Override
    public int getValuePrimitive(int meta) {
        return decode.applyAsInt(meta);
    }
}
