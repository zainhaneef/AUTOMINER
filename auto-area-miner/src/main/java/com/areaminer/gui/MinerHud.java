package com.areaminer.gui;

import com.areaminer.AreaMinerClient;
import com.areaminer.area.CellState;
import com.areaminer.area.LayerData;
import com.areaminer.area.Quarry;
import com.areaminer.area.QuarryModel;
import com.areaminer.config.MinerConfig;
import com.areaminer.core.MinerState;
import com.areaminer.core.QuarryController;
import com.areaminer.pickaxe.PickaxeProfile;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

/** Compact status panel plus a 2D map of the current layer. */
public final class MinerHud {
    private MinerHud() {
    }

    public static int colorOf(QuarryController.Level l) {
        return switch (l) {
            case INFO -> 0xFFB0E0FF;
            case WARN -> 0xFFFFD060;
            case ERROR -> 0xFFFF6060;
        };
    }

    private record Line(String text, int color) { }

    public static void render(DrawContext ctx) {
        MinerConfig cfg = MinerConfig.get();
        MinecraftClient mc = MinecraftClient.getInstance();
        QuarryController c = AreaMinerClient.controller();
        if (!cfg.hud || c == null || mc.player == null || mc.world == null || mc.options.hudHidden) {
            return;
        }
        List<QuarryController.Note> notes = c.notes();
        if (c.state() == MinerState.IDLE && c.quarry() == null && notes.isEmpty()) {
            return;
        }
        TextRenderer tr = mc.textRenderer;
        List<Line> lines = new ArrayList<>();
        lines.add(new Line("Auto Area Miner  [" + c.state() + "]", stateColor(c.state())));
        Quarry q = c.quarry();
        QuarryModel m = c.model();
        if (q == null) {
            lines.add(new Line("Select corners: /miner pos1, /miner pos2", 0xFFCCCCCC));
        } else {
            lines.add(new Line("Quarry " + q.describe(), 0xFFFFFFFF));
        }
        if (m != null) {
            if (c.state() == MinerState.SCANNING) {
                lines.add(new Line(String.format("Scanning... %.0f%%", c.scanProgress() * 100), 0xFFFFFFFF));
            } else {
                lines.add(new Line("Layer " + Math.max(1, c.layer() + 1) + "/" + m.layers.length
                        + (c.layer() >= 0 && c.layer() < m.layers.length ? "  y=" + q.yOfLayer(c.layer()) : ""), 0xFFFFFFFF));
                lines.add(new Line(String.format("Mined %,d   Left %,d   %.1f%%", m.cleared, m.remainingTotal(), m.percent()), 0xFFFFFFFF));
                double bpm = c.stats().blocksPerMinute(c.now());
                String eta = bpm > 0 ? String.format("   ETA %.0f min", m.remainingTotal() / bpm) : "";
                lines.add(new Line(String.format("%.0f blocks/min%s", bpm, eta), 0xFFCCCCCC));
            }
        }
        PickaxeProfile pr = c.profile();
        if (q != null) {
            lines.add(new Line("Pickaxe: " + (pr != null ? pr.label() : "none held"), 0xFFCCCCCC));
        }
        BlockPos t = c.target();
        if (t != null) {
            lines.add(new Line("Target " + t.getX() + " " + t.getY() + " " + t.getZ(), 0xFFFFE040));
        }
        if (c.state() == MinerState.PAUSED) {
            lines.add(new Line(c.pauseReason(), 0xFFFFD060));
        } else if (!c.hint().isEmpty() && c.isActive()) {
            lines.add(new Line(c.hint(), 0xFF90FF90));
        }
        for (QuarryController.Note n : notes) {
            lines.add(new Line(n.text(), colorOf(n.level())));
        }

        int width = 0;
        for (Line l : lines) {
            width = Math.max(width, tr.getWidth(l.text()));
        }
        int x = cfg.hudX;
        int y = cfg.hudY;
        int height = lines.size() * 10 + 6;
        ctx.fill(x - 3, y - 3, x + width + 3, y - 3 + height, 0x90000000);
        int ty = y;
        for (Line l : lines) {
            ctx.drawTextWithShadow(tr, l.text(), x, ty, l.color());
            ty += 10;
        }

        if (m != null && c.layer() >= 0 && c.layer() < m.layers.length && c.state() != MinerState.SCANNING) {
            drawMap(ctx, c, m, mc, x, y - 3 + height + 4, cfg);
        }
    }

    private static int stateColor(MinerState s) {
        return switch (s) {
            case MINING, VERIFYING -> 0xFF60FF60;
            case MOVING, POSITIONING -> 0xFF60C0FF;
            case PAUSED, RECOVERING -> 0xFFFFD060;
            case ERROR -> 0xFFFF6060;
            case COMPLETED -> 0xFF60FFC0;
            default -> 0xFFFFFFFF;
        };
    }

    private static void drawMap(DrawContext ctx, QuarryController c, QuarryModel m, MinecraftClient mc,
                                int px, int py, MinerConfig cfg) {
        Quarry q = m.q;
        LayerData d = m.layers[c.layer()];
        int sx = q.sizeX();
        int sz = q.sizeZ();
        int w = Math.min(sx, cfg.mapMaxCells);
        int h = Math.min(sz, cfg.mapMaxCells);
        int cp = cfg.mapCellPx;
        BlockPos t = c.target();
        int fx = t != null ? t.getX() - q.minX : mc.player.getBlockX() - q.minX;
        int fz = t != null ? t.getZ() - q.minZ : mc.player.getBlockZ() - q.minZ;
        int ox = Math.max(0, Math.min(sx - w, fx - w / 2));
        int oz = Math.max(0, Math.min(sz - h, fz - h / 2));

        ctx.fill(px - 1, py - 1, px + w * cp + 1, py + h * cp + 1, 0xFF000000);
        for (int z = 0; z < h; z++) {
            for (int x = 0; x < w; x++) {
                byte s = d.get((oz + z) * sx + ox + x);
                ctx.fill(px + x * cp, py + z * cp, px + (x + 1) * cp, py + (z + 1) * cp, cellColor(s));
            }
        }
        if (t != null) {
            for (int[] o : c.planFootprint()) {
                if (o[1] != 0) {
                    continue;
                }
                cell(ctx, px, py, cp, t.getX() - q.minX + o[0] - ox, t.getZ() - q.minZ + o[2] - oz, w, h, 0xFF40E060);
            }
            cell(ctx, px, py, cp, t.getX() - q.minX - ox, t.getZ() - q.minZ - oz, w, h, 0xFFFFE040);
        }
        cell(ctx, px, py, cp, mc.player.getBlockX() - q.minX - ox, mc.player.getBlockZ() - q.minZ - oz, w, h, 0xFFFFFFFF);
    }

    private static void cell(DrawContext ctx, int px, int py, int cp, int x, int z, int w, int h, int color) {
        if (x < 0 || z < 0 || x >= w || z >= h) {
            return;
        }
        ctx.fill(px + x * cp, py + z * cp, px + (x + 1) * cp, py + (z + 1) * cp, color);
    }

    private static int cellColor(byte s) {
        return switch (s) {
            case CellState.REMAINING -> 0xFF7A8794;
            case CellState.EMPTY -> 0xFF1A1A1A;
            case CellState.BLOCKED -> 0xFFC04040;
            case CellState.IGNORED -> 0xFF3A3A3A;
            case CellState.SKIPPED -> 0xFFB07020;
            default -> 0xFF202850;
        };
    }
}
