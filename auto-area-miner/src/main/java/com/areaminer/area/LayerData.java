package com.areaminer.area;

/** One horizontal layer of the quarry. Cell index = x + z * sizeX (relative to the quarry minimum). */
public final class LayerData {
    public final int sizeX;
    public final int sizeZ;
    private final byte[] cells;
    public int remaining;
    public int blocked;
    public int unknown;
    public int skipped;
    /** True once this layer has ever contained a mineable block. */
    public boolean hadWork;
    /** True after a full sweep found nothing left and nothing unknown. Reset when a block appears. */
    public boolean confirmedClear;

    public LayerData(int sizeX, int sizeZ) {
        this.sizeX = sizeX;
        this.sizeZ = sizeZ;
        this.cells = new byte[sizeX * sizeZ];
        this.unknown = cells.length;
    }

    public int size() {
        return cells.length;
    }

    public byte get(int idx) {
        return cells[idx];
    }

    public void set(int idx, byte state) {
        byte old = cells[idx];
        if (old == state) {
            return;
        }
        adjust(old, -1);
        adjust(state, +1);
        cells[idx] = state;
        if (state == CellState.REMAINING) {
            hadWork = true;
            confirmedClear = false;
        }
    }

    private void adjust(byte s, int d) {
        switch (s) {
            case CellState.REMAINING -> remaining += d;
            case CellState.BLOCKED -> blocked += d;
            case CellState.UNKNOWN -> unknown += d;
            case CellState.SKIPPED -> skipped += d;
            default -> { }
        }
    }
}
