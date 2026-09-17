package databack.common.serde;

import java.lang.reflect.Type;
import java.util.HashSet;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.ResourceLocation;

import org.apache.commons.lang3.mutable.MutableBoolean;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.gtnewhorizon.gtnhlib.blockstate.core.BlockState;
import com.gtnewhorizon.gtnhlib.blockstate.core.BlockStateImpl;
import com.gtnewhorizon.gtnhlib.eventbus.EventBusSubscriber;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.registry.GameRegistry;
import databack.CommonProxy;
import databack.common.dto.BlockStateDTO;
import databack.common.interop.BlockNameTransformer;
import databack.common.interop.modern_block.BlockIdentity;
import databack.common.interop.registry.BlockIdentityRegistry;
import databack.common.loader.DatapackEvent.DatapackStartLoadingEvent;
import databack.common.loader.DatapackLoader;
import databack.common.mixinext.BlockExt_Identity;

@EventBusSubscriber
public class MiscAdapters {

    public static void init() {
        DatapackSerialization.getBuilder()
            .registerTypeAdapter(BlockState.class, new BlockStateAdapter())
            .registerTypeAdapter(ResourceLocation.class, new ResourceLocationAdapter())
            .registerTypeAdapter(IChatComponent.class, new IChatComponent.Serializer());
    }

    private static final HashSet<ResourceLocation> MISSING_BLOCKS = new HashSet<>();

    @SubscribeEvent
    public static void resetMissingBlocks(DatapackStartLoadingEvent event) {
        synchronized (MISSING_BLOCKS) {
            MISSING_BLOCKS.clear();
        }
    }

    public static class BlockStateAdapter implements JsonDeserializer<BlockState>, JsonSerializer<BlockState> {

        @Override
        public BlockState deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
            throws JsonParseException {
            BlockStateDTO dto = context.deserialize(json, BlockStateDTO.class);

            BlockIdentity identity = BlockIdentityRegistry.INSTANCE.getObject(dto.Name);

            BlockState state = new BlockStateImpl();

            if (identity == null) {
                synchronized (MISSING_BLOCKS) {
                    if (MISSING_BLOCKS.add(dto.Name)) {
                        DatapackLoader.LOGGER.error("Could not find block {}, using air", dto.Name);
                    }
                }

                state.reset(Blocks.air);
            } else {
                state.reset(identity.block);

                BlockExt_Identity blockExt = (BlockExt_Identity) identity.block;

                if (blockExt.db$getVariantProperty() != null) {
                    state.setPropertyValue(blockExt.db$getVariantProperty(), identity.variant);
                }

                if (dto.Properties != null) {
                    for (var e : dto.Properties.entrySet()) {
                        state.setPropertyValue(e.getKey(), e.getValue());
                    }
                }
            }

            return state;
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
