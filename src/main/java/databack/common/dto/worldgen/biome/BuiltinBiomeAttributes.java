package databack.common.dto.worldgen.biome;

import static databack.common.serde.DatapackSerialization.createTaggedUnionLoader;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.gtnewhorizon.gtnhlib.color.ImmutableColor;
import com.gtnewhorizon.gtnhlib.color.RGBColor;

import databack.common.annotation.RangeFloat;
import databack.common.dto.SoundEventRef;
import databack.common.serde.DatapackSerialization;
import databack.common.serde.TaggedUnionLoader;
import databack.common.util.DBMathUtils;

public class BuiltinBiomeAttributes {

    public static void init() {
        DatapackSerialization.getBuilder()
            .registerTypeAdapter(ImmutableColor.class, new ImmutableColorDeserializer())
            .registerTypeAdapter(SoundEventRef.class, new SoundEventRefDeserializer())
            .registerTypeAdapter(DatapackBiome.BiomeMusicList.class, new BiomeMusicListDeserializer());

        TaggedUnionLoader<BooleanBiomeAttribute> bools = createTaggedUnionLoader("builtin/biome_attributes/bools", BooleanBiomeAttribute.class);

        bools.setTagField("modifier");
        bools.addVariant("override", OverrideBBA.class);
        bools.addVariant("and", AndBBA.class);
        bools.addVariant("nand", NandBBA.class);
        bools.addVariant("or", OrBBA.class);
        bools.addVariant("nor", NorBBA.class);
        bools.addVariant("xor", XorBBA.class);
        bools.addVariant("xnor", XnorBBA.class);
        bools.setFallback((json, typeOfT, context) -> {
            var value = new OverrideBBA();
            value.argument = json.getAsBoolean();
            return value;
        });

        TaggedUnionLoader<FloatBiomeAttribute> floats = createTaggedUnionLoader("builtin/biome_attributes/floats", FloatBiomeAttribute.class);

        floats.setTagField("modifier");
        floats.addVariant("override", OverrideFBA.class);
        floats.addVariant("add", AddFBA.class);
        floats.addVariant("sub", SubFBA.class);
        floats.addVariant("mul", MulFBA.class);
        floats.addVariant("min", MinFBA.class);
        floats.addVariant("max", MaxFBA.class);
        floats.addVariant("alpha_blend", AlphaBlendFBA.class);
        floats.setFallback((json, typeOfT, context) -> {
            var value = new OverrideFBA();
            value.argument = json.getAsFloat();
            return value;
        });

        TaggedUnionLoader<RGBBiomeAttribute> rgb = createTaggedUnionLoader("builtin/biome_attributes/rgb", RGBBiomeAttribute.class);

        rgb.setTagField("modifier");
        rgb.addVariant("override", OverrideCBA.class);
        rgb.addVariant("add", AddCBA.class);
        rgb.addVariant("sub", SubCBA.class);
        rgb.addVariant("mul", MulCBA.class);
        rgb.addVariant("alpha_blend", AlphaBlendCBA.class);
        rgb.addVariant("blend_to_gray", BlendToGrayCBA.class);
        rgb.setFallback((json, typeOfT, context) -> {
            var value = new OverrideCBA();
            value.argument = context.deserialize(json, ImmutableColor.class);
            return value;
        });

        TaggedUnionLoader<RGBABiomeAttribute> rgba = createTaggedUnionLoader("builtin/biome_attributes/rgba", RGBABiomeAttribute.class);

        rgba.setTagField("modifier");
        rgba.addVariant("override", OverrideCBA_RGBA.class);
        rgba.addVariant("add", AddCBA_RGBA.class);
        rgba.addVariant("sub", SubCBA_RGBA.class);
        rgba.addVariant("mul", MulCBA_RGBA.class);
        rgba.addVariant("alpha_blend", AlphaBlendCBA_RGBA.class);
        rgba.addVariant("blend_to_gray", BlendToGrayCBA_RGBA.class);
        rgba.setFallback((json, typeOfT, context) -> {
            var value = new OverrideCBA_RGBA();
            value.argument = context.deserialize(json, ImmutableColor.class);
            return value;
        });
    }

    private static class SoundEventRefDeserializer implements JsonDeserializer<SoundEventRef> {

        @Override
        public SoundEventRef deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
            throws JsonParseException {
            SoundEventRef ref = new SoundEventRef();
            if (json.isJsonPrimitive()) {
                ref.sound_id = json.getAsString();
            } else if (json.isJsonObject()) {
                JsonObject obj = json.getAsJsonObject();
                ref.sound_id = obj.get("sound_id").getAsString();
                if (obj.has("range")) {
                    ref.range = obj.get("range").getAsFloat();
                }
            } else {
                throw new JsonParseException("Expected string or object for SoundEventRef, got: " + json);
            }
            return ref;
        }
    }

    private static class BiomeMusicListDeserializer implements JsonDeserializer<DatapackBiome.BiomeMusicList> {

        @Override
        public DatapackBiome.BiomeMusicList deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
            throws JsonParseException {
            if (!json.isJsonArray()) {
                throw new JsonParseException("Expected array for BiomeMusicList, got: " + json);
            }
            JsonArray array = json.getAsJsonArray();
            List<DatapackBiome.WeightedBiomeMusic> entries = new ArrayList<>(array.size());
            for (JsonElement element : array) {
                JsonObject obj = element.getAsJsonObject();
                DatapackBiome.WeightedBiomeMusic entry = new DatapackBiome.WeightedBiomeMusic();
                entry.weight = obj.get("weight").getAsInt();
                entry.data = context.deserialize(obj.get("data"), DatapackBiome.BiomeMusic.class);
                entries.add(entry);
            }
            return new DatapackBiome.BiomeMusicList(entries);
        }
    }

    private static class ImmutableColorDeserializer implements JsonDeserializer<ImmutableColor> {

        @Override
        public ImmutableColor deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
            throws JsonParseException {
            if (!json.isJsonPrimitive()) {
                throw new JsonParseException("Expected color string (#rrggbb or #aarrggbb) or integer: " + json);
            }
            if (json.getAsJsonPrimitive().isNumber()) {
                return RGBColor.fromARGB(json.getAsInt());
            }
            String str = json.getAsString();
            if (str.startsWith("#")) {
                str = str.substring(1);
                int value = (int) Long.parseLong(str, 16);
                return str.length() <= 6 ? RGBColor.fromRGB(value) : RGBColor.fromARGB(value);
            }
            throw new JsonParseException("Expected color string (#rrggbb or #aarrggbb) or integer: " + json);
        }
    }

    public interface BooleanBiomeAttribute {
        boolean apply(boolean value);
    }

    public static abstract class BinaryBooleanBiomeAttribute implements BooleanBiomeAttribute {
        public boolean argument;
    }

    public static class OverrideBBA extends BinaryBooleanBiomeAttribute {

        @Override
        public boolean apply(boolean value) {
            return this.argument;
        }
    }

    public static class AndBBA extends BinaryBooleanBiomeAttribute {

        @Override
        public boolean apply(boolean value) {
            return value && this.argument;
        }
    }

    public static class NandBBA extends BinaryBooleanBiomeAttribute {

        @Override
        public boolean apply(boolean value) {
            return !(value && this.argument);
        }
    }

    public static class OrBBA extends BinaryBooleanBiomeAttribute {

        @Override
        public boolean apply(boolean value) {
            return value || this.argument;
        }
    }

    public static class NorBBA extends BinaryBooleanBiomeAttribute {

        @Override
        public boolean apply(boolean value) {
            return !(value || this.argument);
        }
    }

    public static class XorBBA extends BinaryBooleanBiomeAttribute {

        @Override
        public boolean apply(boolean value) {
            return value ^ this.argument;
        }
    }

    public static class XnorBBA extends BinaryBooleanBiomeAttribute {

        @Override
        public boolean apply(boolean value) {
            return value == this.argument;
        }
    }

    public interface FloatBiomeAttribute {
        float apply(float value);
    }

    public static abstract class BinaryFloatBiomeAttribute implements FloatBiomeAttribute {
        public float argument;
    }

    public static class OverrideFBA extends BinaryFloatBiomeAttribute {

        @Override
        public float apply(float value) {
            return this.argument;
        }
    }

    public static class AddFBA extends BinaryFloatBiomeAttribute {

        @Override
        public float apply(float value) {
            return this.argument + value;
        }
    }

    public static class SubFBA extends BinaryFloatBiomeAttribute {

        @Override
        public float apply(float value) {
            return this.argument - value;
        }
    }

    public static class MulFBA extends BinaryFloatBiomeAttribute {

        @Override
        public float apply(float value) {
            return this.argument * value;
        }
    }

    public static class MinFBA extends BinaryFloatBiomeAttribute {

        @Override
        public float apply(float value) {
            return Math.min(this.argument, value);
        }
    }

    public static class MaxFBA extends BinaryFloatBiomeAttribute {

        @Override
        public float apply(float value) {
            return Math.max(this.argument, value);
        }
    }

    public static class FloatWithAlpha {

        public float value;
        @Nullable
        @RangeFloat(min = 0, max = 1)
        public Float alpha;
    }

    public static class AlphaBlendFBA implements FloatBiomeAttribute {

        public FloatWithAlpha argument;

        @Override
        public float apply(float value) {
            float alpha = Math.max(0, Math.min(1, argument.alpha == null ? 0f : argument.alpha));

            return (1 - alpha) * value + alpha * value;
        }
    }

    public interface RGBBiomeAttribute {
        void apply(RGBColor color);
    }

    public static abstract class BinaryRGBBiomeAttribute implements RGBBiomeAttribute {
        public ImmutableColor argument;
    }

    public static class OverrideCBA extends BinaryRGBBiomeAttribute {

        @Override
        public void apply(RGBColor color) {
            color.set(argument.getRed(), argument.getGreen(), argument.getBlue());
        }
    }

    public static class AddCBA extends BinaryRGBBiomeAttribute {

        @Override
        public void apply(RGBColor color) {
            color.red = DBMathUtils.clamp(color.getRed() + argument.getRed(), 0, 255);
            color.green = DBMathUtils.clamp(color.getGreen() + argument.getGreen(), 0, 255);
            color.blue = DBMathUtils.clamp(color.getBlue() + argument.getBlue(), 0, 255);
        }
    }

    public static class SubCBA extends BinaryRGBBiomeAttribute {

        @Override
        public void apply(RGBColor color) {
            color.red = DBMathUtils.clamp(color.getRed() - argument.getRed(), 0, 255);
            color.green = DBMathUtils.clamp(color.getGreen() - argument.getGreen(), 0, 255);
            color.blue = DBMathUtils.clamp(color.getBlue() - argument.getBlue(), 0, 255);
        }
    }

    public static class MulCBA extends BinaryRGBBiomeAttribute {

        @Override
        public void apply(RGBColor color) {
            color.red = DBMathUtils.clamp(color.getRed() * argument.getRed() / 255, 0, 255);
            color.green = DBMathUtils.clamp(color.getGreen() * argument.getGreen() / 255, 0, 255);
            color.blue = DBMathUtils.clamp(color.getBlue() * argument.getBlue() / 255, 0, 255);
        }
    }

    public static class AlphaBlendCBA extends BinaryRGBBiomeAttribute {

        @Override
        public void apply(RGBColor color) {
            float alpha = argument.getAlpha() / 255f;

            color.red = (int) DBMathUtils.clamp((1 - alpha) * color.getRed() + alpha * argument.getRed() / 255, 0, 255);
            color.green = (int) DBMathUtils.clamp((1 - alpha) * color.getGreen() + alpha * argument.getGreen() / 255, 0, 255);
            color.blue = (int) DBMathUtils.clamp((1 - alpha) * color.getBlue() + alpha * argument.getBlue() / 255, 0, 255);
        }
    }

    public static class BlendToGray {

        @RangeFloat(min = 0, max = 1)
        public float brightness;
        @RangeFloat(min = 0, max = 1)
        public float factor;
    }

    public static class BlendToGrayCBA implements RGBBiomeAttribute {

        public BlendToGray argument;

        @Override
        public void apply(RGBColor color) {
            float r = color.getRed() / 255f;
            float g = color.getGreen() / 255f;
            float b = color.getBlue() / 255f;

            float brightness = (r * 0.3F + g * 0.59F + b * 0.11F) * argument.brightness;

            float lerp = 1.0F - argument.factor;
            r = r * lerp + brightness * (1.0F - lerp);
            g = g * lerp + brightness * (1.0F - lerp);
            b = b * lerp + brightness * (1.0F - lerp);

            color.red = DBMathUtils.clamp((int) (r * 255f), 0, 255);
            color.green = DBMathUtils.clamp((int) (g * 255f), 0, 255);
            color.blue = DBMathUtils.clamp((int) (b * 255f), 0, 255);
        }
    }

    public interface RGBABiomeAttribute {
        void apply(RGBColor color);
    }

    public static abstract class BinaryRGBABiomeAttribute implements RGBABiomeAttribute {
        public ImmutableColor argument;
    }

    public static class OverrideCBA_RGBA extends BinaryRGBABiomeAttribute {

        @Override
        public void apply(RGBColor color) {
            color.set(argument.getRed(), argument.getGreen(), argument.getBlue(), argument.getAlpha());
        }
    }

    public static class AddCBA_RGBA extends BinaryRGBABiomeAttribute {

        @Override
        public void apply(RGBColor color) {
            color.red = DBMathUtils.clamp(color.getRed() + argument.getRed(), 0, 255);
            color.green = DBMathUtils.clamp(color.getGreen() + argument.getGreen(), 0, 255);
            color.blue = DBMathUtils.clamp(color.getBlue() + argument.getBlue(), 0, 255);
            color.alpha = DBMathUtils.clamp(color.getAlpha() + argument.getAlpha(), 0, 255);
        }
    }

    public static class SubCBA_RGBA extends BinaryRGBABiomeAttribute {

        @Override
        public void apply(RGBColor color) {
            color.red = DBMathUtils.clamp(color.getRed() - argument.getRed(), 0, 255);
            color.green = DBMathUtils.clamp(color.getGreen() - argument.getGreen(), 0, 255);
            color.blue = DBMathUtils.clamp(color.getBlue() - argument.getBlue(), 0, 255);
            color.alpha = DBMathUtils.clamp(color.getAlpha() - argument.getAlpha(), 0, 255);
        }
    }

    public static class MulCBA_RGBA extends BinaryRGBABiomeAttribute {

        @Override
        public void apply(RGBColor color) {
            color.red = DBMathUtils.clamp(color.getRed() * argument.getRed() / 255, 0, 255);
            color.green = DBMathUtils.clamp(color.getGreen() * argument.getGreen() / 255, 0, 255);
            color.blue = DBMathUtils.clamp(color.getBlue() * argument.getBlue() / 255, 0, 255);
            color.alpha = DBMathUtils.clamp(color.getAlpha() * argument.getAlpha() / 255, 0, 255);
        }
    }

    public static class AlphaBlendCBA_RGBA extends BinaryRGBABiomeAttribute {

        @Override
        public void apply(RGBColor color) {
            float newAlpha = argument.getAlpha() / 255f;
            float oldAlpha = color.getAlpha() / 255f;

            float resultAlpha = newAlpha + oldAlpha * (1f - newAlpha);

            if (resultAlpha < 1e6) {
                color.set(0, 0, 0, 0);
                return;
            }

            float resultAlphaInv = 1f / resultAlpha;

            float r = (argument.getRed() / 255f * newAlpha + color.getRed() / 255f * (1 - newAlpha)) * resultAlphaInv;
            float g = (argument.getGreen() / 255f * newAlpha + color.getGreen() / 255f * (1 - newAlpha)) * resultAlphaInv;
            float b = (argument.getBlue() / 255f * newAlpha + color.getBlue() / 255f * (1 - newAlpha)) * resultAlphaInv;

            color.red = DBMathUtils.clamp((int) (r * 255f), 0, 255);
            color.green = DBMathUtils.clamp((int) (g * 255f), 0, 255);
            color.blue = DBMathUtils.clamp((int) (b * 255f), 0, 255);
        }
    }

    public static class BlendToGrayCBA_RGBA implements RGBABiomeAttribute {

        public BlendToGray argument;

        @Override
        public void apply(RGBColor color) {
            float r = color.getRed() / 255f;
            float g = color.getGreen() / 255f;
            float b = color.getBlue() / 255f;

            float brightness = (r * 0.3F + g * 0.59F + b * 0.11F) * argument.brightness;

            float lerp = 1.0F - argument.factor;
            r = r * lerp + brightness * (1.0F - lerp);
            g = g * lerp + brightness * (1.0F - lerp);
            b = b * lerp + brightness * (1.0F - lerp);

            color.red = DBMathUtils.clamp((int) (r * 255f), 0, 255);
            color.green = DBMathUtils.clamp((int) (g * 255f), 0, 255);
            color.blue = DBMathUtils.clamp((int) (b * 255f), 0, 255);
        }
    }
}
