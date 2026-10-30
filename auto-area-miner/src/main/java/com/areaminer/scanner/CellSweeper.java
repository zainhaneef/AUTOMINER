package com.areaminer.scanner;

import com.areaminer.area.CellState;
import com.areaminer.area.LayerData;
import com.areaminer.area.Quarry;
import com.areaminer.area.QuarryModel;
import com.areaminer.mining.MiningStats;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;

/**
 * Re-checks the current layer a few hundred cells per tick, so changes made by anyone
 * (you, other players, falling gravel, a different tool) are noticed without full rescans.
 */
public final class CellSweeper {
    private final QuarryModel model;
    private final CellClassifier classifier;
    private final BlockPos.Mutable pos = new BlockPos.Mutable();
    private int layer = -1;
    private int ptr;

    public CellSweeper(QuarryModel model, CellClassifier classifier) {
        this.model = model;
        this.classifier = classifier;
    }

    public void setLayer(int l) {
        if (l != layer) {
            layer = l;
            ptr = 0;
        }
    }

    public void step(ClientWorld world, ItemStack tool, int budget, long tick, MiningStats stats) {
        if (layer < 0 || layer >= model.layers.length) {
            return;
        }
        Quarry q = model.q;
        LayerData data = model.layers[layer];
        int size = data.size();
        int y = q.yOfLayer(layer);
        for (int i = 0; i < budget; i++) {
            int idx = ptr;
            int x = q.cellX(idx);
            int z = q.cellZ(idx);
            byte old = data.get(idx);
            if (old != CellState.IGNORED) {
                pos.set(x, y, z);
                byte now = classifier.classify(world, pos, tool);
                if (now != CellState.UNKNOWN && model.observe(x, y, z, now)) {
                    stats.record(tick);
                }
            }
            ptr++;
            if (ptr >= size) {
                ptr = 0;
                data.confirmedClear = data.remaining == 0 && data.unknown == 0;
                return; // one full cycle per call at most
            }
        }
    }
}
