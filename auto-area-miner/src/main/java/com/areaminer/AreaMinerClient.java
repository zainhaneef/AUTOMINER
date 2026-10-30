package com.areaminer;

import com.areaminer.commands.MinerCommands;
import com.areaminer.config.MinerConfig;
import com.areaminer.core.QuarryController;
import com.areaminer.gui.MinerHud;
import com.areaminer.gui.ScreenOpener;
import com.areaminer.pickaxe.PickaxeProfiles;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AreaMinerClient implements ClientModInitializer {
    public static final String MOD_ID = "areaminer";
    public static final Logger LOG = LoggerFactory.getLogger("AreaMiner");

    private static QuarryController controller;

    public static QuarryController controller() {
        return controller;
    }

    @Override
    public void onInitializeClient() {
        MinerConfig.load();
        PickaxeProfiles.load();
        controller = new QuarryController();
        MinerCommands.register();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            try {
                controller.tick(client);
            } catch (Throwable t) {
                LOG.error("Auto Area Miner stopped after an internal error", t);
                controller.failFromException(t);
            }
            ScreenOpener.tick(client);
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> controller.onDisconnect());

        HudElementRegistry.addLast(Identifier.of(MOD_ID, "hud"), (context, tickCounter) -> MinerHud.render(context));

        installOverlay();
        LOG.info("Auto Area Miner loaded (manual-confirmation mode).");
    }

    /** Reflective so the mod still builds and runs when WorldOverlay.java is excluded (-PnoWorldRender). */
    private static void installOverlay() {
        try {
            Class.forName("com.areaminer.render.WorldOverlay").getMethod("install").invoke(null);
        } catch (ClassNotFoundException e) {
            LOG.info("In-world overlay not included in this build - HUD and layer map only.");
        } catch (Throwable t) {
            LOG.warn("In-world overlay could not be installed - HUD and layer map still work.", t);
        }
    }
}
