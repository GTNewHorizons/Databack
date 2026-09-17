package databack.common.util;

import net.minecraft.util.ResourceLocation;

import org.jetbrains.annotations.Nullable;

import com.github.bsideup.jabel.Desugar;
import com.google.common.collect.ImmutableMap;
import com.gtnewhorizon.gtnhlib.blockstate.core.BlockState;
import com.gtnewhorizon.gtnhlib.blockstate.core.BlockStatePool;
import com.gtnewhorizon.gtnhlib.blockstate.registry.BlockPropertyRegistry;

@Desugar
public record BlockSpec(ResourceLocation blockId, ImmutableMap<String, String> properties) {

    public static BlockSpec parse(String spec) {
        int firstBrace = spec.indexOf('[');
        int lastBrace = spec.lastIndexOf(']');

        if (firstBrace == -1 || lastBrace == -1 || lastBrace < firstBrace) {
            throw new IllegalArgumentException("Malformed BlockState string (missing or mismatched brackets): " + spec);
        }

        String id = spec.substring(0, firstBrace);

        String[] idHalves = id.split(":");

        ResourceLocation blockId = new ResourceLocation(
            idHalves.length == 1
            ? "minecraft"
            : idHalves[0], idHalves.length == 1 ? idHalves[0] : idHalves[1]
        );

        ImmutableMap.Builder<String, String> properties = ImmutableMap.builder();

        String values = spec.substring(firstBrace + 1, lastBrace);

        while (!values.isEmpty()) {
            int nextEq = values.indexOf('=');

            String valueName = values.substring(0, nextEq);
            String value;

            if (values.charAt(nextEq + 1) == '"') {
                char[] chars = values.substring(nextEq + 2).toCharArray();

                int i = 0;

                StringBuilder sb = new StringBuilder();

                for (; i < chars.length; i++) {
                    if (chars[i] == '\\') {
                        if (i < chars.length - 1) {
                            sb.append(chars[i + 1]);
                        }

                        i++;
                    } else if (chars[i] == '"') {
                        break;
                    } else {
                        sb.append(chars[i]);
                    }
                }

                value = sb.toString();
                // i points at the closing '"'; skip past it
                values = values.substring(nextEq + 2 + i + 1);
                // skip the comma separator that follows (non-quoted path handles this via nextComma)
                if (!values.isEmpty() && values.charAt(0) == ',') {
                    values = values.substring(1);
                }
            } else {
                int nextComma = values.indexOf(',');

                if (nextComma != -1) {
                    value = values.substring(nextEq + 1, nextComma);
                    values = values.substring(nextComma + 1);
                } else {
                    value = values.substring(nextEq + 1);
                    values = "";
                }
            }

            properties.put(valueName, value);
        }

        return new BlockSpec(blockId, properties.build());
    }
}
