package me.K1nse_.litematica.printer.handler.handlers;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

public final class MineDebugLog {
    private MineDebugLog() {
    }

    public static void write(String message) {
    }

    public static void reset() {
    }

    public static String pos(BlockPos pos) {
        if (pos == null) {
            return "null";
        }
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    public static String describeState(BlockState state) {
        return state.getBlock() + " " + state;
    }
}
