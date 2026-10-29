package databack.common.interop;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.UnaryOperator;

import net.minecraft.block.Block;
import net.minecraft.block.BlockRedstoneComparator;
import net.minecraft.block.BlockRedstoneLight;
import net.minecraft.block.BlockRedstoneRepeater;
import net.minecraft.block.BlockRedstoneTorch;
import net.minecraft.block.BlockSign;
import net.minecraft.block.BlockSlab;
import net.minecraft.block.BlockStairs;
import net.minecraft.block.BlockTrapDoor;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/// Converts modern block state properties into the names and values GTNHLib uses for 1.7 blocks, e.g. a slab's
/// `type=top` into `top=true`, and back. Properties without a rule pass through unchanged.
public final class PropertyTranslator {

    private PropertyTranslator() {}

    /// One property's two names and the value conversions between them; a conversion gives null for values it drops.
    private static final class Entry {

        final Class<?> blockClass;
        final String modernName;
        /// Null when the property has no 1.7 equivalent.
        @Nullable
        final String legacyName;
        final UnaryOperator<String> toLegacy, toModern;

        Entry(Class<?> blockClass, String modernName, @Nullable String legacyName, UnaryOperator<String> toLegacy,
            UnaryOperator<String> toModern) {
            this.blockClass = blockClass;
            this.modernName = modernName;
            this.legacyName = legacyName;
            this.toLegacy = toLegacy;
            this.toModern = toModern;
        }
    }

    private static final List<Entry> RULES = new ArrayList<>();

    /// Modern properties that 1.7 computes from neighbours or doesn't have. Dropped without a warning when a block lacks
    /// them.
    private static final Set<String> MODERN_ONLY = new HashSet<>(Arrays.asList(
        "waterlogged", "north", "east", "south", "west", "up", "down", "snowy", "in_wall", "distance"));

    /// Translates `modernName` on blocks that are instances of `blockClass` (so EFR subclasses are included) to and
    /// from GTNHLib's `legacyName`. Earlier rules win.
    public static void register(Class<?> blockClass, String modernName, String legacyName,
        UnaryOperator<String> toLegacy, UnaryOperator<String> toModern) {
        RULES.add(new Entry(blockClass, modernName, legacyName, toLegacy, toModern));
    }

    /// Drops `modernName` on blocks that are instances of `blockClass`: 1.7 computes it, or has no equivalent.
    public static void drop(Class<?> blockClass, String modernName) {
        RULES.add(new Entry(blockClass, modernName, null, value -> null, value -> null));
    }

    /// The GTNHLib `{name, value}` for a modern property on `block`, or null to drop it.
    @Nullable
    public static String[] translate(@NotNull Block block, @NotNull String name, @NotNull String value) {
        for (Entry entry : RULES) {
            if (entry.modernName.equals(name) && entry.blockClass.isInstance(block)) {
                String translated = entry.legacyName == null ? null : entry.toLegacy.apply(value);
                return translated == null ? null : new String[] { entry.legacyName, translated };
            }
        }

        return new String[] { name, value };
    }

    /// The modern `{name, value}` for a GTNHLib property on `block`, or null to drop it.
    @Nullable
    public static String[] untranslate(@NotNull Block block, @NotNull String name, @NotNull String value) {
        for (Entry entry : RULES) {
            if (name.equals(entry.legacyName) && entry.blockClass.isInstance(block)) {
                String translated = entry.toModern.apply(value);
                return translated == null ? null : new String[] { entry.modernName, translated };
            }
        }

        return new String[] { name, value };
    }

    /// Whether a modern property can be dropped silently when the 1.7 block doesn't have it.
    public static boolean isModernOnly(@NotNull String name) {
        return MODERN_ONLY.contains(name);
    }

    /// Maps `modern[i]` to `legacy[i]` and back; other values are dropped.
    private static void values(Class<?> blockClass, String modernName, String legacyName, String[] modern,
        String[] legacy) {
        register(blockClass, modernName, legacyName, value -> lookup(value, modern, legacy),
            value -> lookup(value, legacy, modern));
    }

    @Nullable
    private static String lookup(String value, String[] from, String[] to) {
        for (int i = 0; i < from.length; i++) {
            if (from[i].equals(value)) return to[i];
        }
        return null;
    }

    private static void rename(Class<?> blockClass, String modernName, String legacyName) {
        register(blockClass, modernName, legacyName, UnaryOperator.identity(), UnaryOperator.identity());
    }

    @Nullable
    private static Integer parseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Nullable
    private static Float parseFloat(String value) {
        try {
            return Float.parseFloat(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static {
        // Double slabs are separate blocks (see StateForms), so only the half is left
        values(BlockSlab.class, "type", "top", new String[] { "bottom", "top" }, new String[] { "false", "true" });

        // GTNHLib stores the half as which way the stair is 'up': upside-down stairs point down
        values(BlockStairs.class, "half", "up", new String[] { "bottom", "top" }, new String[] { "up", "down" });
        drop(BlockStairs.class, "shape");

        values(BlockTrapDoor.class, "half", "up", new String[] { "top", "bottom" }, new String[] { "up", "down" });

        // Unlit torches and lamps are separate blocks (see StateForms); GTNHLib calls the switch 'powered'
        rename(BlockRedstoneTorch.class, "lit", "powered");
        rename(BlockRedstoneLight.class, "lit", "powered");

        values(BlockRedstoneComparator.class, "mode", "mode", new String[] { "compare", "subtract" },
            new String[] { "comparator", "subtractor" });

        // Modern delays are 1-4, 1.7 stores 0-3
        register(BlockRedstoneRepeater.class, "delay", "delay", value -> {
            Integer delay = parseInt(value);
            return delay == null ? null : Integer.toString(delay - 1);
        }, value -> {
            Integer delay = parseInt(value);
            return delay == null ? null : Integer.toString(delay + 1);
        });

        // Modern rotations are sixteenths of a turn, GTNHLib's are degrees
        register(BlockSign.class, "rotation", "rotation", value -> {
            Integer rotation = parseInt(value);
            return rotation == null ? null : Float.toString(rotation * 22.5f);
        }, value -> {
            Float degrees = parseFloat(value);
            return degrees == null ? null : Integer.toString(Math.floorMod(Math.round(degrees / 22.5f), 16));
        });
    }
}
