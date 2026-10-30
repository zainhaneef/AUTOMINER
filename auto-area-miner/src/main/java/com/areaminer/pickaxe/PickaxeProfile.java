package com.areaminer.pickaxe;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Describes what one kind of pickaxe breaks when its centre block is mined.
 * Stored in config/areaminer-pickaxes.json - nothing here is hard-coded to a server.
 */
public final class PickaxeProfile {
    public String id = "normal";
    public String displayName = "Normal pickaxe";
    /** Matches if the item's name contains ANY of these (case-insensitive). Empty = no name rule. */
    public List<String> nameContains = new ArrayList<>();
    /** Matches if any lore line contains ANY of these. Empty = no lore rule. */
    public List<String> loreContains = new ArrayList<>();
    /** Optional exact item id such as "minecraft:netherite_pickaxe". Empty = any. */
    public String itemId = "";
    /** Shorthand: odd N gives an N x N pattern in the layer plane. 0 = unused. */
    public int squareSize = 0;
    /** Explicit pattern: list of [dx, dy, dz] relative to the mined block (include [0,0,0]). */
    public int[][] offsets = null;
    /** Informational: the ability needs activation / has a cooldown on the server. */
    public boolean needsActivation = false;
    public String notes = "";

    private transient int[][] cached;

    public int[][] footprint() {
        if (cached == null) {
            if (offsets != null && offsets.length > 0) {
                cached = offsets;
            } else if (squareSize > 1) {
                int r = squareSize / 2;
                List<int[]> list = new ArrayList<>();
                for (int dz = -r; dz <= r; dz++) {
                    for (int dx = -r; dx <= r; dx++) {
                        list.add(new int[]{dx, 0, dz});
                    }
                }
                cached = list.toArray(new int[0][]);
            } else {
                cached = new int[][]{{0, 0, 0}};
            }
        }
        return cached;
    }

    public void invalidate() {
        cached = null;
    }

    public boolean isMultiBlock() {
        return footprint().length > 1;
    }

    public boolean hasMatchRules() {
        return !nameContains.isEmpty() || !loreContains.isEmpty() || (itemId != null && !itemId.isEmpty());
    }

    public boolean matches(String name, List<String> lore, String itemIdOfStack) {
        if (!hasMatchRules()) {
            return false;
        }
        if (itemId != null && !itemId.isEmpty() && !itemId.equals(itemIdOfStack)) {
            return false;
        }
        if (!nameContains.isEmpty() && !containsAny(name, nameContains)) {
            return false;
        }
        if (!loreContains.isEmpty()) {
            boolean any = false;
            for (String line : lore) {
                if (containsAny(line, loreContains)) {
                    any = true;
                    break;
                }
            }
            if (!any) {
                return false;
            }
        }
        return true;
    }

    private static boolean containsAny(String text, List<String> needles) {
        String t = text.toLowerCase(Locale.ROOT);
        for (String n : needles) {
            if (t.contains(n.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    public String label() {
        return displayName + (isMultiBlock() ? " (" + footprint().length + " blocks)" : "");
    }
}
