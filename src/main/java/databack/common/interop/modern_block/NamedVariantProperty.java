package databack.common.interop.modern_block;

import java.lang.reflect.Type;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.gtnewhorizon.gtnhlib.blockstate.core.BlockPropertyTrait;
import com.gtnewhorizon.gtnhlib.blockstate.core.InvalidPropertyJsonException;
import com.gtnewhorizon.gtnhlib.blockstate.core.InvalidPropertyTextException;
import com.gtnewhorizon.gtnhlib.blockstate.core.MetaBlockProperty;
import com.gtnewhorizon.gtnhlib.blockstate.properties.IntegerBlockProperty.IntegerMetaBlockProperty;

/// A variant property whose values are [NamedVariant]s. The backing property decodes a meta into a variant index;
/// indices outside the variant list wrap around, which matches how most 1.7 blocks treat unused metas (icon lookups
/// are typically `meta % types.length`).
public class NamedVariantProperty implements MetaBlockProperty<NamedVariant> {

    private final IntegerMetaBlockProperty backing;
    private final NamedVariant[] universe;

    public NamedVariantProperty(IntegerMetaBlockProperty backing, String... names) {
        this.backing = backing;
        this.universe = new NamedVariant[names.length];

        for (int i = 0; i < names.length; i++) {
            universe[i] = new NamedVariant(i, names[i]);
        }
    }

    public NamedVariant[] getVariants() {
        return universe.clone();
    }

    @Override
    public String getName() {
        return backing.getName();
    }

    @Override
    public Type getType() {
        return NamedVariant.class;
    }

    @Override
    public boolean hasTrait(BlockPropertyTrait trait) {
        return backing.hasTrait(trait);
    }

    @Override
    public boolean needsExisting() {
        return backing.needsExisting();
    }

    @Override
    public int getMeta(NamedVariant variant, int existing) {
        return backing.getMetaPrimitive(variant.ordinal(), existing);
    }

    @Override
    public NamedVariant getValue(int meta) {
        return universe[Math.floorMod(backing.getValuePrimitive(meta), universe.length)];
    }

    @Override
    public NamedVariant deserialize(JsonElement element) throws InvalidPropertyJsonException {
        return parse(element.getAsString());
    }

    @Override
    public JsonElement serialize(NamedVariant variant) {
        return new JsonPrimitive(stringify(variant));
    }

    @Override
    public NamedVariant parse(String text) throws InvalidPropertyTextException {
        for (NamedVariant variant : universe) {
            if (variant.name().equals(text)) return variant;
        }

        throw new InvalidPropertyTextException("Invalid variant '" + text + "'");
    }

    @Override
    public String stringify(NamedVariant variant) {
        return variant.name();
    }
}
