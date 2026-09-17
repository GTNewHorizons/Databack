package databack.common.debug;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import databack.common.debug.DebugOverlayRegistry.DisplayMode;

/**
 * Provides a human-readable title and description for a RenderGameOverlayEvent.Text handler,
 * displayed in the F3+F6 debug overlay options screen.
 *
 * Both values are lang keys resolved at display time.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface DebugOverlayEntry {

    /** Lang key for the short display name shown in the list. */
    String title();

    /** Lang key for the description shown in the tooltip. Empty string means no tooltip. */
    String description() default "";

    /** Default display mode for the annotated RenderGameOverlayEvent.Text handler. */
    DisplayMode defaultMode() default DisplayMode.IN_OVERLAY;
}
