package databack.common.dto;

import org.jetbrains.annotations.Nullable;

/** A sound event reference: either a plain ID string or an inline definition with an optional range. */
public class SoundEventRef {

    /** The sound event ID. Set for both string and struct forms. */
    public String sound_id;

    /** Range in blocks. Only present for the inline struct form. */
    @Nullable
    public Float range;

}
