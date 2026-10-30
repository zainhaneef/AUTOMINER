package com.areaminer.pickaxe;

import com.areaminer.movement.ReachGuide;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Measures what a special pickaxe REALLY breaks: look at a block, run /miner calibrate,
 * mine that one block, and the surrounding blocks that vanish become the observed pattern.
 */
public final class Calibrator {
    public enum Phase { OFF, WAITING, SETTLING, DONE }

    private static final int R = 3;
    private Phase phase = Phase.OFF;
    private BlockPos centre;
    private final Set<BlockPos> snapshot = new HashSet<>();
    private int settle;
    private List<int[]> result = new ArrayList<>();

    public Phase phase() {
        return phase;
    }

    public List<int[]> result() {
        return result;
    }

    /** @return an error message, or null when calibration started. */
    public String begin(MinecraftClient mc) {
        if (mc.world == null) {
            return "Not in a world.";
        }
        BlockHitResult hit = ReachGuide.blockHit(mc);
        if (hit == null) {
            return "Look at the block you are going to mine, then run the command again.";
        }
        centre = hit.getBlockPos().toImmutable();
        snapshot.clear();
        for (int dx = -R; dx <= R; dx++) {
            for (int dy = -R; dy <= R; dy++) {
                for (int dz = -R; dz <= R; dz++) {
                    BlockPos p = centre.add(dx, dy, dz);
                    if (!mc.world.getBlockState(p).isAir()) {
                        snapshot.add(p);
                    }
                }
            }
        }
        phase = Phase.WAITING;
        return null;
    }

    public void cancel() {
        phase = Phase.OFF;
        snapshot.clear();
    }

    public void tick(MinecraftClient mc) {
        if (phase == Phase.OFF || phase == Phase.DONE || mc.world == null) {
            return;
        }
        if (phase == Phase.WAITING) {
            if (mc.world.getBlockState(centre).isAir()) {
                phase = Phase.SETTLING;
                settle = 10;
            }
            return;
        }
        if (--settle > 0) {
            return;
        }
        List<int[]> res = new ArrayList<>();
        int minX = 0, maxX = 0, minY = 0, maxY = 0, minZ = 0, maxZ = 0;
        for (BlockPos p : snapshot) {
            if (mc.world.getBlockState(p).isAir()) {
                int dx = p.getX() - centre.getX();
                int dy = p.getY() - centre.getY();
                int dz = p.getZ() - centre.getZ();
                res.add(new int[]{dx, dy, dz});
                minX = Math.min(minX, dx);
                maxX = Math.max(maxX, dx);
                minY = Math.min(minY, dy);
                maxY = Math.max(maxY, dy);
                minZ = Math.min(minZ, dz);
                maxZ = Math.max(maxZ, dz);
            }
        }
        result = res;
        phase = Phase.DONE;
        snapshot.clear();
        if (mc.player != null) {
            mc.player.sendMessage(Text.literal("[Miner] Calibration: " + res.size() + " block(s) broke. Extent x "
                    + minX + ".." + maxX + ", y " + minY + ".." + maxY + ", z " + minZ + ".." + maxZ
                    + ". Save it with /miner calibrate save <profileId>."), false);
        }
    }

    /** Writes the measured pattern into a profile (creating it if needed). @return blocks in the pattern. */
    public int applyTo(String profileId) {
        if (phase != Phase.DONE || result.isEmpty()) {
            return -1;
        }
        PickaxeProfile p = PickaxeProfiles.byId(profileId);
        if (p == null) {
            p = new PickaxeProfile();
            p.id = profileId;
            p.displayName = profileId;
            PickaxeProfiles.add(p);
        }
        p.offsets = result.toArray(new int[0][]);
        p.squareSize = 0;
        p.invalidate();
        PickaxeProfiles.save();
        return result.size();
    }
}
