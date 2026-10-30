package com.areaminer.gui;

import com.areaminer.AreaMinerClient;
import com.areaminer.config.MinerConfig;
import com.areaminer.core.MinerState;
import com.areaminer.core.QuarryController;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/** Control panel: Start / Pause-Resume / Stop / Clear plus live statistics. */
public final class MinerScreen extends Screen {
    private final QuarryController ctl = AreaMinerClient.controller();
    private ButtonWidget pauseButton;
    private ButtonWidget profileButton;
    private ButtonWidget overlayButton;

    public MinerScreen() {
        super(Text.literal("Auto Area Miner"));
    }

    @Override
    protected void init() {
        int bw = 100;
        int gap = 6;
        int total = 4 * bw + 3 * gap;
        int x0 = (this.width - total) / 2;
        int y1 = this.height - 56;
        int y2 = this.height - 30;

        addDrawableChild(ButtonWidget.builder(Text.literal("Start"), b -> {
            String err = ctl.start(MinecraftClient.getInstance());
            if (err != null) {
                ctl.warn(err);
            }
        }).dimensions(x0, y1, bw, 20).build());

        pauseButton = addDrawableChild(ButtonWidget.builder(Text.literal("Pause"), b -> {
            if (ctl.state() == MinerState.PAUSED) {
                ctl.resume();
            } else {
                ctl.pause("Paused by you.", false);
            }
        }).dimensions(x0 + bw + gap, y1, bw, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Stop"), b -> ctl.stop())
                .dimensions(x0 + 2 * (bw + gap), y1, bw, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Clear selection"), b -> ctl.clearSelection())
                .dimensions(x0 + 3 * (bw + gap), y1, bw, 20).build());

        profileButton = addDrawableChild(ButtonWidget.builder(Text.literal("Pickaxe: auto"), b -> ctl.cycleProfile())
                .dimensions(x0, y2, bw + 40, 20).build());

        overlayButton = addDrawableChild(ButtonWidget.builder(Text.literal("Overlay"), b -> {
            MinerConfig cfg = MinerConfig.get();
            cfg.worldOverlay = !cfg.worldOverlay;
            MinerConfig.save();
        }).dimensions(x0 + bw + 40 + gap, y2, bw, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Close"), b -> close())
                .dimensions(x0 + 3 * (bw + gap), y2, bw, 20).build());
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        pauseButton.setMessage(Text.literal(ctl.state() == MinerState.PAUSED ? "Resume" : "Pause"));
        profileButton.setMessage(Text.literal("Pickaxe: " + ctl.profileOverride()));
        overlayButton.setMessage(Text.literal("Overlay: " + (MinerConfig.get().worldOverlay ? "on" : "off")));

        int x = 20;
        int y = 20;
        ctx.drawTextWithShadow(this.textRenderer, "Auto Area Miner - manual-confirmation mode", x, y, 0xFFFFD060);
        y += 16;
        for (String line : ctl.statusLines()) {
            ctx.drawTextWithShadow(this.textRenderer, line, x, y, 0xFFFFFFFF);
            y += 11;
        }
        for (QuarryController.Note n : ctl.notes()) {
            ctx.drawTextWithShadow(this.textRenderer, n.text(), x, y + 6, MinerHud.colorOf(n.level()));
            y += 11;
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
