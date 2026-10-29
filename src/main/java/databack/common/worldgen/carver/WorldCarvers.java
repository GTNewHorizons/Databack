package databack.common.worldgen.carver;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

import net.minecraft.init.Blocks;
import net.minecraft.util.ResourceLocation;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.google.gson.JsonElement;
import com.gtnewhorizon.gtnhlib.util.data.BlockMeta;
import com.gtnewhorizon.gtnhlib.util.data.ImmutableBlockMeta;

import databack.common.dto.worldgen.biome.DatapackBiome;
import databack.common.dto.worldgen.carver.CarveStep;
import databack.common.dto.worldgen.carver.IConfiguredCarver;
import databack.common.handlers.BiomeList;
import databack.common.handlers.ConfiguredCarverList;
import databack.common.interop.modern_block.BlockIdentity;
import databack.common.interop.registry.BlockIdentityRegistry;
import databack.common.tags.BuiltinTagRegistries;
import databack.common.tags.ITag;
import databack.common.worldgen.aquifer.standin.AqAquifer;
import databack.common.worldgen.carver.standin.CvAquifer;
import databack.common.worldgen.carver.standin.CvBlock;
import databack.common.worldgen.carver.standin.CvBlockSet;
import databack.common.worldgen.carver.standin.CvBlockState;
import databack.common.worldgen.carver.standin.CvBlocks;
import databack.common.worldgen.carver.standin.CvChunk;
import databack.common.worldgen.carver.standin.CvChunkPos;
import databack.common.worldgen.carver.standin.CvContext;
import databack.common.worldgen.carver.standin.CvStatePool;

/// The carver step of a world: vanilla's carvers, configured from the world's datapacks, over 1.7 blocks. States are
/// keyed by block and meta ([BlockMeta]); `replaceable` tags are matched through each block's [BlockIdentity].
/// Thread-safe, though each chunk's carving runs on one thread.
public final class WorldCarvers {

    private static final Logger LOGGER = LogManager.getLogger("databack-carver");

    /// The biome a carving starting in a chunk takes its carvers from.
    @FunctionalInterface
    public interface StartBiome {

        @NotNull
        String at(int chunkX, int chunkZ);
    }

    /// The surface rule's block at one exposed block, or null.
    @FunctionalInterface
    public interface TopMaterial {

        @Nullable
        ImmutableBlockMeta at(int x, int y, int z, boolean underFluid);
    }

    private static final Map<CvBlock, Object> PAYLOADS;

    static {
        Map<CvBlock, Object> payloads = new HashMap<>();
        payloads.put(CvBlocks.AIR, new BlockMeta(Blocks.air, 0));
        payloads.put(CvBlocks.CAVE_AIR, new BlockMeta(Blocks.air, 0));
        payloads.put(CvBlocks.DIRT, new BlockMeta(Blocks.dirt, 0));
        payloads.put(CvBlocks.GRASS_BLOCK, new BlockMeta(Blocks.grass, 0));
        payloads.put(CvBlocks.MYCELIUM, new BlockMeta(Blocks.mycelium, 0));
        payloads.put(CvBlocks.WATER, new BlockMeta(Blocks.water, 0));
        payloads.put(CvBlocks.LAVA, new BlockMeta(Blocks.lava, 0));
        PAYLOADS = Collections.unmodifiableMap(payloads);
    }

    @NotNull
    private final VanillaCarvers vanilla;
    @NotNull
    private final CarverSystem system;
    @NotNull
    private final StartBiome startBiome;
    private final int minGenY, genDepth;
    private final Map<String, List<ConfiguredCarver>> byBiome = new ConcurrentHashMap<>();
    private final Map<String, ConfiguredCarver> byId = new ConcurrentHashMap<>();

    /// `minGenY` and `genDepth` are the dimension's range (for anchors and height providers).
    public WorldCarvers(
        @NotNull VanillaCarvers vanilla, long seed, @NotNull StartBiome startBiome, int minGenY, int genDepth
    ) {
        this.vanilla = vanilla;
        this.startBiome = startBiome;
        this.minGenY = minGenY;
        this.genDepth = genDepth;
        this.system = new CarverSystem(seed, (x, z) -> carversOf(startBiome.at(x, z)));
    }

    /// Carves chunk `(chunkX, chunkZ)` in place. `blocks` covers `layers` layers from `bottomY` at index
    /// `((y - bottomY) * 16 + z) * 16 + x`; air is [Blocks#air].
    public void carve(
        int chunkX, int chunkZ, @NotNull ImmutableBlockMeta[] blocks, int bottomY, int layers,
        @NotNull AqAquifer aquifer, @NotNull TopMaterial topMaterial
    ) {
        CvStatePool pool = new CvStatePool(PAYLOADS);
        CvBlockState[] states = new CvBlockState[blocks.length];
        // Runs of one block instance (stone, mostly) skip the pool lookup.
        ImmutableBlockMeta previous = null;
        for (int i = 0; i < blocks.length; i++) {
            states[i] = blocks[i] == previous ? states[i - 1] : pool.state(key(blocks[i]));
            previous = blocks[i];
        }

        CvChunk chunk = new CvChunk(new CvChunkPos(chunkX, chunkZ), bottomY, layers, states);
        CvContext context = new CvContext(minGenY, genDepth, (c, pos, underFluid) -> {
            ImmutableBlockMeta top = topMaterial.at(pos.getX(), pos.getY(), pos.getZ(), underFluid);
            return top == null ? null : pool.state(key(top));
        });
        system.carve(chunk, context, new CvAquifer(aquifer));

        for (int i = 0; i < blocks.length; i++) blocks[i] = (ImmutableBlockMeta) pool.payload(states[i]);
    }

    private static BlockMeta key(ImmutableBlockMeta block) {
        return block instanceof BlockMeta ? (BlockMeta) block : new BlockMeta(block.getBlock(), block.getBlockMeta());
    }

    /// The carvers of a biome (its `carvers` for the air step), built once.
    @NotNull
    private List<ConfiguredCarver> carversOf(@NotNull String biome) {
        return byBiome.computeIfAbsent(biome, id -> {
            DatapackBiome definition = BiomeList.RT.getHandler().getBiome(id);
            if (definition == null || definition.carvers == null) return Collections.emptyList();

            List<IConfiguredCarver> entries = definition.carvers.phases.get(CarveStep.air);
            if (entries == null) return Collections.emptyList();

            List<ConfiguredCarver> carvers = new ArrayList<>();
            for (IConfiguredCarver entry : entries) {
                ConfiguredCarver carver = entry.instantiate(buildContext());
                if (carver != null) carvers.add(carver);
            }
            return carvers;
        });
    }

    private CarverBuildContext buildContext() {
        return new CarverBuildContext(vanilla, WorldCarvers::blockSet, this::reference);
    }

    @Nullable
    private ConfiguredCarver reference(@NotNull String id) {
        ConfiguredCarver known = byId.get(id);
        if (known != null) return known;

        IConfiguredCarver definition = ConfiguredCarverList.RT.getHandler().getConfiguredCarver(id);
        if (definition == null) return null;

        ConfiguredCarver carver = definition.instantiate(buildContext());
        if (carver != null) byId.put(id, carver);
        return carver;
    }

    /// A `replaceable` value: block ids and `#tags`, a single one or a list. Membership is cached per block.
    private static CvBlockSet blockSet(@Nullable JsonElement replaceable) {
        List<Predicate<BlockIdentity>> entries = new ArrayList<>();
        if (replaceable != null) {
            if (replaceable.isJsonArray()) {
                for (JsonElement e : replaceable.getAsJsonArray()) entries.add(entry(e.getAsString()));
            } else {
                entries.add(entry(replaceable.getAsString()));
            }
        }

        Map<Object, Boolean> cache = new ConcurrentHashMap<>();
        return new CvBlockSet(new CvStatePool(PAYLOADS), payload -> cache.computeIfAbsent(payload, p -> {
            BlockMeta block = (BlockMeta) p;
            BlockIdentity identity = BlockIdentityRegistry.INSTANCE.getBlockIdentity(block.getBlock(),
                block.getBlockMeta());
            if (identity == null) return false;
            for (Predicate<BlockIdentity> entry : entries) {
                if (entry.test(identity)) return true;
            }
            return false;
        }));
    }

    private static Predicate<BlockIdentity> entry(String entry) {
        if (!entry.startsWith("#")) {
            ResourceLocation id = new ResourceLocation(entry);
            return identity -> identity.identityId.equals(id);
        }

        ResourceLocation tagId = new ResourceLocation(entry.substring(1));
        return identity -> {
            ITag<BlockIdentity> tag = BuiltinTagRegistries.blocks().getTag(tagId);
            if (tag == null) {
                LOGGER.warn("Unknown block tag #{} in a carver's replaceable blocks", tagId);
                return false;
            }
            return tag.includes(identity);
        };
    }
}
