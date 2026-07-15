package databack.common.interop.world_clock;

import net.minecraftforge.common.DimensionManager;

import databack.common.interop.WorldClock;

public class DimensionWorldClock implements WorldClock {

    public final int dimId;

    public DimensionWorldClock(int dimId) {
        this.dimId = dimId;
    }

    @Override
    public long getWorldTime() {
        return DimensionManager.getWorld(dimId).getWorldTime();
    }

    @Override
    public void setWorldTime(long time) {
        DimensionManager.getWorld(dimId).setWorldTime(time);
    }
}
