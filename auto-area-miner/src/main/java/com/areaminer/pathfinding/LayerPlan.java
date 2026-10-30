package com.areaminer.pathfinding;

import com.areaminer.area.CellState;
import com.areaminer.area.LayerData;
import com.areaminer.area.QuarryModel;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

/**
 * Serpentine plan for ONE layer, built incrementally (a few thousand cells per tick).
 *
 * Walks the layer row by row, alternating direction. Whenever it meets a block that no earlier
 * centre will break, it picks the centre (a mineable block) whose pattern covers the most
 * still-uncovered blocks. For a normal pickaxe the pattern is one block, so the plan is simply
 * every block in serpentine order. For a 3x3 pickaxe it becomes a lattice of centres.
 *
 * Centres whose pattern would break blocks outside the quarry are rejected unless
 * allowOvershoot is on; cells that can only be reached that way become "leftovers".
 */
public final class LayerPlan {
    private final QuarryModel model;
    private final int layer;
    private final int[][] fp;       // [dx, dy, dz]
    private final int[][] fpLayer;  // dy == 0 entries as [dx, dz]
    private final boolean allowOvershoot;
    private final int sx;
    private final int sz;
    private final List<Integer> centres = new ArrayList<>();
    private final List<Integer> leftovers = new ArrayList<>();
    private final BitSet covered;
    private int cursor;
    private int head;
    private boolean done;

    public LayerPlan(QuarryModel model, int layer, int[][] footprint, boolean allowOvershoot) {
        this.model = model;
        this.layer = layer;
        this.fp = footprint;
        this.allowOvershoot = allowOvershoot;
        this.sx = model.q.sizeX();
        this.sz = model.q.sizeZ();
        this.covered = new BitSet(sx * sz);
        List<int[]> flat = new ArrayList<>();
        for (int[] o : footprint) {
            if (o[1] == 0) {
                flat.add(new int[]{o[0], o[2]});
            }
        }
        if (flat.isEmpty()) {
            flat.add(new int[]{0, 0});
        }
        this.fpLayer = flat.toArray(new int[0][]);
    }

    public boolean isDone() {
        return done;
    }

    public int layer() {
        return layer;
    }

    public int plannedCentres() {
        return centres.size();
    }

    public int[][] footprint() {
        return fp;
    }

    /** Processes up to {@code budget} cells. @return true once the whole layer has been planned. */
    public boolean step(int budget) {
        LayerData data = model.layers[layer];
        int total = data.size();
        for (int n = 0; n < budget && cursor < total; n++, cursor++) {
            int r = cursor / sx;
            int c = cursor % sx;
            if ((r & 1) == 1) {
                c = sx - 1 - c; // serpentine
            }
            int idx = r * sx + c;
            if (data.get(idx) != CellState.REMAINING || covered.get(idx)) {
                continue;
            }
            int bestCx = -1;
            int bestCz = -1;
            int bestScore = 0;
            for (int[] o : fpLayer) {
                int cx = c - o[0];
                int cz = r - o[1];
                if (!centreValid(data, cx, cz)) {
                    continue;
                }
                int score = 0;
                for (int[] p : fpLayer) {
                    int x = cx + p[0];
                    int z = cz + p[1];
                    if (x < 0 || z < 0 || x >= sx || z >= sz) {
                        continue;
                    }
                    int j = z * sx + x;
                    if (data.get(j) == CellState.REMAINING && !covered.get(j)) {
                        score++;
                    }
                }
                if (score > bestScore) {
                    bestScore = score;
                    bestCx = cx;
                    bestCz = cz;
                }
            }
            if (bestScore == 0) {
                leftovers.add(idx);
                covered.set(idx);
            } else {
                centres.add(bestCz * sx + bestCx);
                for (int[] p : fpLayer) {
                    int x = bestCx + p[0];
                    int z = bestCz + p[1];
                    if (x >= 0 && z >= 0 && x < sx && z < sz && data.get(z * sx + x) == CellState.REMAINING) {
                        covered.set(z * sx + x);
                    }
                }
            }
        }
        if (cursor >= total) {
            done = true;
        }
        return done;
    }

    private boolean centreValid(LayerData data, int cx, int cz) {
        if (cx < 0 || cz < 0 || cx >= sx || cz >= sz) {
            return false;
        }
        if (data.get(cz * sx + cx) != CellState.REMAINING) {
            return false; // the centre is the block we actually mine, so it must exist
        }
        if (allowOvershoot) {
            return true;
        }
        for (int[] o : fp) {
            int x = cx + o[0];
            int z = cz + o[2];
            int l = layer - o[1]; // +dy is up, and layer 0 is the top
            if (x < 0 || z < 0 || x >= sx || z >= sz || l < 0 || l >= model.layers.length) {
                return false;
            }
        }
        return true;
    }

    /** A centre is still worth mining while its own block is still there. */
    private boolean useful(int centreIdx) {
        return model.layers[layer].get(centreIdx) == CellState.REMAINING;
    }

    /** Drops finished centres from the front of the queue. */
    public void pruneHead() {
        while (head < centres.size() && !useful(centres.get(head))) {
            head++;
        }
    }

    /** Up to {@code max} still-useful centres in route order (cell indices). */
    public List<Integer> upcoming(int max) {
        List<Integer> out = new ArrayList<>();
        for (int i = head; i < centres.size() && out.size() < max; i++) {
            if (useful(centres.get(i))) {
                out.add(centres.get(i));
            }
        }
        return out;
    }

    /** Cells that could not be covered without breaking blocks outside the quarry, and are still present. */
    public int leftoverRemaining() {
        LayerData data = model.layers[layer];
        int n = 0;
        for (int idx : leftovers) {
            if (data.get(idx) == CellState.REMAINING) {
                n++;
            }
        }
        return n;
    }
}
