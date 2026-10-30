package com.areaminer.recovery;

import net.minecraft.util.math.Vec3d;

/** Counts consecutive ticks in which the player has not moved. Bounded by the caller's limit. */
public final class StuckDetector {
    private Vec3d last;
    private int stillTicks;

    public void reset(Vec3d pos) {
        last = pos;
        stillTicks = 0;
    }

    /** @return how many ticks the player has stayed (almost) in place. */
    public int update(Vec3d pos) {
        if (last == null || pos.squaredDistanceTo(last) >= 0.01) {
            last = pos;
            stillTicks = 0;
        } else {
            stillTicks++;
        }
        return stillTicks;
    }
}
