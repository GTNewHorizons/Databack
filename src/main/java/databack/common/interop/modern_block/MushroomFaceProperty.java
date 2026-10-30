package databack.common.interop.modern_block;

import org.jetbrains.annotations.NotNull;

import com.gtnewhorizon.gtnhlib.blockstate.core.BlockPropertyTrait;
import com.gtnewhorizon.gtnhlib.blockstate.properties.BooleanBlockProperty.BooleanMetaBlockProperty;

/// One face of a 1.7 huge mushroom block (modern `up`, `north`, ...: whether the face shows the cap or stem, not
/// pores). 1.7 doesn't store faces separately: its meta is one of a few layouts, so each face reads and writes the
/// whole layout.
///
/// - 0: pores everywhere; 14: cap everywhere; 15: stem everywhere; 10: stem on the sides only.
/// - 1-9: cap on top and on at most one north/south and one east/west side: 1 north-west, 2 north, 3 north-east,
///   4 west, 5 none, 6 east, 7 south-west, 8 south, 9 south-east.
///
/// GTNHLib sets faces one at a time, so a face lost on the way to a layout stays lost:
///
/// - **Approximation:** other cap layouts become the nearest top layout (north before south, west before east, and a
///   top even when `up` is false). Modern caps that differ only there have another cap block above or beside them,
///   so the difference is hidden. The whole cap (14) is kept but never reached from another layout; vanilla's
///   features don't place it.
/// - A stem's ends follow the last of `up` and `down` set: modern stems show both ends or neither.
public final class MushroomFaceProperty implements BooleanMetaBlockProperty {

    private static final int UP = 1, DOWN = 2, NORTH = 4, SOUTH = 8, WEST = 16, EAST = 32;
    private static final int SIDES = NORTH | SOUTH | WEST | EAST, ALL = SIDES | UP | DOWN;

    private static final int PORES = 0, CAP = 14, STEM_SIDES = 10, STEM = 15;
    /// The 1-9 layouts' faces, by meta.
    private static final int[] TOP_LAYOUTS = { -1, UP | NORTH | WEST, UP | NORTH, UP | NORTH | EAST, UP | WEST, UP,
        UP | EAST, UP | SOUTH | WEST, UP | SOUTH, UP | SOUTH | EAST };

    @NotNull
    private final String name;
    private final int face;

    private MushroomFaceProperty(@NotNull String name, int face) {
        this.name = name;
        this.face = face;
    }

    /// One property per face, in modern's names.
    @NotNull
    public static MushroomFaceProperty[] all() {
        return new MushroomFaceProperty[] { new MushroomFaceProperty("up", UP), new MushroomFaceProperty("down", DOWN),
            new MushroomFaceProperty("north", NORTH), new MushroomFaceProperty("south", SOUTH),
            new MushroomFaceProperty("west", WEST), new MushroomFaceProperty("east", EAST) };
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
    public boolean getValuePrimitive(int meta) {
        return (faces(meta) & face) != 0;
    }

    @Override
    public int getMetaPrimitive(boolean value, int existing) {
        if (existing == STEM || existing == STEM_SIDES) {
            if ((face & (UP | DOWN)) == 0) return existing;
            return value ? STEM : STEM_SIDES;
        }
        return capMeta(value ? faces(existing) | face : faces(existing) & ~face);
    }

    static int faces(int meta) {
        if (meta >= 1 && meta <= 9) return TOP_LAYOUTS[meta];
        if (meta == STEM_SIDES) return SIDES;
        if (meta == CAP || meta == STEM) return ALL;
        return 0;
    }

    static int capMeta(int faces) {
        if (faces == ALL) return CAP;
        if (faces == 0) return PORES;

        int northSouth = (faces & NORTH) != 0 ? NORTH : faces & SOUTH;
        int westEast = (faces & WEST) != 0 ? WEST : faces & EAST;
        int layout = UP | northSouth | westEast;
        for (int meta = 1; meta <= 9; meta++) {
            if (TOP_LAYOUTS[meta] == layout) return meta;
        }
        throw new AssertionError("Every top layout is listed: " + layout);
    }
}
