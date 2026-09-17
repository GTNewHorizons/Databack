package databack.common.util;

import java.util.Objects;
import java.util.function.Predicate;

import net.minecraft.util.ResourceLocation;

import org.apache.commons.lang3.mutable.MutableBoolean;

import com.gtnewhorizon.gtnhlib.blockstate.core.BlockState;
import databack.common.interop.modern_block.BlockIdentity;
import databack.common.interop.registry.BlockIdentityRegistry;
import databack.common.interop.registry.ProxyBlockRegistry;

public interface BlockStatePredicate extends Predicate<BlockState> {


    static BlockStatePredicate matchesLegacy(String spec) {
        return matchesLegacy(BlockSpec.parse(spec));
    }

    static BlockStatePredicate matchesLegacy(BlockSpec spec) {
        return state -> {
            ResourceLocation blockId = ProxyBlockRegistry.INSTANCE.getIdForObject(state.getBlock());

            if (!blockId.equals(spec.blockId())) return false;

            if (spec.properties().isEmpty()) return true;

            MutableBoolean valid = new MutableBoolean(true);

            state.forEachValue((name, propertyState, property, value) -> {
                if (!valid.booleanValue()) return;

                String expected = spec.properties().get(name);

                if (expected == null) return;

                if (property == null) {
                    if (!Objects.equals(expected, value)) {
                        valid.setValue(false);
                    }
                } else {
                    //noinspection unchecked
                    if (!Objects.equals(expected, property.stringify(value))) {
                        valid.setValue(false);
                    }
                }
            });

            return valid.booleanValue();
        };
    }

    static BlockStatePredicate matchesModern(String spec) {
        return matchesModern(BlockSpec.parse(spec));
    }

    static BlockStatePredicate matchesModern(BlockSpec spec) {
        return state -> {
            BlockIdentity blockIdentity = BlockIdentityRegistry.INSTANCE.getBlockIdentity(state.getBlock(), state.getBlockMeta(0));

            if (!blockIdentity.identityId.equals(spec.blockId())) return false;

            if (spec.properties().isEmpty()) return true;

            MutableBoolean valid = new MutableBoolean(true);

            state.forEachValue((name, propertyState, property, value) -> {
                if (!valid.booleanValue()) return;

                String expected = spec.properties().get(name);

                if (expected == null) return;

                if (property == null) {
                    if (!Objects.equals(expected, value)) {
                        valid.setValue(false);
                    }
                } else {
                    //noinspection unchecked
                    if (!Objects.equals(expected, property.stringify(value))) {
                        valid.setValue(false);
                    }
                }
            });

            return valid.booleanValue();
        };
    }
}
