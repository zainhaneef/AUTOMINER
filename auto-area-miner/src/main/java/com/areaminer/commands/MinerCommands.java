package com.areaminer.commands;

import com.areaminer.AreaMinerClient;
import com.areaminer.area.Quarry;
import com.areaminer.config.MinerConfig;
import com.areaminer.core.QuarryController;
import com.areaminer.core.MinerState;
import com.areaminer.gui.ScreenOpener;
import com.areaminer.movement.ReachGuide;
import com.areaminer.pickaxe.PickaxeProfile;
import com.areaminer.pickaxe.PickaxeProfiles;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;

import java.io.IOException;
import java.util.List;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

/** All /miner client commands. */
public final class MinerCommands {
    private MinerCommands() {
    }

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(build()));
    }

    private static QuarryController ctl() {
        return AreaMinerClient.controller();
    }

    private static void say(CommandContext<FabricClientCommandSource> c, String msg) {
        c.getSource().sendFeedback(Text.literal(msg));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> build() {
        return literal("miner")
                .then(literal("pos1").executes(c -> setPos(c, 1)))
                .then(literal("pos2").executes(c -> setPos(c, 2)))
                .then(literal("start").executes(MinerCommands::start))
                .then(literal("pause").executes(c -> {
                    ctl().pause("Paused by you.", false);
                    return Command.SINGLE_SUCCESS;
                }))
                .then(literal("resume").executes(c -> {
                    ctl().resume();
                    return Command.SINGLE_SUCCESS;
                }))
                .then(literal("stop").executes(c -> {
                    ctl().stop();
                    say(c, "Stopped. Your selection is kept (/miner clear removes it).");
                    return Command.SINGLE_SUCCESS;
                }))
                .then(literal("status").executes(c -> {
                    for (String line : ctl().statusLines()) {
                        say(c, line);
                    }
                    return Command.SINGLE_SUCCESS;
                }))
                .then(literal("clear").executes(c -> {
                    ctl().clearSelection();
                    say(c, "Selection cleared.");
                    return Command.SINGLE_SUCCESS;
                }))
                .then(literal("save").then(argument("name", StringArgumentType.word()).executes(MinerCommands::save)))
                .then(literal("load").then(argument("name", StringArgumentType.word()).executes(MinerCommands::load)))
                .then(literal("list").executes(c -> {
                    List<String> names = Quarry.listSaved();
                    say(c, names.isEmpty() ? "No saved quarries." : "Saved quarries: " + String.join(", ", names));
                    return Command.SINGLE_SUCCESS;
                }))
                .then(literal("profile")
                        .executes(MinerCommands::listProfiles)
                        .then(argument("id", StringArgumentType.word()).executes(MinerCommands::setProfile)))
                .then(literal("calibrate")
                        .executes(MinerCommands::calibrate)
                        .then(literal("save").then(argument("id", StringArgumentType.word()).executes(MinerCommands::calibrateSave))))
                .then(literal("retry").executes(c -> {
                    int n = ctl().retrySkipped();
                    say(c, n + " skipped block(s) restored.");
                    return Command.SINGLE_SUCCESS;
                }))
                .then(literal("rescan").executes(c -> {
                    ctl().stop();
                    String err = ctl().start(c.getSource().getClient());
                    say(c, err == null ? "Rescanning..." : err);
                    return Command.SINGLE_SUCCESS;
                }))
                .then(literal("gui").executes(c -> {
                    ScreenOpener.request();
                    return Command.SINGLE_SUCCESS;
                }))
                .then(literal("overlay").executes(c -> {
                    MinerConfig cfg = MinerConfig.get();
                    cfg.worldOverlay = !cfg.worldOverlay;
                    MinerConfig.save();
                    say(c, "In-world overlay " + (cfg.worldOverlay ? "on" : "off") + ".");
                    return Command.SINGLE_SUCCESS;
                }))
                .then(literal("reload").executes(c -> {
                    MinerConfig.load();
                    PickaxeProfiles.load();
                    say(c, "Config and pickaxe profiles reloaded.");
                    return Command.SINGLE_SUCCESS;
                }));
    }

    private static int setPos(CommandContext<FabricClientCommandSource> c, int which) {
        MinecraftClient mc = c.getSource().getClient();
        BlockPos p;
        BlockHitResult hit = ReachGuide.blockHit(mc);
        if (hit != null) {
            p = hit.getBlockPos();
        } else {
            p = c.getSource().getPlayer().getBlockPos();
        }
        ctl().setPos(which, p);
        say(c, "Corner " + which + " = " + p.getX() + " " + p.getY() + " " + p.getZ()
                + (hit != null ? " (block you are looking at)" : " (your position)"));
        Quarry q = ctl().quarry();
        if (q != null) {
            say(c, "Quarry: " + q.describe());
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int start(CommandContext<FabricClientCommandSource> c) {
        String err = ctl().start(c.getSource().getClient());
        if (err != null) {
            say(c, err);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int save(CommandContext<FabricClientCommandSource> c) {
        String name = StringArgumentType.getString(c, "name");
        Quarry q = ctl().quarry();
        if (q == null) {
            say(c, "Nothing to save - set both corners first.");
        } else if (!Quarry.validName(name)) {
            say(c, "Use letters, digits, - or _ (max 40).");
        } else {
            try {
                q.save(name);
                say(c, "Saved quarry '" + name + "'.");
            } catch (IOException e) {
                say(c, "Could not save: " + e.getMessage());
            }
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int load(CommandContext<FabricClientCommandSource> c) {
        String name = StringArgumentType.getString(c, "name");
        if (!Quarry.validName(name)) {
            say(c, "Invalid name.");
            return Command.SINGLE_SUCCESS;
        }
        try {
            Quarry q = Quarry.load(name);
            ctl().loadQuarry(q);
            say(c, "Loaded '" + name + "': " + q.describe() + " in " + q.dimension);
        } catch (IOException e) {
            say(c, e.getMessage());
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int listProfiles(CommandContext<FabricClientCommandSource> c) {
        say(c, "Mode: " + ctl().profileOverride() + "  (use /miner profile auto, or /miner profile <id>)");
        for (PickaxeProfile p : PickaxeProfiles.all()) {
            say(c, " - " + p.id + ": " + p.label() + (p.hasMatchRules() ? "" : "  [manual only]"));
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int setProfile(CommandContext<FabricClientCommandSource> c) {
        String id = StringArgumentType.getString(c, "id");
        if (!"auto".equalsIgnoreCase(id) && PickaxeProfiles.byId(id) == null) {
            say(c, "Unknown profile '" + id + "'. /miner profile lists them.");
        } else {
            ctl().setProfileOverride(id.toLowerCase());
            say(c, "Pickaxe profile mode: " + id.toLowerCase());
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int calibrate(CommandContext<FabricClientCommandSource> c) {
        String err = ctl().calibrator().begin(c.getSource().getClient());
        say(c, err != null ? err : "Calibrating: mine that ONE block now (top face). I will record which neighbours vanish.");
        return Command.SINGLE_SUCCESS;
    }

    private static int calibrateSave(CommandContext<FabricClientCommandSource> c) {
        String id = StringArgumentType.getString(c, "id");
        int n = ctl().calibrator().applyTo(id);
        say(c, n < 0 ? "No finished calibration yet. Run /miner calibrate and mine a block first."
                : "Saved a " + n + "-block pattern to profile '" + id + "'. Use /miner profile " + id + " to force it.");
        return Command.SINGLE_SUCCESS;
    }
}
