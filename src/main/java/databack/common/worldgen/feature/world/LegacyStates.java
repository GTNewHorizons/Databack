package databack.common.worldgen.feature.world;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.block.Block;
import net.minecraft.util.ResourceLocation;

import org.jetbrains.annotations.NotNull;

import com.gtnewhorizon.gtnhlib.blockstate.core.BlockState;
import com.gtnewhorizon.gtnhlib.blockstate.core.MetaBlockProperty;
import com.gtnewhorizon.gtnhlib.blockstate.registry.BlockPropertyRegistry;
import com.gtnewhorizon.gtnhlib.util.data.BlockMeta;
import com.gtnewhorizon.gtnhlib.util.data.ImmutableBlockMeta;

import databack.common.annotation.ThreadSafe;
import databack.common.dto.BlockStateDTO;
import databack.common.interop.PropertyTranslator;
import databack.common.interop.modern_block.BlockIdentity;
import databack.common.interop.modern_block.StateForms;
import databack.common.interop.registry.BlockIdentityRegistry;
import databack.common.mixinext.BlockExt_Identity;
import databack.common.serde.MiscAdapters;
import databack.common.worldgen.feature.standin.FtBlock;
import databack.common.worldgen.feature.standin.FtBlockState;

/// Converts between the features' modern block states and 1.7 blocks, through [BlockIdentityRegistry]. Both ways
/// are cached; create one per generator, after the block identities are registered.
@ThreadSafe
public final class LegacyStates {

    private final ConcurrentHashMap<FtBlockState, ImmutableBlockMeta> legacy = new ConcurrentHashMap<>();
    /// By `block id << 4 | meta`.
    private final ConcurrentHashMap<Integer, FtBlockState> modern = new ConcurrentHashMap<>();

    /// The 1.7 block and meta for `state`, as [MiscAdapters#resolveBlockState] gives it (air for unknown blocks).
    @NotNull
    public ImmutableBlockMeta toLegacy(@NotNull FtBlockState state) {
        return legacy.computeIfAbsent(state, LegacyStates::resolve);
    }

    private static ImmutableBlockMeta resolve(FtBlockState state) {
        BlockStateDTO dto = new BlockStateDTO();
        dto.Name = new ResourceLocation(state.getBlock().id);
        dto.Properties = state.properties().isEmpty() ? null : new HashMap<>(state.properties());

        BlockState resolved = MiscAdapters.resolveBlockState(dto);
        return new BlockMeta(resolved.getBlock(), resolved.getBlockMeta(0));
    }

    /// The modern state of a 1.7 block: its identity's block, with the properties GTNHLib reads from the meta
    /// translated back ([PropertyTranslator#untranslate]), and the property a state form stands for. Blocks without an
    /// identity keep their 1.7 registry name, without properties.
    @NotNull
    public FtBlockState toModern(@NotNull Block block, int meta) {
        return modern.computeIfAbsent(Block.getIdFromBlock(block) << 4 | meta & 15, key -> identify(block, meta));
    }

    private static FtBlockState identify(Block block, int meta) {
        BlockIdentity identity = BlockIdentityRegistry.INSTANCE.getBlockIdentity(block, meta);
        if (identity == null) return FtBlock.of(Block.blockRegistry.getNameForObject(block)).defaultBlockState();

        Map<String, String> properties = properties(block, meta);
        StateForms.Form form = BlockIdentityRegistry.INSTANCE.stateFormOf(identity);
        if (form != null) properties.put(form.property, form.value);

        return FtBlockState.of(FtBlock.of(identity.identityId.toString()), properties);
    }

    /// The block's GTNHLib properties in modern terms, except its variant, which its identity already is.
    private static Map<String, String> properties(Block block, int meta) {
        MetaBlockProperty<?> variant = ((BlockExt_Identity) block).db$getVariantProperty();

        Map<String, String> properties = new HashMap<>();
        try (BlockState legacy = BlockPropertyRegistry.getBlockState(block, meta)) {
            for (Map.Entry<String, String> e : legacy.toMap().entrySet()) {
                if (variant != null && variant.getName().equals(e.getKey())) continue;

                String[] modern = PropertyTranslator.untranslate(block, e.getKey(), e.getValue());
                if (modern != null) properties.put(modern[0], modern[1]);
            }
        }
        return properties;
    }
}
