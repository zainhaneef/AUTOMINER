package com.areaminer.mining;

import java.util.ArrayDeque;

/** Counts verified removals and derives a rolling blocks-per-minute figure. */
public final class MiningStats {
    private static final int WINDOW_TICKS = 1200; // 60 s
    private final ArrayDeque<Long> times = new ArrayDeque<>();
    private long total;

    public void record(long tick) {
        total++;
        times.addLast(tick);
        trim(tick);
        while (times.size() > 20000) {
            times.pollFirst();
        }
    }

    private void trim(long now) {
        while (!times.isEmpty() && now - times.peekFirst() > WINDOW_TICKS) {
            times.pollFirst();
        }
    }

    public double blocksPerMinute(long now) {
        trim(now);
        return times.size();
    }

    public long total() {
        return total;
    }

    public void reset() {
        times.clear();
        total = 0;
    }
}
