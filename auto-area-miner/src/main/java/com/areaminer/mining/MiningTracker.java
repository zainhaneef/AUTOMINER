package com.areaminer.mining;

import com.areaminer.area.CellState;
import com.areaminer.area.QuarryModel;
import com.areaminer.config.MinerConfig;
import com.areaminer.scanner.CellClassifier;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;

/**
 * Follows ONE target. Nothing counts as mined until the world itself shows air:
 * every tick the whole pattern around the target is re-read, and each block that turns
 * from present to air is counted exactly once. After the centre breaks it waits a few ticks
 * for late server updates, then compares what really broke with what the profile expected.
 */
public final class MiningTracker {
    public enum Event { NONE, SETTLING, FINISHED, TIMED_OUT, SKIPPED }

    private final QuarryModel model;
    private final CellClassifier classifier;
    private final MiningStats stats;
    private final BlockPos.Mutable scratch = new BlockPos.Mutable();

    private BlockPos centre;
    private int[][] fp;
    private long startTick;
    private int attempts;
    private int settleLeft;
    private int expected;
    private int broken;
    private boolean centreBroken;
    private int lastExpected;
    private int lastBroken;
    private int abilityMisses;

    public MiningTracker(QuarryModel model, CellClassifier classifier, MiningStats stats) {
        this.model = model;
        this.classifier = classifier;
        this.stats = stats;
    }

    public void begin(BlockPos target, int[][] footprint, long tick) {
        centre = target;
        fp = footprint;
        startTick = tick;
        attempts = 0;
        settleLeft = -1;
        broken = 0;
        centreBroken = false;
        expected = 0;
        for (int[] o : fp) {
            int x = target.getX() + o[0];
            int y = target.getY() + o[1];
            int z = target.getZ() + o[2];
            if (model.q.contains(x, y, z) && model.stateAt(x, y, z) == CellState.REMAINING) {
                expected++;
            }
        }
    }

    public void cancel() {
        centre = null;
    }

    public boolean active() {
        return centre != null;
    }

    public int attempts() {
        return attempts;
    }

    public int lastExpected() {
        return lastExpected;
    }

    public int lastBroken() {
        return lastBroken;
    }

    public int abilityMisses() {
        return abilityMisses;
    }

    public Event tick(ClientWorld world, long tick, ItemStack tool, MinerConfig cfg) {
        if (centre == null) {
            return Event.NONE;
        }
        for (int[] o : fp) {
            int x = centre.getX() + o[0];
            int y = centre.getY() + o[1];
            int z = centre.getZ() + o[2];
            if (!model.q.contains(x, y, z)) {
                continue;
            }
            scratch.set(x, y, z);
            byte now = classifier.classify(world, scratch, tool);
            if (now == CellState.UNKNOWN) {
                continue; // chunk not loaded: never guess
            }
            boolean wasRemaining = model.stateAt(x, y, z) == CellState.REMAINING;
            if (model.observe(x, y, z, now)) {
                stats.record(tick);
                if (wasRemaining) {
                    broken++;
                }
                if (o[0] == 0 && o[1] == 0 && o[2] == 0) {
                    centreBroken = true;
                }
            }
        }

        if (centreBroken && settleLeft < 0) {
            settleLeft = cfg.settleTicks;
        }
        if (settleLeft >= 0) {
            if (settleLeft == 0) {
                finish();
                return Event.FINISHED;
            }
            settleLeft--;
            return Event.SETTLING;
        }

        if (tick - startTick > (long) cfg.targetTimeoutSeconds * 20L) {
            attempts++;
            if (attempts > cfg.maxRetries) {
                model.observe(centre.getX(), centre.getY(), centre.getZ(), CellState.SKIPPED);
                centre = null;
                return Event.SKIPPED;
            }
            startTick = tick;
            return Event.TIMED_OUT;
        }
        return Event.NONE;
    }

    private void finish() {
        lastExpected = expected;
        lastBroken = broken;
        if (expected > 1) {
            if (broken <= 1) {
                abilityMisses++;
            } else {
                abilityMisses = 0;
            }
        }
        centre = null;
    }
}
