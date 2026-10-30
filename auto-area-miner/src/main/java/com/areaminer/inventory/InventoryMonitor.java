package com.areaminer.inventory;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;

/** Read-only inventory and durability checks. Never moves or discards anything. */
public final class InventoryMonitor {
    private InventoryMonitor() {
    }

    /** Empty slots among the 36 main inventory slots (hotbar included). */
    public static int freeSlots(PlayerEntity player) {
        PlayerInventory inv = player.getInventory();
        int n = 0;
        for (int i = 0; i < 36; i++) {
            if (inv.getStack(i).isEmpty()) {
                n++;
            }
        }
        return n;
    }

    /** Remaining uses of a tool, or Integer.MAX_VALUE when it cannot break. */
    public static int usesLeft(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.isDamageable()) {
            return Integer.MAX_VALUE;
        }
        return stack.getMaxDamage() - stack.getDamage();
    }
}
