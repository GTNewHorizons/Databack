package databack.common.worldgen.vanilla.fixture;

/// Replaces [VanillaShelf]. Compiled without the rewritten bundle, so it sees [RewriteFixture] as [Object], and has
/// none of the inert members.
public final class ShelfStandIn {

    private ShelfStandIn() {}

    public static Object echo(Object value) {
        return value;
    }
}
