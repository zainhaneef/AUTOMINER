package com.areaminer.scanner;

import com.areaminer.area.CellState;
import com.areaminer.config.MinerConfig;
import net.minecraft.block.BlockState;
import net.minecraft.block.FluidBlock;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;

import java.util.HashSet;
import java.util.Set;

/** Turns what is actually in the world into a CellState. Reads the world only, never changes it. */
public final class CellClassifier {
    private final Set<String> ignored;

    public CellClassifier(MinerConfig cfg) {
        this.ignored = new HashSet<>(cfg.ignoredBlocks);
    }

    /**
     * @param tool the held mining tool, or null when no mining tool is held
     *             (then no cell is classified as BLOCKED).
     */
    public byte classify(ClientWorld world, BlockPos pos, ItemStack tool) {
        if (!world.getChunkManager().isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) {
            return CellState.UNKNOWN;
        }
        BlockState state = world.getBlockState(pos);
        if (state.isAir() || state.getBlock() instanceof FluidBlock) {
            return CellState.EMPTY;
        }
        if (state.getHardness(world, pos) < 0) {
            return CellState.IGNORED; // bedrock, barriers, ...
        }
        if (!ignored.isEmpty() && ignored.contains(Registries.BLOCK.getId(state.getBlock()).toString())) {
            return CellState.IGNORED;
        }
        if (tool != null && state.isToolRequired() && !tool.isSuitableFor(state)) {
            return CellState.BLOCKED;
        }
        return CellState.REMAINING;
    }
}
