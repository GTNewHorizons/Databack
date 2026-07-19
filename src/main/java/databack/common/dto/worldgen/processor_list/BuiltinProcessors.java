package databack.common.dto.worldgen.processor_list;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.gtnewhorizon.gtnhlib.blockstate.core.BlockState;
import databack.common.dto.worldgen.int_provider.IIntProvider;
import databack.common.serde.DatapackSerialization;
import databack.common.serde.TaggedUnionLoader;

@SuppressWarnings("unused")
public class BuiltinProcessors {

    public static void init() {
        // IProcessor — tagged on "processor_type"
        TaggedUnionLoader<IProcessor> processors = DatapackSerialization
            .createTaggedUnionLoader("worldgen/processor", IProcessor.class, "processor_type");

        processors.addVariant("minecraft:rule", RuleProcessor.class);
        processors.addVariant("minecraft:block_age", BlockAgeProcessor.class);
        processors.addVariant("minecraft:block_ignore", BlockIgnoreProcessor.class);
        processors.addVariant("minecraft:block_rot", BlockRotProcessor.class);
        processors.addVariant("minecraft:gravity", GravityProcessor.class);
        processors.addVariant("minecraft:protected_blocks", ProtectedBlocksProcessor.class);
        processors.addVariant("minecraft:capped", CappedProcessor.class);

        // IRuleTest — tagged on "predicate_type"
        TaggedUnionLoader<IRuleTest> ruleTests = DatapackSerialization
            .createTaggedUnionLoader("worldgen/rule_test", IRuleTest.class, "predicate_type");

        ruleTests.addVariant("minecraft:always_true", AlwaysTrueTest.class);
        ruleTests.addVariant("minecraft:block_match", BlockMatchTest.class);
        ruleTests.addVariant("minecraft:blockstate_match", BlockStateMatchTest.class);
        ruleTests.addVariant("minecraft:random_block_match", RandomBlockMatchTest.class);
        ruleTests.addVariant("minecraft:random_blockstate_match", RandomBlockStateMatchTest.class);
        ruleTests.addVariant("minecraft:tag_match", TagMatchTest.class);

        // ProcessorList — handles both [] and {processors:[]} forms
        DatapackSerialization.getBuilder().registerTypeAdapter(
            ProcessorList.class,
            (JsonDeserializer<ProcessorList>) (json, typeOfT, ctx) -> {
                JsonArray array;
                if (json.isJsonArray()) {
                    array = json.getAsJsonArray();
                } else {
                    array = json.getAsJsonObject().getAsJsonArray("processors");
                }
                List<IProcessor> procs = new ArrayList<>(array == null ? 0 : array.size());
                if (array != null) {
                    for (JsonElement el : array) {
                        procs.add(ctx.deserialize(el, IProcessor.class));
                    }
                }
                ProcessorList result = new ProcessorList();
                result.processors = procs;
                return result;
            });

        // IProcessorListRef — string | ProcessorList (array or object form)
        DatapackSerialization.getBuilder().registerTypeAdapter(
            IProcessorListRef.class,
            (JsonDeserializer<IProcessorListRef>) (json, typeOfT, ctx) -> {
                if (json.isJsonPrimitive()) {
                    return new ProcessorListIdRef(json.getAsString());
                }
                return ctx.deserialize(json, ProcessorList.class);
            });
    }

    // ---- Processors -----------------------------------------------------------

    private static class RuleProcessor implements IProcessor {

        public List<ProcessorRule> rules;
    }

    static class ProcessorRule {

        public IRuleTest input_predicate;
        public IRuleTest location_predicate;
        @Nullable
        public JsonElement position_predicate;
        public BlockState output_state;
        @Nullable
        public JsonElement block_entity_modifier;
    }

    private static class BlockAgeProcessor implements IProcessor {

        public float mossiness;
    }

    private static class BlockIgnoreProcessor implements IProcessor {

        public List<BlockState> blocks;
    }

    private static class BlockRotProcessor implements IProcessor {

        public float integrity;
        @Nullable
        public JsonElement rottable_blocks;
    }

    private static class GravityProcessor implements IProcessor {

        public String heightmap;
        public int offset;
    }

    private static class ProtectedBlocksProcessor implements IProcessor {

        public JsonElement value;
    }

    private static class CappedProcessor implements IProcessor {

        public IProcessor delegate;
        public IIntProvider limit;
    }

    // ---- Rule tests -----------------------------------------------------------

    private static class AlwaysTrueTest implements IRuleTest {}

    private static class BlockMatchTest implements IRuleTest {

        public String block;
    }

    private static class BlockStateMatchTest implements IRuleTest {

        public BlockState block_state;
    }

    private static class RandomBlockMatchTest implements IRuleTest {

        public String block;
        public float probability;
    }

    private static class RandomBlockStateMatchTest implements IRuleTest {

        public BlockState block_state;
        public float probability;
    }

    private static class TagMatchTest implements IRuleTest {

        public String tag;
    }
}
