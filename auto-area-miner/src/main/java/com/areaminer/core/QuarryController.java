package com.areaminer.core;

import com.areaminer.area.CellState;
import com.areaminer.area.LayerData;
import com.areaminer.area.Quarry;
import com.areaminer.area.QuarryModel;
import com.areaminer.config.MinerConfig;
import com.areaminer.inventory.InventoryMonitor;
import com.areaminer.mining.MiningStats;
import com.areaminer.mining.MiningTracker;
import com.areaminer.movement.ReachGuide;
import com.areaminer.pathfinding.LayerPlan;
import com.areaminer.pickaxe.Calibrator;
import com.areaminer.pickaxe.PickaxeProfile;
import com.areaminer.pickaxe.PickaxeProfiles;
import com.areaminer.recovery.StuckDetector;
import com.areaminer.scanner.CellClassifier;
import com.areaminer.scanner.CellSweeper;
import com.areaminer.scanner.QuarryScanner;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Owns the state machine. Manual-confirmation mode: it never moves, aims or clicks for the player.
 * It scans, plans, points at the next block, and counts only what the world confirms.
 * Everything runs on the client thread, in small per-tick slices.
 */
public final class QuarryController {
    public enum Level { INFO, WARN, ERROR }

    public record Note(String text, Level level, long expireTick) { }

    private BlockPos pos1;
    private BlockPos pos2;
    private Quarry quarry;

    private MinerState state = MinerState.IDLE;
    private String pauseReason = "";
    private boolean autoPause;
    private boolean waitingForSingle;
    private String singleReason = "";

    private QuarryModel model;
    private QuarryScanner scanner;
    private CellClassifier classifier;
    private CellSweeper sweeper;
    private LayerPlan plan;
    private PickaxeProfile planProfile;
    private MiningTracker tracker;
    private final MiningStats stats = new MiningStats();
    private final StuckDetector stuck = new StuckDetector();
    private final Calibrator calibrator = new Calibrator();

    private int layer;
    private BlockPos target;
    private String hint = "";
    private PickaxeProfile profile;
    private String profileOverride = "auto";
    private long tick;
    private long recoverUntil;
    private int replans;
    private int remainingAtReplan = Integer.MAX_VALUE;
    private int chunkWait;
    private String errorMessage = "";
    private final ArrayDeque<Note> notes = new ArrayDeque<>();

    // ------------------------------------------------------------------ selection

    public void setPos(int which, BlockPos p) {
        if (isActive()) {
            stop();
        }
        if (which == 1) {
            pos1 = p.toImmutable();
        } else {
            pos2 = p.toImmutable();
        }
        quarry = null;
        if (pos1 != null && pos2 != null) {
            quarry = new Quarry(pos1, pos2, currentDimension());
        }
    }

    public void loadQuarry(Quarry q) {
        stop();
        quarry = q;
        pos1 = new BlockPos(q.minX, q.minY, q.minZ);
        pos2 = new BlockPos(q.maxX, q.maxY, q.maxZ);
    }

    public void clearSelection() {
        stop();
        pos1 = null;
        pos2 = null;
        quarry = null;
    }

    private static String currentDimension() {
        ClientWorld w = MinecraftClient.getInstance().world;
        return w == null ? "unknown" : w.getRegistryKey().getValue().toString();
    }

    // ------------------------------------------------------------------ lifecycle

    public boolean isActive() {
        return state != MinerState.IDLE && state != MinerState.COMPLETED && state != MinerState.ERROR;
    }

    /** @return an error message, or null when started. */
    public String start(MinecraftClient mc) {
        if (mc.world == null || mc.player == null) {
            return "Not in a world.";
        }
        if (quarry == null) {
            return "Set both corners first (/miner pos1 and /miner pos2).";
        }
        if (isActive()) {
            return "Already running (" + state + ").";
        }
        MinerConfig cfg = MinerConfig.get();
        if (quarry.volume() > cfg.maxVolume) {
            return "Selection has " + String.format("%,d", quarry.volume()) + " blocks; the limit is "
                    + String.format("%,d", cfg.maxVolume) + " (maxVolume in config/areaminer.json).";
        }
        if (!quarry.dimension.equals(currentDimension())) {
            return "This quarry was selected in " + quarry.dimension + ".";
        }
        resetRuntime();
        model = new QuarryModel(quarry);
        classifier = new CellClassifier(cfg);
        scanner = new QuarryScanner(model, classifier);
        sweeper = new CellSweeper(model, classifier);
        tracker = new MiningTracker(model, classifier, stats);
        state = MinerState.SCANNING;
        notify(Level.INFO, "Scanning " + quarry.describe() + "...", false);
        return null;
    }

    public void pause(String reason, boolean auto) {
        if (!isActive() || state == MinerState.PAUSED || state == MinerState.SCANNING) {
            return;
        }
        if (tracker != null) {
            tracker.cancel();
        }
        target = null;
        state = MinerState.PAUSED;
        pauseReason = reason;
        autoPause = auto;
        notify(Level.WARN, "Paused: " + reason, true);
    }

    public void resume() {
        if (state != MinerState.PAUSED) {
            return;
        }
        state = plan == null ? MinerState.PLANNING : MinerState.MOVING;
        pauseReason = "";
        autoPause = false;
        target = null;
        notify(Level.INFO, "Resumed.", false);
    }

    public void stop() {
        resetRuntime();
        state = MinerState.IDLE;
    }

    public void onDisconnect() {
        if (isActive()) {
            resetRuntime();
            state = MinerState.IDLE;
        }
        calibrator.cancel();
        notes.clear();
    }

    public void failFromException(Throwable t) {
        resetRuntime();
        state = MinerState.ERROR;
        errorMessage = t.getClass().getSimpleName() + ": " + t.getMessage();
        notify(Level.ERROR, "Internal error, stopped safely: " + errorMessage, true);
    }

    private void fail(String msg) {
        resetRuntime();
        state = MinerState.ERROR;
        errorMessage = msg;
        notify(Level.ERROR, msg, true);
    }

    private void complete() {
        long left = model.remainingTotal();
        long blocked = model.blockedTotal();
        long skipped = model.skippedTotal();
        target = null;
        plan = null;
        state = MinerState.COMPLETED;
        String extra = "";
        if (blocked > 0) {
            extra += ", " + blocked + " need a better tool";
        }
        if (skipped > 0) {
            extra += ", " + skipped + " skipped (/miner retry)";
        }
        if (left > 0) {
            extra += ", " + left + " left";
        }
        notify(Level.INFO, "Quarry finished: " + String.format("%,d", model.cleared) + " blocks cleared" + extra + ".", true);
    }

    private void resetRuntime() {
        model = null;
        scanner = null;
        sweeper = null;
        classifier = null;
        plan = null;
        planProfile = null;
        tracker = null;
        target = null;
        layer = 0;
        replans = 0;
        remainingAtReplan = Integer.MAX_VALUE;
        chunkWait = 0;
        waitingForSingle = false;
        pauseReason = "";
        autoPause = false;
        hint = "";
        errorMessage = "";
        stats.reset();
    }

    /** @return number of skipped cells that were restored. */
    public int retrySkipped() {
        if (model == null) {
            return 0;
        }
        int n = 0;
        for (LayerData d : model.layers) {
            for (int i = 0; i < d.size(); i++) {
                if (d.get(i) == CellState.SKIPPED) {
                    d.set(i, CellState.REMAINING);
                    n++;
                }
            }
        }
        if (n > 0) {
            plan = null;
            target = null;
            replans = 0;
            remainingAtReplan = Integer.MAX_VALUE;
            if (state == MinerState.COMPLETED) {
                layer = -1;
                if (!advanceLayer()) {
                    complete();
                }
            } else if (state != MinerState.PAUSED && isActive() && state != MinerState.SCANNING) {
                state = MinerState.PLANNING;
            }
        }
        return n;
    }

    // ------------------------------------------------------------------ per-tick

    public void tick(MinecraftClient mc) {
        tick++;
        ClientWorld w = mc.world;
        ClientPlayerEntity p = mc.player;
        if (w == null || p == null) {
            return;
        }
        calibrator.tick(mc);
        while (!notes.isEmpty() && notes.peekFirst().expireTick() < tick) {
            notes.pollFirst();
        }
        if (!isActive()) {
            return;
        }
        MinerConfig cfg = MinerConfig.get();
        if (!w.getRegistryKey().getValue().toString().equals(quarry.dimension)) {
            fail("Dimension changed - stopped.");
            return;
        }

        ItemStack held = p.getMainHandStack();
        profile = PickaxeProfiles.resolve(held, profileOverride);
        ItemStack tool = PickaxeProfiles.isMiningTool(held) ? held : null;

        if (state == MinerState.PAUSED) {
            if (!autoPause) {
                return;
            }
            String why = conditionProblem(p, held, true, cfg);
            if (why != null) {
                pauseReason = why;
                return;
            }
            resume();
            return;
        }
        String why = conditionProblem(p, held, false, cfg);
        if (why != null) {
            pause(why, true);
            return;
        }

        switch (state) {
            case SCANNING -> {
                if (scanner.step(w, tool, cfg.scanBudgetPerTick)) {
                    layer = -1;
                    if (!advanceLayer()) {
                        complete();
                    }
                }
            }
            case PLANNING -> planStep(cfg);
            default -> activeStep(mc, w, p, tool, cfg);
        }
    }

    /** Returns a reason to pause, or null when everything is fine. */
    private String conditionProblem(ClientPlayerEntity p, ItemStack held, boolean resuming, MinerConfig cfg) {
        if (!p.isAlive()) {
            return "You died - it resumes after you respawn.";
        }
        int free = InventoryMonitor.freeSlots(p);
        int need = resuming ? cfg.minFreeSlots + 2 : cfg.minFreeSlots;
        if (cfg.pauseOnInventoryFull && free < need) {
            return "Inventory full (" + free + " free slot(s)). Empty some space.";
        }
        if (cfg.pauseOnLowDurability && PickaxeProfiles.isMiningTool(held)) {
            int left = InventoryMonitor.usesLeft(held);
            if (left <= cfg.lowDurabilityUses) {
                return "Pickaxe durability low (" + left + " uses left). Swap or repair it.";
            }
        }
        if (waitingForSingle) {
            if (profile != null && !profile.isMultiBlock()) {
                waitingForSingle = false;
                plan = null;
                target = null;
            } else {
                return singleReason;
            }
        }
        return null;
    }

    private boolean advanceLayer() {
        for (int l = layer + 1; l < model.layers.length; l++) {
            LayerData d = model.layers[l];
            if (d.remaining > 0 || d.unknown > 0) {
                layer = l;
                plan = null;
                target = null;
                replans = 0;
                remainingAtReplan = Integer.MAX_VALUE;
                sweeper.setLayer(l);
                state = MinerState.PLANNING;
                return true;
            }
        }
        return false;
    }

    private void planStep(MinerConfig cfg) {
        if (plan == null) {
            planProfile = profile != null ? profile : PickaxeProfiles.normal();
            plan = new LayerPlan(model, layer, planProfile.footprint(), cfg.allowOvershoot);
            sweeper.setLayer(layer);
        }
        if (plan.step(cfg.planBudgetPerTick)) {
            state = MinerState.MOVING;
            target = null;
        }
    }

    private void activeStep(MinecraftClient mc, ClientWorld w, ClientPlayerEntity p, ItemStack tool, MinerConfig cfg) {
        if (profile != null && planProfile != null
                && !Arrays.deepEquals(profile.footprint(), planProfile.footprint())) {
            tracker.cancel();
            plan = null;
            target = null;
            state = MinerState.PLANNING;
            notify(Level.INFO, "Pickaxe is now " + profile.label() + " - replanning this layer.", false);
            return;
        }

        sweeper.setLayer(layer);
        sweeper.step(w, tool, cfg.sweepBudget, tick, stats);

        LayerData d = model.layers[layer];
        if (!tracker.active() && d.remaining == 0 && d.unknown == 0 && d.confirmedClear) {
            notify(Level.INFO, "Layer " + (layer + 1) + "/" + model.layers.length + " complete.", true);
            if (!advanceLayer()) {
                complete();
            }
            return;
        }

        if (target != null) {
            MiningTracker.Event ev = tracker.tick(w, tick, tool, cfg);
            switch (ev) {
                case SETTLING -> {
                    state = MinerState.VERIFYING;
                    hint = "Block broke - confirming with the server...";
                    return;
                }
                case FINISHED -> onTargetFinished();
                case TIMED_OUT -> {
                    state = MinerState.RECOVERING;
                    recoverUntil = tick + 40;
                    notify(Level.WARN, "Block not breaking (attempt " + tracker.attempts() + "/" + cfg.maxRetries
                            + "). Check your tool, reach and aim.", false);
                }
                case SKIPPED -> {
                    notify(Level.WARN, "Skipped a block that would not break. /miner retry restores skipped blocks.", false);
                    target = null;
                }
                case NONE -> { }
            }
        }

        if (target == null) {
            pickTarget(p, cfg);
            if (target == null) {
                return;
            }
        }
        if (state == MinerState.RECOVERING && tick < recoverUntil) {
            return;
        }
        guidance(mc, w, p, cfg);
    }

    private void onTargetFinished() {
        target = null;
        int expected = tracker.lastExpected();
        int broken = tracker.lastBroken();
        if (expected > 1) {
            if (tracker.abilityMisses() >= 2) {
                notify(Level.WARN, "The special ability doesn't seem to trigger (cooldown, not activated, or wrong profile). "
                        + "Only single blocks are breaking. Try /miner calibrate.", false);
            } else if (broken > 1 && broken < expected) {
                notify(Level.INFO, "Partial multi-block break: " + broken + " of " + expected + " expected. Replanning around it.", false);
            }
        }
    }

    private void pickTarget(ClientPlayerEntity p, MinerConfig cfg) {
        LayerData d = model.layers[layer];
        plan.pruneHead();
        List<Integer> up = plan.upcoming(6);
        if (up.isEmpty()) {
            if (d.remaining > 0) {
                handleExhaustedPlan(d, cfg);
            } else {
                hint = "Layer looks clear - double-checking...";
            }
            return;
        }
        Vec3d eye = p.getEyePos();
        int chosen = up.get(0);
        if (ReachGuide.distance(eye, cellPos(chosen)) > cfg.reach) {
            for (int i = 1; i < up.size(); i++) {
                if (ReachGuide.distance(eye, cellPos(up.get(i))) <= cfg.reach) {
                    chosen = up.get(i);
                    break;
                }
            }
        }
        target = cellPos(chosen);
        tracker.begin(target, planProfile.footprint(), tick);
        stuck.reset(posOf(p));
    }

    private static Vec3d posOf(ClientPlayerEntity p) {
        return new Vec3d(p.getX(), p.getY(), p.getZ());
    }

    private BlockPos cellPos(int idx) {
        Quarry q = model.q;
        return new BlockPos(q.cellX(idx), q.yOfLayer(layer), q.cellZ(idx));
    }

    private void handleExhaustedPlan(LayerData d, MinerConfig cfg) {
        int leftovers = plan.leftoverRemaining();
        if (leftovers > 0 && planProfile.isMultiBlock() && !cfg.allowOvershoot && leftovers >= d.remaining) {
            waitingForSingle = true;
            singleReason = "Swap to a normal 1-block pickaxe for " + leftovers + " edge block(s): the "
                    + planProfile.displayName + " would break blocks outside your quarry "
                    + "(or set allowOvershoot in the config).";
            pause(singleReason, true);
            return;
        }
        if (d.remaining < remainingAtReplan) {
            replans = 0;
        }
        remainingAtReplan = d.remaining;
        if (replans >= cfg.maxReplans) {
            int n = 0;
            for (int i = 0; i < d.size(); i++) {
                if (d.get(i) == CellState.REMAINING) {
                    d.set(i, CellState.SKIPPED);
                    n++;
                }
            }
            notify(Level.WARN, "Gave up on " + n + " block(s) in layer " + (layer + 1)
                    + " after " + cfg.maxReplans + " replans. /miner retry restores them.", true);
            return;
        }
        replans++;
        plan = null;
        state = MinerState.PLANNING;
    }

    private void guidance(MinecraftClient mc, ClientWorld w, ClientPlayerEntity p, MinerConfig cfg) {
        if (!w.getChunkManager().isChunkLoaded(target.getX() >> 4, target.getZ() >> 4)) {
            chunkWait++;
            state = MinerState.RECOVERING;
            hint = "Waiting for the target chunk to load...";
            if (chunkWait > 600) {
                chunkWait = 0;
                pause("Chunks near the target aren't loading (lag or connection problem?). Use /miner resume when it recovers.", false);
            }
            return;
        }
        chunkWait = 0;
        Vec3d eye = p.getEyePos();
        double dist = ReachGuide.distance(eye, target);
        if (dist > cfg.reach) {
            state = MinerState.MOVING;
            hint = ReachGuide.moveHint(eye, target, dist, cfg.reach);
            if (stuck.update(posOf(p)) >= 600) {
                model.observe(target.getX(), target.getY(), target.getZ(), CellState.SKIPPED);
                tracker.cancel();
                target = null;
                notify(Level.WARN, "Target out of reach for 30 s - skipped it. /miner retry restores skipped blocks.", false);
            }
        } else {
            stuck.reset(posOf(p));
            BlockHitResult hit = ReachGuide.blockHit(mc);
            if (hit == null || !hit.getBlockPos().equals(target)) {
                state = MinerState.POSITIONING;
                hint = "In reach - aim at the highlighted block.";
            } else {
                state = MinerState.MINING;
                hint = ReachGuide.mineHint(hit, planProfile);
            }
        }
    }

    // ------------------------------------------------------------------ notifications

    public void notify(Level level, String text, boolean chat) {
        MinerConfig cfg = MinerConfig.get();
        notes.addLast(new Note(text, level, tick + cfg.notificationSeconds * 20L));
        while (notes.size() > 5) {
            notes.pollFirst();
        }
        if (chat) {
            ClientPlayerEntity p = MinecraftClient.getInstance().player;
            if (p != null) {
                p.sendMessage(Text.literal("[Miner] " + text), false);
            }
        }
    }

    public void warn(String text) {
        notify(Level.WARN, text, true);
    }

    // ------------------------------------------------------------------ profile selection

    public String profileOverride() {
        return profileOverride;
    }

    public void setProfileOverride(String id) {
        profileOverride = id;
    }

    public void cycleProfile() {
        List<String> ids = new ArrayList<>();
        ids.add("auto");
        for (PickaxeProfile pr : PickaxeProfiles.all()) {
            ids.add(pr.id);
        }
        int i = ids.indexOf(profileOverride);
        profileOverride = ids.get((i + 1) % ids.size());
    }

    // ------------------------------------------------------------------ getters for UI / render

    public MinerState state() {
        return state;
    }

    public String pauseReason() {
        return pauseReason;
    }

    public Quarry quarry() {
        return quarry;
    }

    public BlockPos pos1() {
        return pos1;
    }

    public BlockPos pos2() {
        return pos2;
    }

    public QuarryModel model() {
        return model;
    }

    public int layer() {
        return layer;
    }

    public BlockPos target() {
        return target;
    }

    public String hint() {
        return hint;
    }

    public PickaxeProfile profile() {
        return profile;
    }

    public PickaxeProfile planProfile() {
        return planProfile;
    }

    public MiningStats stats() {
        return stats;
    }

    public long now() {
        return tick;
    }

    public Calibrator calibrator() {
        return calibrator;
    }

    public List<Note> notes() {
        return new ArrayList<>(notes);
    }

    public int[][] planFootprint() {
        return planProfile != null ? planProfile.footprint() : new int[][]{{0, 0, 0}};
    }

    public List<BlockPos> upcomingTargets(int n) {
        List<BlockPos> out = new ArrayList<>();
        if (plan != null && model != null && isActive() && state != MinerState.PAUSED) {
            for (int idx : plan.upcoming(n)) {
                out.add(cellPos(idx));
            }
        }
        return out;
    }

    public List<String> statusLines() {
        List<String> out = new ArrayList<>();
        out.add("State: " + state + (state == MinerState.PAUSED ? " - " + pauseReason : "")
                + (state == MinerState.ERROR ? " - " + errorMessage : ""));
        if (quarry == null) {
            out.add("Quarry: not set. Use /miner pos1 and /miner pos2.");
            return out;
        }
        out.add("Quarry: " + quarry.describe());
        if (model != null) {
            out.add("Layer: " + Math.max(1, layer + 1) + " / " + model.layers.length
                    + (layer >= 0 && layer < model.layers.length ? "  (y = " + quarry.yOfLayer(layer) + ")" : ""));
            out.add(String.format("Mined: %,d    Remaining: %,d    Progress: %.1f%%",
                    model.cleared, model.remainingTotal(), model.percent()));
            if (model.blockedTotal() > 0 || model.skippedTotal() > 0 || model.unknownTotal() > 0) {
                out.add("Blocked (wrong tool): " + model.blockedTotal() + "   Skipped: " + model.skippedTotal()
                        + "   Not loaded/unscanned: " + model.unknownTotal());
            }
            out.add(String.format("Speed: %.0f blocks/min (last 60 s)", stats.blocksPerMinute(tick)));
        }
        PickaxeProfile pr = profile;
        out.add("Pickaxe profile: " + (pr != null ? pr.label() : "none held") + "   (mode: " + profileOverride + ")");
        if (target != null) {
            out.add("Target: " + target.getX() + " " + target.getY() + " " + target.getZ());
        }
        if (!hint.isEmpty() && isActive()) {
            out.add("Hint: " + hint);
        }
        return out;
    }

    public double scanProgress() {
        return scanner == null ? 1.0 : scanner.progress();
    }
}
