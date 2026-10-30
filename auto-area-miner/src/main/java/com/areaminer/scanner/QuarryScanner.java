package com.areaminer.scanner;

import com.areaminer.area.Quarry;
import com.areaminer.area.QuarryModel;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;

/** Initial incremental scan: classifies a limited number of cells per tick, top layer first. */
public final class QuarryScanner {
    private final QuarryModel model;
    private final CellClassifier classifier;
    private final long total;
    private long ptr;
    private final BlockPos.Mutable pos = new BlockPos.Mutable();

    public QuarryScanner(QuarryModel model, CellClassifier classifier) {
        this.model = model;
        this.classifier = classifier;
        this.total = model.q.volume();
    }

    /** @return true when the whole volume has been visited. */
    public boolean step(ClientWorld world, ItemStack tool, int budget) {
        Quarry q = model.q;
        int layerSize = q.sizeX() * q.sizeZ();
        for (int i = 0; i < budget && ptr < total; i++, ptr++) {
            int layer = (int) (ptr / layerSize);
            int idx = (int) (ptr % layerSize);
            pos.set(q.cellX(idx), q.yOfLayer(layer), q.cellZ(idx));
            model.layers[layer].set(idx, classifier.classify(world, pos, tool));
        }
        return ptr >= total;
    }

    public double progress() {
        return total == 0 ? 1.0 : (double) ptr / total;
    }
}
