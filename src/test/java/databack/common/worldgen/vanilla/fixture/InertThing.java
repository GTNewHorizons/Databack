package databack.common.worldgen.vanilla.fixture;

/// Stands for a codec: something [RewriteFixture] builds but never needs.
public class InertThing {

    public static InertThing make() {
        return new InertThing();
    }

    public InertThing named(String name) {
        return this;
    }

    public int size() {
        return 41;
    }
}
