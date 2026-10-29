package databack.common.interop.modern_block;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.util.ResourceLocation;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/// 1.7 blocks that stand for a modern block in a particular state, such as `lit_furnace` for `furnace[lit=true]` or a
/// double slab block for `oak_slab[type=double]`. Modern states are turned into 1.7 blocks by picking the form whose
/// property value matches.
public final class StateForms {

    /// One form: `identity` is used when the modern state has `property` set to `value`.
    public static final class Form {

        @NotNull
        public final String property;
        @NotNull
        public final String value;
        @NotNull
        public final BlockIdentity identity;

        Form(@NotNull String property, @NotNull String value, @NotNull BlockIdentity identity) {
            this.property = property;
            this.value = value;
            this.identity = identity;
        }
    }

    private final HashMap<ResourceLocation, List<Form>> forms = new HashMap<>();

    public void add(@NotNull String property, @NotNull String value, @NotNull BlockIdentity identity) {
        forms.computeIfAbsent(identity.identityId, id -> new ArrayList<>(1))
            .add(new Form(property, value, identity));
    }

    /// The form of `modernId` selected by `properties`, or null if the claimed block should be used. The first form
    /// registered wins if several match.
    @Nullable
    public Form select(@NotNull ResourceLocation modernId, @Nullable Map<String, String> properties) {
        if (properties == null || properties.isEmpty()) return null;

        List<Form> candidates = forms.get(modernId);

        if (candidates == null) return null;

        for (Form form : candidates) {
            if (form.value.equals(properties.get(form.property))) return form;
        }

        return null;
    }
}
