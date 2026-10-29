package databack.common.worldgen.vanilla.fixture;

/// Stands for a vanilla class `VanillaClassRewriterTest` replaces with [ShelfStandIn].
public class VanillaShelf {

    public static final InertThing CODEC = new InertThing();

    public static RewriteFixture echo(RewriteFixture value) {
        return value;
    }

    public static InertThing codec() {
        return CODEC;
    }
}
