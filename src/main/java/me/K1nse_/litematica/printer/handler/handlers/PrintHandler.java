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

    // ---- 打印增强（移植自 EMT）----
    /** 未确认放置重试表 */
    private final Map<Long, RetryEntry> retryTable = new LinkedHashMap<>();
    private static final int MAX_PLACE_RETRIES = 2;
    @Nullable
    private Item activePlacementItem;
    private long nextPlacementItemTick;

    private record RetryEntry(BlockPos pos, BlockState requiredState, long sentTick, int attempts) {
    }

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
        this.autoEnableForLoadedSchematic();
        this.processRetries();
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
        if (this.shouldDeferShulker(ctx)) {
            return false;
        }
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
        if (!this.canAffordReserve(this.action, this.ctx)) {
            this.setBlockPosCooldown(blockPos, ConfigUtils.getPlaceCooldown());
            setIterationConsumedEffectiveExecution(false);
            return;
        }
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
        if (this.ctx != null && result.taskEvent() != PrintPlacementResult.TaskEvent.FAILURE) {
            this.trackForRetry(blockPos, this.ctx.requiredState);
            this.trackShulkerPlaced(blockPos, this.ctx.requiredState);
        }
        }
    }

    private void enterProcessMode() {
        // 同物品优先（移植自 EMT）：先把当前手持物品的分组提到最前
        if (Configs.Print.PLACE_SAME_ITEM_FIRST.getBooleanValue() && this.player != null && !this.pendingGroups.isEmpty()) {
            Item held = this.player.getMainHandItem().getItem();
            if (held != net.minecraft.world.item.Items.AIR && this.pendingGroups.containsKey(held)) {
                Map<Item, ArrayDeque<BlockPos>> reordered = new LinkedHashMap<>();
                reordered.put(held, this.pendingGroups.get(held));
                for (Map.Entry<Item, ArrayDeque<BlockPos>> entry : this.pendingGroups.entrySet()) {
                    if (entry.getKey() != held) {
                        reordered.put(entry.getKey(), entry.getValue());
                    }
                }
                this.pendingGroups.clear();
                this.pendingGroups.putAll(reordered);
            }
        }
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
            if (!this.canSwitchToPlacementItem(this.currentGroupEntry.getKey())) {
                return;
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
        if (!this.canAffordReserve(action.get(), ctx)) {
            this.setBlockPosCooldown(blockPos, ConfigUtils.getPlaceCooldown());
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
        if (result.taskEvent() != PrintPlacementResult.TaskEvent.FAILURE) {
            this.trackForRetry(blockPos, ctx.requiredState);
            this.trackShulkerPlaced(blockPos, ctx.requiredState);
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


    // ==================== 打印增强实现（移植自 EMT）====================

    /** 加载投影后自动开启工作开关 */
    private void autoEnableForLoadedSchematic() {
        if (!Configs.Core.AUTO_ENABLE_PRINTER.getBooleanValue() || Configs.Core.WORK_SWITCH.getBooleanValue()) {
            return;
        }
        if (this.level == null) {
            return;
        }
        if (SchematicWorldHandler.getSchematicWorld() == null) {
            return;
        }
        Configs.Core.WORK_SWITCH.setBooleanValue(true);
    }

    /** 纯发包放置的未确认重试：超过冷却仍未变成目标方块则重放一次 */
    private void processRetries() {
        if (this.retryTable.isEmpty()) {
            return;
        }
        WorldSchematic schematic = SchematicWorldHandler.getSchematicWorld();
        if (this.level == null || schematic == null) {
            this.retryTable.clear();
            return;
        }
        long now = this.level.getGameTime();
        int cooldown = Math.max(4, ConfigUtils.getPlaceCooldown());
        List<BlockPos> retryPositions = new ArrayList<>();
        Iterator<Map.Entry<Long, RetryEntry>> iterator = this.retryTable.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Long, RetryEntry> entry = iterator.next();
            RetryEntry record = entry.getValue();
            BlockPos pos = record.pos();
            BlockState actual = this.level.getBlockState(pos);
            if (!actual.isAir() && actual.getBlock() == record.requiredState().getBlock()) {
                iterator.remove();
                continue;
            }
            if (now - record.sentTick() < cooldown) {
                continue;
            }
            if (record.attempts() >= MAX_PLACE_RETRIES) {
                iterator.remove();
                continue;
            }
            entry.setValue(new RetryEntry(pos, record.requiredState(), now, record.attempts() + 1));
            retryPositions.add(pos);
        }
        for (BlockPos pos : retryPositions) {
            SchematicBlockContext retryCtx = new SchematicBlockContext(this.client, this.level, schematic, pos);
            Optional<Action> retryAction = Guides.INSTANCE.buildAction(retryCtx);
            retryAction.ifPresent(action -> this.placementExecutor.execute(retryCtx, action,
                    this.printTasks.createActionHandle(retryCtx, action)));
        }
    }

    private void trackForRetry(BlockPos pos, @Nullable BlockState requiredState) {
        if (pos == null || requiredState == null || this.level == null) {
            return;
        }
        if (!Configs.Print.PRINT_USE_PACKET.getBooleanValue()) {
            return;
        }
        this.retryTable.put(pos.asLong(), new RetryEntry(pos.immutable(), requiredState, this.level.getGameTime(), 0));
        if (this.retryTable.size() > 512) {
            this.retryTable.clear();
        }
    }

    private void trackShulkerPlaced(BlockPos pos, @Nullable BlockState requiredState) {
        if (pos != null && requiredState != null && this.level != null
                && requiredState.getBlock() instanceof net.minecraft.world.level.block.ShulkerBoxBlock) {
            me.K1nse_.litematica.printer.guide.guides.ShulkerPlacementGuard.INSTANCE.onPlaced(pos, this.level.getGameTime());
        }
    }

    /** 预留物品：背包里该物品数量必须大于预留数才允许放置 */
    private boolean canAffordReserve(@Nullable Action action, @Nullable SchematicBlockContext ctx) {
        if (action == null || ctx == null || this.player == null) {
            return true;
        }
        if (!Configs.Print.RESERVE_ITEMS.getBooleanValue() || this.player.getAbilities().instabuild) {
            return true;
        }
        int reserve = Configs.Print.RESERVE_ITEM_COUNT.getIntegerValue();
        if (reserve <= 0) {
            return true;
        }
        Item[] requiredItems = action.getRequiredItems(ctx.requiredState.getBlock());
        for (Item item : requiredItems) {
            if (item == null || item == net.minecraft.world.item.Items.AIR) {
                continue;
            }
            int count = InventoryUtils.countMatchingMainInventory(this.player, stack -> stack.is(item));
            if (count <= reserve) {
                return false;
            }
        }
        return true;
    }

    /** 潜影盒策略：跳过 / 后置 / 仅空盒 */
    private boolean shouldDeferShulker(SchematicBlockContext ctx) {
        if (!(ctx.requiredState.getBlock() instanceof net.minecraft.world.level.block.ShulkerBoxBlock)) {
            return false;
        }
        if (Configs.Print.SKIP_SHULKER.getBooleanValue()) {
            return true;
        }
        if (Configs.Print.SHULKER_AFTER_ORDINARY.getBooleanValue() && this.hasPendingOrdinaryTargets()) {
            this.setBlockPosCooldown(ctx.blockPos, ConfigUtils.getPlaceCooldown());
            return true;
        }
        if (Configs.Print.ONLY_EMPTY_SHULKER.getBooleanValue()) {
            me.K1nse_.litematica.printer.guide.guides.ShulkerPlacementGuard.GuardResult result =
                    me.K1nse_.litematica.printer.guide.guides.ShulkerPlacementGuard.INSTANCE.evaluate(ctx);
            if (result != me.K1nse_.litematica.printer.guide.guides.ShulkerPlacementGuard.GuardResult.READY) {
                this.setBlockPosCooldown(ctx.blockPos, ConfigUtils.getPlaceCooldown());
                return true;
            }
        }
        return false;
    }

    /** 分组队列里是否还有非潜影盒方块待放 */
    private boolean hasPendingOrdinaryTargets() {
        for (Map.Entry<Item, ArrayDeque<BlockPos>> entry : this.pendingGroups.entrySet()) {
            if (entry.getValue().isEmpty()) {
                continue;
            }
            if (!(entry.getKey() instanceof net.minecraft.world.item.BlockItem blockItem)
                    || !(blockItem.getBlock() instanceof net.minecraft.world.level.block.ShulkerBoxBlock)) {
                return true;
            }
        }
        return false;
    }

    /** 切物品节流：同物品直接放，换物品要等 itemSwitchInterval */
    private boolean canSwitchToPlacementItem(Item item) {
        if (this.level == null) {
            return true;
        }
        long tick = this.level.getGameTime();
        if (this.activePlacementItem == item) {
            return true;
        }
        if (tick < this.nextPlacementItemTick) {
            return false;
        }
        int interval = Configs.Print.ITEM_SWITCH_INTERVAL.getIntegerValue();
        this.activePlacementItem = item;
        this.nextPlacementItemTick = tick + interval;
        return true;
    }
}
