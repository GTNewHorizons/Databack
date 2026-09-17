package databack.common.interop.modern_block;

import java.lang.reflect.Type;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.gtnewhorizon.gtnhlib.blockstate.core.BlockPropertyTrait;
import com.gtnewhorizon.gtnhlib.blockstate.core.InvalidPropertyJsonException;
import com.gtnewhorizon.gtnhlib.blockstate.core.InvalidPropertyTextException;
import com.gtnewhorizon.gtnhlib.blockstate.core.MetaBlockProperty;
import com.gtnewhorizon.gtnhlib.blockstate.properties.IntegerBlockProperty;
import com.gtnewhorizon.gtnhlib.blockstate.properties.IntegerBlockProperty.IntegerMetaBlockProperty;

public class VariantBlockProperty<Variant extends Enum<Variant> & BlockVariant> implements MetaBlockProperty<Variant> {

    private final Class<Variant> enumType;
    private final IntegerMetaBlockProperty backing;

    private final Variant[] universe;

    public VariantBlockProperty(Class<Variant> enumType, IntegerMetaBlockProperty backing) {
        this.enumType = enumType;
        this.backing = backing;

        this.universe = enumType.getEnumConstants();
    }

    public VariantBlockProperty(Class<Variant> enumType, String name, int mask) {
        this.enumType = enumType;
        this.backing = (IntegerMetaBlockProperty) IntegerBlockProperty.meta(name, mask, Integer.numberOfTrailingZeros(mask));

        this.universe = enumType.getEnumConstants();
    }

    @Override
    public String getName() {
        return backing.getName();
    }

    @Override
    public Type getType() {
        return enumType;
    }

    @Override
    public int getMeta(Variant variant, int existing) {
        return backing.getMetaPrimitive(variant.ordinal(), existing);
    }

    @Override
    public Variant getValue(int meta) {
        return universe[backing.getValuePrimitive(meta)];
    }

    @Override
    public boolean hasTrait(BlockPropertyTrait trait) {
        return backing.hasTrait(trait);
    }

    @Override
    public Variant deserialize(JsonElement element) throws InvalidPropertyJsonException {
        return parse(element.getAsString());
    }

    @Override
    public JsonElement serialize(Variant variant) {
        return new JsonPrimitive(stringify(variant));
    }

    @Override
    public Variant parse(String text) throws InvalidPropertyTextException {
        try {
            return Enum.valueOf(enumType, text.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new InvalidPropertyTextException("Invalid enum variant '" + text + "'", e);
        }
    }

    @Override
    public String stringify(Variant variant) {
        return variant.name().toLowerCase();
    }
}
