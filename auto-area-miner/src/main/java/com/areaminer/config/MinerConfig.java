package com.areaminer.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** User-editable settings, stored in config/areaminer.json. */
public final class MinerConfig {
    private static final Logger LOG = LoggerFactory.getLogger("AreaMiner");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static MinerConfig instance = new MinerConfig();

    // ---- performance / scan limits ----
    public int scanBudgetPerTick = 3000;   // cells classified per tick during the initial scan
    public int sweepBudget = 400;          // cells re-checked per tick on the current layer
    public int planBudgetPerTick = 2000;   // cells processed per tick while planning a layer
    public long maxVolume = 2_000_000L;    // refuse selections larger than this

    // ---- guidance / verification ----
    public double reach = 4.5;             // assumed block reach (blocks)
    public int settleTicks = 8;            // wait this long after the centre breaks before judging the result
    public int targetTimeoutSeconds = 45;  // no break within this time = one failed attempt
    public int maxRetries = 3;             // failed attempts before a target is skipped
    public int maxReplans = 3;             // replans without progress before leftovers are skipped
    public boolean allowOvershoot = false; // allow multi-block centres whose pattern leaves the quarry

    // ---- inventory / tools ----
    public boolean pauseOnInventoryFull = true;
    public int minFreeSlots = 1;
    public boolean pauseOnLowDurability = true;
    public int lowDurabilityUses = 30;
    public List<String> ignoredBlocks = new ArrayList<>(); // e.g. "minecraft:obsidian"

    // ---- display ----
    public boolean hud = true;
    public boolean worldOverlay = true;
    public int hudX = 6;
    public int hudY = 6;
    public int mapMaxCells = 48;
    public int mapCellPx = 3;
    public int notificationSeconds = 8;

    public static MinerConfig get() {
        return instance;
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("areaminer.json");
    }

    public static void load() {
        Path f = file();
        try {
            if (Files.exists(f)) {
                MinerConfig c = GSON.fromJson(Files.readString(f), MinerConfig.class);
                if (c != null) {
                    instance = c;
                }
            }
        } catch (Exception e) {
            LOG.warn("Could not read {} - using defaults", f, e);
            instance = new MinerConfig();
        }
        instance.sanitize();
        save();
    }

    public static void save() {
        try {
            Files.createDirectories(file().getParent());
            Files.writeString(file(), GSON.toJson(instance));
        } catch (IOException e) {
            LOG.warn("Could not write config", e);
        }
    }

    private void sanitize() {
        scanBudgetPerTick = Math.max(100, scanBudgetPerTick);
        sweepBudget = Math.max(50, sweepBudget);
        planBudgetPerTick = Math.max(100, planBudgetPerTick);
        maxVolume = Math.max(1000L, maxVolume);
        reach = Math.max(2.0, Math.min(6.0, reach));
        settleTicks = Math.max(2, settleTicks);
        targetTimeoutSeconds = Math.max(5, targetTimeoutSeconds);
        maxRetries = Math.max(0, maxRetries);
        maxReplans = Math.max(1, maxReplans);
        minFreeSlots = Math.max(0, minFreeSlots);
        lowDurabilityUses = Math.max(0, lowDurabilityUses);
        mapMaxCells = Math.max(8, Math.min(96, mapMaxCells));
        mapCellPx = Math.max(1, Math.min(6, mapCellPx));
        notificationSeconds = Math.max(2, notificationSeconds);
        if (ignoredBlocks == null) {
            ignoredBlocks = new ArrayList<>();
        }
    }
}
