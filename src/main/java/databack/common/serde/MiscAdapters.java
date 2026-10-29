package databack.common.serde;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.ResourceLocation;

import org.apache.commons.lang3.mutable.MutableBoolean;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.gtnewhorizon.gtnhlib.blockstate.core.BlockProperty;
import com.gtnewhorizon.gtnhlib.blockstate.core.BlockState;
import com.gtnewhorizon.gtnhlib.blockstate.core.BlockStateImpl;
import com.gtnewhorizon.gtnhlib.blockstate.registry.BlockPropertyRegistry;
import com.gtnewhorizon.gtnhlib.eventbus.EventBusSubscriber;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.registry.GameRegistry;
import databack.CommonProxy;
import databack.common.dto.BlockStateDTO;
import databack.common.interop.PropertyTranslator;
import databack.common.interop.modern_block.BlockIdentity;
import databack.common.interop.modern_block.StateForms;
import databack.common.interop.registry.BlockIdentityRegistry;
import databack.common.loader.DatapackEvent.DatapackStartLoadingEvent;
import databack.common.loader.DatapackLoader;
import databack.common.mixinext.BlockExt_Identity;

@EventBusSubscriber
public class MiscAdapters {

    private static final Logger BLOCK_STATE_LOGGER = LogManager.getLogger("datapack|blockstate");

    public static void init() {
        DatapackSerialization.getBuilder()
            .registerTypeAdapter(BlockState.class, new BlockStateAdapter())
            .registerTypeAdapter(ResourceLocation.class, new ResourceLocationAdapter())
            .registerTypeAdapter(IChatComponent.class, new IChatComponent.Serializer());
    }

    private static final HashSet<ResourceLocation> MISSING_BLOCKS = new HashSet<>();

    /// A datapack block state as a 1.7 block state, through [BlockIdentityRegistry]. Unknown blocks become air (logged
    /// once per name).
    public static BlockState resolveBlockState(BlockStateDTO dto) {
        BlockIdentity identity = BlockIdentityRegistry.INSTANCE.getObject(dto.Name);

        BlockState state = new BlockStateImpl();

        if (identity == null) {
            synchronized (MISSING_BLOCKS) {
                if (MISSING_BLOCKS.add(dto.Name)) {
                    BLOCK_STATE_LOGGER.error("Could not find block {}, using air", dto.Name);
                }
            }

            state.reset(Blocks.air);
        } else {
            // Some states are a separate 1.7 block (lit furnaces, double slabs); that block already encodes the property
            StateForms.Form form = BlockIdentityRegistry.INSTANCE.findStateForm(dto.Name, dto.Properties);

            if (form != null) identity = form.identity;

            state.reset(identity.block);

            BlockExt_Identity blockExt = (BlockExt_Identity) identity.block;

            if (blockExt.db$getVariantProperty() != null) {
                state.setPropertyValue(blockExt.db$getVariantProperty(), identity.variant);
            }

            if (dto.Properties != null && !dto.Properties.isEmpty()) {
                // Properties are set as resolved GTNHLib properties: getBlockMeta() ignores ones only set by name
                Map<String, BlockProperty<?>> available = new HashMap<>();
                BlockPropertyRegistry.getPossibleProperties(
                    identity.block, identity.getBlockMeta(0), prop -> available.put(prop.getName(), prop));

                for (var e : dto.Properties.entrySet()) {
                    if (form != null && form.property.equals(e.getKey())) continue;

                    setTranslatedProperty(state, dto.Name, available, e.getKey(), e.getValue());
                }
            }
        }

        return state;
    }

    private static final HashSet<String> UNSUPPORTED_PROPERTIES = new HashSet<>();

    private static void setTranslatedProperty(BlockState state, ResourceLocation modernId,
        Map<String, BlockProperty<?>> available, String name, String value) {
        String[] translated = PropertyTranslator.translate(state.getBlock(), name, value);

        if (translated == null) return;

        BlockProperty<?> property = available.get(translated[0]);

        if (property == null) {
            if (!PropertyTranslator.isModernOnly(name)) {
                warnOnce(modernId + "[" + name + "]", "Block {} property {} has no 1.7 equivalent, ignoring it", modernId, name);
            }

            return;
        }

        try {
            setParsed(state, property, translated[1]);
        } catch (Exception e) {
            warnOnce(modernId + "[" + name + "=" + value + "]", "Block {} property {}={} could not be applied: {}",
                modernId, name, value, e.getMessage());
        }
    }

    private static <T> void setParsed(BlockState state, BlockProperty<T> property, String text) throws Exception {
        state.setPropertyValue(property, property.parse(text));
    }

    private static void warnOnce(String key, String message, Object... args) {
        synchronized (UNSUPPORTED_PROPERTIES) {
            if (UNSUPPORTED_PROPERTIES.add(key)) BLOCK_STATE_LOGGER.warn(message, args);
        }
    }

    @SubscribeEvent
    public static void resetMissingBlocks(DatapackStartLoadingEvent event) {
        synchronized (MISSING_BLOCKS) {
            MISSING_BLOCKS.clear();
        }

        synchronized (UNSUPPORTED_PROPERTIES) {
            UNSUPPORTED_PROPERTIES.clear();
        }
    }

    public static class BlockStateAdapter implements JsonDeserializer<BlockState>, JsonSerializer<BlockState> {

        @Override
        public BlockState deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
            throws JsonParseException {
            return resolveBlockState(context.deserialize(json, BlockStateDTO.class));
        }

        @Override
        public JsonElement serialize(BlockState src, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject obj = new JsonObject();

            var id = GameRegistry.findUniqueIdentifierFor(src.getBlock());

            obj.addProperty("Name", id.modId + ":" + id.name);

            JsonObject props = new JsonObject();
            MutableBoolean filled = new MutableBoolean(false);

            src.forEachValue((name, state, prop, value) -> {
                filled.setValue(true);
                props.addProperty(name, String.valueOf(value));
            });

            if (filled.booleanValue()) {
                obj.add("Properties", props);
            }

            return obj;
        }
    }

    public static class ResourceLocationAdapter implements JsonDeserializer<ResourceLocation>, JsonSerializer<ResourceLocation> {

        @Override
        public ResourceLocation deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
            throws JsonParseException {
            String str = json.getAsString();

            return new ResourceLocation(str);
        }

        @Override
        public JsonElement serialize(ResourceLocation src, Type typeOfSrc, JsonSerializationContext context) {
            return new JsonPrimitive(src.toString());
        }
    }
}
