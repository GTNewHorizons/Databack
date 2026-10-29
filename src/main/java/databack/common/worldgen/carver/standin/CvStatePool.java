package databack.common.worldgen.carver.standin;

import java.util.HashMap;
import java.util.Map;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/// The block states the carvers see, one instance per state. A state's payload is whatever the caller's chunk holds
/// (e.g. a block and meta); the blocks the carvers name ([CvBlocks]) have a payload each, and states of those payloads
/// are the blocks' default states. Not thread-safe.
@ApiStatus.Internal
public final class CvStatePool {

    private static final CvBlock OTHER = new CvBlock("databack:other", null);

    @NotNull
    private final Map<CvBlock, Object> payloads;
    private final Map<Object, CvBlockState> states = new HashMap<>();

    /// `payloads` gives the payload of every [CvBlocks] block (cave air may share air's).
    public CvStatePool(@NotNull Map<CvBlock, Object> payloads) {
        this.payloads = payloads;
        for (Map.Entry<CvBlock, Object> e : payloads.entrySet()) {
            if (e.getKey() != CvBlocks.CAVE_AIR) states.put(e.getValue(), e.getKey().defaultBlockState());
        }
    }

    /// The state of `payload`.
    @NotNull
    public CvBlockState state(@NotNull Object payload) {
        CvBlockState state = states.get(payload);
        if (state == null) {
            state = new CvBlockState(OTHER, payload);
            states.put(payload, state);
        }
        return state;
    }

    /// The payload of `state`.
    @NotNull
    public Object payload(@NotNull CvBlockState state) {
        if (state.payload != null) return state.payload;
        Object payload = payloads.get(state.block);
        if (payload == null) throw new IllegalStateException("No payload for " + state.block);
        return payload;
    }
}
