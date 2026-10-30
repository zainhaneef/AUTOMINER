package com.areaminer.area;

/** State of one block position inside the quarry. */
public final class CellState {
    public static final byte UNKNOWN = 0;    // not scanned yet / chunk not loaded
    public static final byte EMPTY = 1;      // air, fluid, or already removed
    public static final byte REMAINING = 2;  // mineable block still present
    public static final byte BLOCKED = 3;    // present, but the current tool cannot mine it
    public static final byte IGNORED = 4;    // unbreakable or on the ignore list
    public static final byte SKIPPED = 5;    // given up on after retries

    private CellState() {
    }
}
