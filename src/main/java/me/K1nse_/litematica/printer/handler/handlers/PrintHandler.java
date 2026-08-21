package me.K1nse_.litematica.printer.handler.handlers;

import fi.dy.masa.litematica.world.SchematicWorldHandler;
import fi.dy.masa.litematica.world.WorldSchematic;
import me.K1nse_.litematica.printer.config.Configs;
import me.K1nse_.litematica.printer.enums.PrintModeType;
import me.K1nse_.litematica.printer.guide.Guides;
import me.K1nse_.litematica.printer.handler.Module;
import me.K1nse_.litematica.printer.handler.scan.ScanIntent;
import me.K1nse_.litematica.printer.handler.handlers.print.PrintPlacementExecutor;
import me.K1nse_.litematica.printer.handler.handlers.print.PrintPlacementResult;
import me.K1nse_.litematica.printer.handler.handlers.print.PrintTaskAction;
import me.K1nse_.litematica.printer.handler.handlers.print.PrintTaskBuildResult;
import me.K1nse_.litematica.printer.handler.handlers.print.PrintTaskController;
import me.K1nse_.litematica.printer.handler.handlers.print.SortedSchematicTargetQueue;
import me.K1nse_.litematica.printer.printer.*;
import me.K1nse_.litematica.printer.printer.action.Action;
import me.K1nse_.litematica.printer.utils.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.jetbrains.annotations.Nullable;

public class PrintHandler extends Module {
    public final static String NAME = "print";

    private Action action;
    @Nullable
    private PrintTaskAction printTaskAction;

    private SchematicBlockContext ctx;
    private final PrintTaskController printTasks = new PrintTaskController();
    private final SortedSchematicTargetQueue sortedTargets = new SortedSchematicTargetQueue();
    private final PrintPlacementExecutor placementExecutor = new PrintPlacementExecutor();

    private List<String> printSkipListCache = List.of();
    private String[] printSkipFilters = new String[0];

    // ---- 按物品分组批处理（移植自原代码2 ScanPlan）----
    private final Map<Item, ArrayDeque<BlockPos>> pendingGroups = new LinkedHashMap<>();
    private boolean groupCollecting = true;
    private Iterator<Map.Entry<Item, ArrayDeque<BlockPos>>> groupIter;
    private Map.Entry<Item, ArrayDeque<BlockPos>> currentGroupEntry;
    private static final int GROUP_COLLECT_LIMIT = 48;
    private int pendingCount;

    public PrintHandler() {
        super(NAME, PrintModeType.PRINTER, Configs.Core.PRINT, Configs.Print.PRINT_SELECTION_TYPE, true);
    }

    public SchematicBlockContext getContext() {
        return ctx;
    }

    @Override
    protected int getTickInterval() {
        if (this.printTasks.hasActiveTask()) {
            return 0;
        }
        int baseInterval = Configs.Placement.PLACE_INTERVAL.getIntegerValue();
        if (Configs.Placement.RTT_ADAPTIVE_INTERVAL.getBooleanValue()) {
            // 保证重放间隔不低于一次往返(RTT),避免在服务端确认上一次放置前就发下一个导致放错。
            int rttFloor = RttReplayController.INSTANCE.getExtraIntervalTicks(
                    Configs.Placement.RTT_SAFETY_PERCENT.getIntegerValue());
            return Math.max(baseInterval, rttFloor);
        }
        return baseInterval;
    }

    @Override
    protected int getMaxEffectiveExecutionsPerTick() {
        return Configs.Placement.PLACE_BLOCKS_PER_TICK.getIntegerValue();
    }

    @Override
    protected boolean isSchematicBlockHandler() {
        return true;
    }

    @Override
    protected boolean canIterate() {
        // 分组处理模式：暂停扫描，把 tick 预算让给分组放置
        return this.groupCollecting;
    }

    @Override
    protected void preprocess() {
        this.updatePrintSkipCache();
        if (!this.groupCollecting) {
            this.processGroups();
        }
    }

    @Override
    protected void onRuntimeReset() {
        this.action = null;
        this.printTaskAction = null;
        this.ctx = null;
        this.printTasks.clear();
        this.sortedTargets.clear();
    }

    @Override
    protected Iterable<BlockPos> getIterationPositions(PrinterBox playerInteractionBox) {
        WorldSchematic schematic = SchematicWorldHandler.getSchematicWorld();
        BlockPos activeTaskPos = this.printTasks.getActiveTargetPos(level, schematic);
        if (activeTaskPos != null) {
            this.sortedTargets.clear();
            return List.of(activeTaskPos);
        }
        if (!Configs.Print.PRINT_SORT_TARGETS.getBooleanValue()) {
            this.sortedTargets.clear();
            return this.getCachedFilteredIterationPositions(playerInteractionBox, ScanIntent.PRINT, pos -> true);
        }
        if (schematic == null || player == null) {
            this.sortedTargets.clear();
            return playerInteractionBox;
        }
        List<PrinterBox> scanSourceBoxes = this.getScanSourceBoxes(playerInteractionBox);
        if (scanSourceBoxes.isEmpty()) {
            this.sortedTargets.clear();
            return List.of();
        }
        return this.sortedTargets.iterable(scanSourceBoxes, level, schematic, player, getScanGuardLimit());
    }

    @Override
    public boolean canIterationBlockPos(BlockPos blockPos) {
        this.action = null;
        this.printTaskAction = null;
        WorldSchematic schematic = SchematicWorldHandler.getSchematicWorld();
        if (schematic == null) return false;
        if (InteractionUtils.INSTANCE.isRecentlyBroken(blockPos) && !this.printTasks.isActiveTaskPos(blockPos)) {
            return false;
        }
        this.ctx = new SchematicBlockContext(client, level, schematic, blockPos);
        if (this.shouldSkipRequiredState(ctx.requiredState)) {
            return false;
        }
        PrintTaskBuildResult taskResult = this.printTasks.buildAction(ctx);
        if (taskResult.handled()) {
            if (!taskResult.hasAction()) {
                return false;
            }
            this.action = taskResult.action();
            this.printTaskAction = taskResult.actionHandle();
            return true;
        }
//        Action action = guide.getAction(ctx);
        Optional<Action> action = Guides.INSTANCE.buildAction(ctx);
        if (action.isEmpty())
            return false;
        this.action = action.get();
        this.printTaskAction = this.printTasks.createActionHandle(ctx, this.action);
        return true;
    }

    private void updatePrintSkipCache() {
        List<String> skipList = Configs.Print.PRINT_SKIP_LIST.getStrings();
        if (skipList.equals(this.printSkipListCache)) {
            return;
        }
        this.printSkipListCache = new ArrayList<>(skipList);
        this.printSkipFilters = this.printSkipListCache.toArray(new String[0]);
    }

    private boolean shouldSkipRequiredState(BlockState requiredState) {
        if (!Configs.Print.PRINT_SKIP.getBooleanValue() || this.printSkipFilters.length == 0) {
            return false;
        }
        for (String filter : this.printSkipFilters) {
            if (FilterUtils.matchName(filter, requiredState)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void executeIteration(BlockPos blockPos, AtomicReference<Boolean> skipIteration) {
        PrintTaskAction taskAction = this.printTaskAction;
        PrintPlacementResult result = this.placementExecutor.execute(this.ctx, this.action, taskAction);
        if (!result.consumedEffectiveExecution()) {
            setIterationConsumedEffectiveExecution(false);
        }
        if (taskAction != null) {
            this.applyTaskEvent(taskAction, result.taskEvent());
        }
        if (result.skipIteration()) {
            skipIteration.set(true);
        }
        if (result.cooldownTicks() >= 0) {
            setBlockPosCooldown(blockPos, result.cooldownTicks());
        }
    }

    private void enterProcessMode() {
        this.groupCollecting = false;
        this.groupIter = this.pendingGroups.entrySet().iterator();
        this.currentGroupEntry = null;
    }

    /** 处理模式：按物品分组连续放置（组内同物品，switchToItems 只切换一次）。 */
    private void processGroups() {
        int budget = Math.max(this.getMaxEffectiveExecutionsPerTick(), 1);
        while (budget-- > 0) {
            if (this.currentGroupEntry == null) {
                if (this.groupIter != null && this.groupIter.hasNext()) {
                    this.currentGroupEntry = this.groupIter.next();
                } else {
                    // 全部组处理完成 → 回到收集模式
                    this.pendingGroups.clear();
                    this.groupIter = null;
                    this.currentGroupEntry = null;
                    this.pendingCount = 0;
                    this.groupCollecting = true;
                    return;
                }
            }
            ArrayDeque<BlockPos> queue = this.currentGroupEntry.getValue();
            BlockPos pos = queue.pollFirst();
            if (pos == null) {
                this.currentGroupEntry = null;
                continue;
            }
            this.executeGroupedPlacement(pos);
        }
    }

    /** 组内放置单个坐标（重建 ctx + action，同组方块相同，物品切换由 switchToItems 缓存保证只切一次）。 */
    private void executeGroupedPlacement(BlockPos blockPos) {
        WorldSchematic schematic = SchematicWorldHandler.getSchematicWorld();
        if (schematic == null || this.level == null || this.player == null) {
            return;
        }
        SchematicBlockContext ctx = new SchematicBlockContext(this.client, this.level, schematic, blockPos);
        if (this.shouldSkipRequiredState(ctx.requiredState)) {
            return;
        }
        Optional<Action> action = Guides.INSTANCE.buildAction(ctx);
        if (action.isEmpty()) {
            return;
        }
        PrintTaskAction taskAction = this.printTasks.createActionHandle(ctx, action.get());
        PrintPlacementResult result = this.placementExecutor.execute(ctx, action.get(), taskAction);
        if (taskAction != null) {
            switch (result.taskEvent()) {
                case SUCCESS -> this.printTasks.onActionSuccess(taskAction, ctx, action.get());
                case QUEUED -> this.printTasks.onActionQueued(taskAction, ctx, action.get());
                case FAILURE -> this.printTasks.onActionFailure(taskAction, ctx, action.get());
            }
        }
        if (result.cooldownTicks() >= 0) {
            setBlockPosCooldown(blockPos, result.cooldownTicks());
        }
    }

    private void applyTaskEvent(PrintTaskAction taskAction, PrintPlacementResult.TaskEvent taskEvent) {
        switch (taskEvent) {
            case SUCCESS -> this.printTasks.onActionSuccess(taskAction, this.ctx, this.action);
            case QUEUED -> this.printTasks.onActionQueued(taskAction, this.ctx, this.action);
            case FAILURE -> this.printTasks.onActionFailure(taskAction, this.ctx, this.action);
        }
    }

    @Override
    public boolean isBlockPosOnCooldown(@Nullable BlockPos pos) {
        if (this.printTasks.isActiveTaskPos(pos)) {
            return false;
        }
        return super.isBlockPosOnCooldown(pos);
    }

}

