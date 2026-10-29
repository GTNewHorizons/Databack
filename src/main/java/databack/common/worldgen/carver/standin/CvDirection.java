package databack.common.worldgen.carver.standin;

import org.jetbrains.annotations.ApiStatus;

/// Stand-in for the direction constants the carvers name.
@ApiStatus.Internal
public final class CvDirection {

    public static final CvDirection DOWN = new CvDirection(0, -1, 0);

    final int stepX, stepY, stepZ;

    private CvDirection(int stepX, int stepY, int stepZ) {
        this.stepX = stepX;
        this.stepY = stepY;
        this.stepZ = stepZ;
    }
}
