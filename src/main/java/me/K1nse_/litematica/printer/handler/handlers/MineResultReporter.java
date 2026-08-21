package me.K1nse_.litematica.printer.handler.handlers;

import me.K1nse_.litematica.printer.handler.HudStatsManager;
import me.K1nse_.litematica.printer.mixin_extension.BlockBreakResult;
import me.K1nse_.litematica.printer.utils.ConfigUtils;
import me.K1nse_.litematica.printer.utils.InteractionUtils;
import net.minecraft.core.BlockPos;

final class MineResultReporter {
    private MineResultReporter() {
    }

    static void record(BlockPos blockPos, BlockBreakResult result) {
        switch (result) {
            case COMPLETED -> {
                InteractionUtils.INSTANCE.markRecentlyBroken(blockPos);
                HudStatsManager.INSTANCE.trackExpectedMineClear(HudStatsManager.Mode.MINE, blockPos);
                HudStatsManager.INSTANCE.recordRateUnit(HudStatsManager.Mode.MINE, 1);
                HudStatsManager.INSTANCE.recordStatus(HudStatsManager.Mode.MINE, "运行中");
            }
            case COMPLETED_WAIT -> {
                InteractionUtils.INSTANCE.markRecentlyBroken(blockPos);
                InteractionUtils.INSTANCE.markPendingBroken(blockPos, ConfigUtils.getBreakCooldown());
                HudStatsManager.INSTANCE.trackExpectedMineClear(HudStatsManager.Mode.MINE, blockPos);
                HudStatsManager.INSTANCE.recordDeferred(HudStatsManager.Mode.MINE, "等待服务端确认");
            }
            case IN_PROGRESS -> {
                HudStatsManager.INSTANCE.trackExpectedMineClear(HudStatsManager.Mode.MINE, blockPos);
                HudStatsManager.INSTANCE.recordStatus(HudStatsManager.Mode.MINE, "破坏中");
            }
            case ABORTED -> {
                HudStatsManager.INSTANCE.trackExpectedMineClear(HudStatsManager.Mode.MINE, blockPos);
                HudStatsManager.INSTANCE.recordDeferred(HudStatsManager.Mode.MINE, "挖掘中断");
            }
            case FAILED -> HudStatsManager.INSTANCE.recordFailure(HudStatsManager.Mode.MINE, "破坏失败");
        }
    }
}
