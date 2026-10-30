package com.areaminer.gui;

import net.minecraft.client.MinecraftClient;

/** Opens the screen one tick after a command ran, so the closing chat screen doesn't swallow it. */
public final class ScreenOpener {
    private static boolean pending;

    private ScreenOpener() {
    }

    public static void request() {
        pending = true;
    }

    public static void tick(MinecraftClient client) {
        if (pending) {
            pending = false;
            client.setScreen(new MinerScreen());
        }
    }
}
