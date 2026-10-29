package databack.common.interop;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
/// `type=top` into `top=true`. Properties without a rule pass through unchanged.
public final class PropertyTranslator {

    private PropertyTranslator() {}

    @FunctionalInterface
    public interface Rule {

        /// @return `{name, value}` for GTNHLib, or null to drop the property (it's computed or has no 1.7 equivalent)
        @Nullable
        String[] translate(@NotNull String value);
    }

    private static final class Entry {

        final Class<?> blockClass;
        final String modernName;
        final Rule rule;

        Entry(Class<?> blockClass, String modernName, Rule rule) {
            this.blockClass = blockClass;
            this.modernName = modernName;
            this.rule = rule;
        }
    }

    private static final List<Entry> RULES = new ArrayList<>();

    /// Modern properties that 1.7 computes from neighbours or doesn't have. Dropped without a warning when a block lacks
    /// them.
    private static final Set<String> MODERN_ONLY = new HashSet<>(Arrays.asList(
        "waterlogged", "north", "east", "south", "west", "up", "down", "snowy", "in_wall", "distance"));

    /// Translates `modernName` for blocks that are instances of `blockClass` (so EFR subclasses are included). Earlier
    /// rules win.
    public static void register(Class<?> blockClass, String modernName, Rule rule) {
        RULES.add(new Entry(blockClass, modernName, rule));
    }

    /// The GTNHLib `{name, value}` for a modern property on `block`, or null to drop it.
    @Nullable
    public static String[] translate(@NotNull Block block, @NotNull String name, @NotNull String value) {
        for (Entry entry : RULES) {
            if (entry.modernName.equals(name) && entry.blockClass.isInstance(block)) {
                return entry.rule.translate(value);
            }
        }

        return new String[] { name, value };
    }

    /// Whether a modern property can be dropped silently when the 1.7 block doesn't have it.
    public static boolean isModernOnly(@NotNull String name) {
        return MODERN_ONLY.contains(name);
    }

    /// Maps `from[i]` to `{name, to[i]}`; other values are dropped.
    private static Rule values(String name, String[] from, String[] to) {
        return value -> {
            for (int i = 0; i < from.length; i++) {
                if (from[i].equals(value)) return new String[] { name, to[i] };
            }

            return null;
        };
    }

    private static Rule rename(String name) {
        return value -> new String[] { name, value };
    }

    static {
        // Double slabs are separate blocks (see StateForms), so only the half is left
        register(BlockSlab.class, "type", values("top", new String[] { "bottom", "top" }, new String[] { "false", "true" }));

        // GTNHLib stores the half as which way the stair is 'up': upside-down stairs point down
        register(BlockStairs.class, "half", values("up", new String[] { "bottom", "top" }, new String[] { "up", "down" }));
        register(BlockStairs.class, "shape", value -> null);

        register(BlockTrapDoor.class, "half", values("up", new String[] { "top", "bottom" }, new String[] { "up", "down" }));

        // Unlit torches and lamps are separate blocks (see StateForms); GTNHLib calls the switch 'powered'
        register(BlockRedstoneTorch.class, "lit", rename("powered"));
        register(BlockRedstoneLight.class, "lit", rename("powered"));

        register(BlockRedstoneComparator.class, "mode",
            values("mode", new String[] { "compare", "subtract" }, new String[] { "comparator", "subtractor" }));

        // Modern delays are 1-4, 1.7 stores 0-3
        register(BlockRedstoneRepeater.class, "delay", value -> {
            try {
                return new String[] { "delay", Integer.toString(Integer.parseInt(value) - 1) };
            } catch (NumberFormatException e) {
                return null;
            }
        });

        // Modern rotations are sixteenths of a turn, GTNHLib's are degrees
        register(BlockSign.class, "rotation", value -> {
            try {
                return new String[] { "rotation", Float.toString(Integer.parseInt(value) * 22.5f) };
            } catch (NumberFormatException e) {
                return null;
            }
        });
    }
}
