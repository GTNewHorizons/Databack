package databack.common.serde;

import java.lang.reflect.Type;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.util.IChatComponent;

import org.apache.commons.lang3.mutable.MutableBoolean;
import org.jetbrains.annotations.Nullable;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.gtnewhorizon.gtnhlib.blockstate.core.BlockState;
import com.gtnewhorizon.gtnhlib.blockstate.core.BlockStateImpl;
import cpw.mods.fml.common.registry.GameRegistry;
import databack.CommonProxy;
import databack.common.loader.DatapackLoader;

public class MiscAdapters {

    public static void init() {
        DatapackSerialization.getBuilder()
            .registerTypeAdapter(BlockState.class, new BlockStateAdapter())
            .registerTypeAdapter(IChatComponent.class, (JsonDeserializer<IChatComponent>) (json, typeOfT, context) -> null);
    }

    public static class BlockStateDTO {

        public String Name;

        @Nullable
        public Map<String, String> Properties;

    }

    public static class BlockStateAdapter implements JsonDeserializer<BlockState>, JsonSerializer<BlockState> {

        @Override
        public BlockState deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
            throws JsonParseException {
            BlockStateDTO dto = context.deserialize(json, BlockStateDTO.class);

            String[] halves = dto.Name.split(":");

            Block block = GameRegistry.findBlock(halves[0], halves[1]);

            if (block == null && CommonProxy.isEFRLoaded() && halves[0].equals("minecraft")) {
                block = GameRegistry.findBlock("etfuturum", halves[1]);
            }

            if (block == null) {
                DatapackLoader.LOGGER.error("Could not find block {}, using air", dto.Name);
                block = Blocks.air;
            }

            BlockState state = new BlockStateImpl();

            state.reset(block);

            if (dto.Properties != null) {
                for (var e : dto.Properties.entrySet()) {
                    state.setPropertyValue(e.getKey(), e.getValue());
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
}
