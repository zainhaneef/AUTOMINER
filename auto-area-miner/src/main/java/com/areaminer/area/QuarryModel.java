package com.areaminer.area;

/** Runtime knowledge about every cell of the quarry. Only touched from the client thread. */
public final class QuarryModel {
    public final Quarry q;
    public final LayerData[] layers;
    /** Blocks that were observed to disappear (each counted once). */
    public long cleared;

    public QuarryModel(Quarry q) {
        this.q = q;
        this.layers = new LayerData[q.sizeY()];
        for (int i = 0; i < layers.length; i++) {
            layers[i] = new LayerData(q.sizeX(), q.sizeZ());
        }
    }

    public byte stateAt(int x, int y, int z) {
        if (!q.contains(x, y, z)) {
            return CellState.IGNORED;
        }
        return layers[q.layerOfY(y)].get(q.cellIndex(x, z));
    }

    /**
     * Records a freshly observed world state for one cell.
     * Returns true only when this observation counts as a newly cleared block
     * (a block we knew about is now air). That is the only place progress is gained.
     */
    public boolean observe(int x, int y, int z, byte now) {
        LayerData layer = layers[q.layerOfY(y)];
        int idx = q.cellIndex(x, z);
        byte old = layer.get(idx);
        if (old == now) {
            return false;
        }
        if (old == CellState.SKIPPED && now != CellState.EMPTY && now != CellState.SKIPPED) {
            return false; // stay skipped until /miner retry
        }
        boolean cleared = now == CellState.EMPTY
                && (old == CellState.REMAINING || old == CellState.BLOCKED || old == CellState.SKIPPED);
        layer.set(idx, now);
        if (cleared) {
            this.cleared++;
        }
        return cleared;
    }

    public long remainingTotal() {
        long n = 0;
        for (LayerData l : layers) {
            n += l.remaining;
        }
        return n;
    }

    public long blockedTotal() {
        long n = 0;
        for (LayerData l : layers) {
            n += l.blocked;
        }
        return n;
    }

    public long skippedTotal() {
        long n = 0;
        for (LayerData l : layers) {
            n += l.skipped;
        }
        return n;
    }

    public long unknownTotal() {
        long n = 0;
        for (LayerData l : layers) {
            n += l.unknown;
        }
        return n;
    }

    public double percent() {
        long total = cleared + remainingTotal() + blockedTotal() + skippedTotal();
        return total == 0 ? 100.0 : 100.0 * cleared / total;
    }
}
