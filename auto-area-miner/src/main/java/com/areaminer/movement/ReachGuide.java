package com.areaminer.movement;

import com.areaminer.pickaxe.PickaxeProfile;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/**
 * Guidance maths for manual mode: how far the target is, whether the crosshair is on it,
 * and what to tell the player. Never moves or aims the player.
 */
public final class ReachGuide {
    private ReachGuide() {
    }

    public static BlockHitResult blockHit(MinecraftClient mc) {
        HitResult h = mc.crosshairTarget;
        if (h instanceof BlockHitResult b && b.getType() == HitResult.Type.BLOCK) {
            return b;
        }
        return null;
    }

    public static double distance(Vec3d eye, BlockPos target) {
        return eye.distanceTo(Vec3d.ofCenter(target));
    }

    public static String moveHint(Vec3d eye, BlockPos t, double dist, double reach) {
        int dx = (int) Math.round(t.getX() + 0.5 - eye.x);
        int dy = (int) Math.round(t.getY() + 0.5 - eye.y);
        int dz = (int) Math.round(t.getZ() + 0.5 - eye.z);
        return String.format("Move closer: dx %+d, dy %+d, dz %+d (%.1f blocks away, reach %.1f)", dx, dy, dz, dist, reach);
    }

    public static String mineHint(BlockHitResult hit, PickaxeProfile profile) {
        if (profile != null && profile.isMultiBlock()
                && hit.getSide() != Direction.UP && hit.getSide() != Direction.DOWN) {
            return "Aim at the TOP face so the pattern breaks inside this layer, then mine.";
        }
        return "Mine the highlighted block.";
    }
}
