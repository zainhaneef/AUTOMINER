package com.areaminer.render;

import com.areaminer.AreaMinerClient;
import com.areaminer.area.Quarry;
import com.areaminer.config.MinerConfig;
import com.areaminer.core.QuarryController;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexRendering;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * In-world outlines: quarry boundary, current layer slab, target + pattern, next few targets.
 *
 * NOTE: this is the most Minecraft-version-sensitive file in the mod (Fabric reworked world
 * rendering in 1.21.9/1.21.10). If it does not compile, build with  -PnoWorldRender  and the mod
 * still works with the HUD and layer map. If it throws at runtime it switches itself off.
 */
public final class WorldOverlay {
    private static boolean failed;

    private WorldOverlay() {
    }

    /** Called reflectively from AreaMinerClient so the mod still builds when this file is excluded. */
    public static void install() {
        WorldRenderEvents.AFTER_ENTITIES.register(WorldOverlay::render);
    }

    private static void render(WorldRenderContext ctx) {
        if (failed) {
            return;
        }
        try {
            draw(ctx);
        } catch (Throwable t) {
            failed = true;
            AreaMinerClient.LOG.warn("World overlay disabled after an error (HUD still works)", t);
        }
    }

    private static void draw(WorldRenderContext ctx) {
        if (!MinerConfig.get().worldOverlay) {
            return;
        }
        QuarryController c = AreaMinerClient.controller();
        if (c == null) {
            return;
        }
        MatrixStack ms = ctx.matrices();
        VertexConsumerProvider vcp = ctx.consumers();
        if (ms == null || vcp == null) {
            return;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        Vec3d cam = mc.gameRenderer.getCamera().getPos();
        VertexConsumer lines = vcp.getBuffer(RenderLayer.getLines());

        Quarry q = c.quarry();
        if (q != null) {
            box(ms, lines, cam, q.minX, q.minY, q.minZ, q.maxX + 1, q.maxY + 1, q.maxZ + 1, 0.2f, 0.8f, 1.0f, 1.0f);
            if (c.isActive() && c.layer() >= 0 && c.layer() < q.sizeY()) {
                int y = q.yOfLayer(c.layer());
                box(ms, lines, cam, q.minX, y, q.minZ, q.maxX + 1, y + 1, q.maxZ + 1, 1.0f, 1.0f, 1.0f, 0.6f);
            }
        } else {
            BlockPos a = c.pos1();
            BlockPos b = c.pos2();
            if (a != null) {
                unit(ms, lines, cam, a, 0.2f, 1.0f, 0.2f);
            }
            if (b != null) {
                unit(ms, lines, cam, b, 1.0f, 0.4f, 0.2f);
            }
        }

        BlockPos t = c.target();
        if (t != null) {
            for (int[] o : c.planFootprint()) {
                unit(ms, lines, cam, t.add(o[0], o[1], o[2]), 0.3f, 1.0f, 0.4f);
            }
            unit(ms, lines, cam, t, 1.0f, 0.9f, 0.1f);
        }
        for (BlockPos n : c.upcomingTargets(8)) {
            if (!n.equals(t)) {
                box(ms, lines, cam, n.getX() + 0.3, n.getY() + 0.3, n.getZ() + 0.3,
                        n.getX() + 0.7, n.getY() + 0.7, n.getZ() + 0.7, 0.3f, 0.9f, 1.0f, 0.9f);
            }
        }
    }

    private static void unit(MatrixStack ms, VertexConsumer vc, Vec3d cam, BlockPos p, float r, float g, float b) {
        box(ms, vc, cam, p.getX(), p.getY(), p.getZ(), p.getX() + 1, p.getY() + 1, p.getZ() + 1, r, g, b, 1.0f);
    }

    private static void box(MatrixStack ms, VertexConsumer vc, Vec3d cam,
                            double x1, double y1, double z1, double x2, double y2, double z2,
                            float r, float g, float b, float a) {
        VertexRendering.drawBox(ms, vc, x1 - cam.x, y1 - cam.y, z1 - cam.z, x2 - cam.x, y2 - cam.y, z2 - cam.z, r, g, b, a);
    }
}
