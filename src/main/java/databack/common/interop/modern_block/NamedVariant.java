package databack.common.interop.modern_block;

/// A [BlockVariant] identified by a string, for blocks whose variants are only listed once (as modern ids) instead of
/// as a dedicated enum. Instances are owned by a [NamedVariantProperty] and compared by reference.
public final class NamedVariant implements BlockVariant {

    private final int ordinal;
    private final String name;

    NamedVariant(int ordinal, String name) {
        this.ordinal = ordinal;
        this.name = name;
    }

    @Override
    public int ordinal() {
        return ordinal;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String toString() {
        return name;
    }
}
