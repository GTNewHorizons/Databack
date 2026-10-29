package databack.common.worldgen.vanilla.fixture;

import java.util.function.Supplier;

/// Rewritten by `VanillaClassRewriterTest`, which makes [InertThing] inert, leaves [MissingThing] unsupported and
/// replaces [VanillaShelf] with [ShelfStandIn].
public class RewriteFixture {

    public static final InertThing CODEC = InertThing.make().named("x");
    public static final MissingThing MISSING = new MissingThing("y");
    public static final Supplier<MissingThing> FACTORY = MissingThing::new;
    public static final int VALUE = Integer.parseInt("7");

    public static int useInert(InertThing thing) {
        return thing.size() + 1;
    }

    public static RewriteFixture echo(RewriteFixture value) {
        return VanillaShelf.echo(value);
    }

    public static boolean inertMembersAreNull() {
        return VanillaShelf.CODEC == null && VanillaShelf.codec() == null;
    }

    public static int useMissing() {
        return new MissingThing("w").hashCode();
    }
}
