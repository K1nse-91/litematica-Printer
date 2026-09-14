package me.K1nse_.litematica.printer.handler.handlers;

import fi.dy.masa.malilib.util.restrictions.UsageRestriction;
import me.K1nse_.litematica.printer.config.Configs;
import me.K1nse_.litematica.printer.enums.ExcavateListMode;
import me.K1nse_.litematica.printer.enums.PrintModeType;
import me.K1nse_.litematica.printer.handler.Module;
import me.K1nse_.litematica.printer.handler.TickContext;
import me.K1nse_.litematica.printer.handler.scan.ScanCache;
import me.K1nse_.litematica.printer.handler.scan.ScanIntent;
import me.K1nse_.litematica.printer.mixin_extension.BlockBreakResult;
import me.K1nse_.litematica.printer.printer.ActionManager;
import me.K1nse_.litematica.printer.printer.PrinterBox;
import me.K1nse_.litematica.printer.printer.PrinterUtils;
import me.K1nse_.litematica.printer.utils.ConfigUtils;
import me.K1nse_.litematica.printer.utils.InteractionUtils;
import me.K1nse_.litematica.printer.utils.InventoryUtils;
import me.K1nse_.litematica.printer.utils.UsageRestrictionCache;
import me.K1nse_.litematica.printer.utils.minecraft.BlockUtils;
import me.K1nse_.litematica.printer.utils.mods.ModLoadUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

import static fi.dy.masa.tweakeroo.config.Configs.Lists.BLOCK_TYPE_BREAK_RESTRICTION_BLACKLIST;
import static fi.dy.masa.tweakeroo.config.Configs.Lists.BLOCK_TYPE_BREAK_RESTRICTION_WHITELIST;
import static fi.dy.masa.tweakeroo.tweaks.PlacementTweaks.BLOCK_TYPE_BREAK_RESTRICTION;

public class MineHandler extends Module {
    public static final String NAME = "mine";
    private static final UsageRestrictionCache MINE_RESTRICTION_CACHE = new UsageRestrictionCache();

    private final MineBreakExecutor analyzer = new MineBreakExecutor();
    private final MineToolSession toolSession = new MineToolSession();
    private final MineCandidateQueue candidates = new MineCandidateQueue();
    private final Set<BlockPos> trenchFillTargets = new LinkedHashSet<>();
    private final Set<BlockPos> trenchWaterloggedTargets = new LinkedHashSet<>();
    private final Map<BlockPos, Long> trenchFillInFlight = new HashMap<>();
    private int trenchFillSentThisTick;
    private long lastTrenchFillTick = Long.MIN_VALUE;
    @Nullable
    private BlockPos activeMinePos;

    public MineHandler() {
        super(NAME, PrintModeType.MINE, Configs.Core.MINE, Configs.Mine.MINE_SELECTION_TYPE, true);
    }

    @Override
    public void tick() {
        this.tick(TickContext.capture());
    }

    @Override
    public void tick(TickContext context) {
        if (!ConfigUtils.isEnable() || !ConfigUtils.isMineMode()) {
            this.analyzer.reset();
            this.activeMinePos = null;
            this.toolSession.reset();
            this.trenchFillTargets.clear();
            this.trenchWaterloggedTargets.clear();
            this.trenchFillInFlight.clear();
            this.lastTrenchFillTick = Long.MIN_VALUE;
        }
        super.tick(context);
    }

    public int getRetryQueueSize() {
        return this.candidates.size() + this.trenchFillTargets.size()
                + this.trenchWaterloggedTargets.size()
                + (this.activeMinePos == null ? 0 : 1);
    }

    public static boolean mineRestriction(BlockState blockState) {
        if (!InteractionUtils.breakRestriction(blockState)) {
            return false;
        }
        if (Configs.Mine.EXCAVATE_LIMITER.getOptionListValue().equals(ExcavateListMode.TWEAKEROO)) {
            if (!ModLoadUtils.isTweakerooLoaded()) return true;
            UsageRestriction.ListType listType = BLOCK_TYPE_BREAK_RESTRICTION.getListType();
            return MINE_RESTRICTION_CACHE.allows("tweakeroo", listType,
                    BLOCK_TYPE_BREAK_RESTRICTION_BLACKLIST.getStrings(),
                    BLOCK_TYPE_BREAK_RESTRICTION_WHITELIST.getStrings(),
                    blockState);
        }

        Object optionListValue = Configs.Mine.EXCAVATE_LIMIT.getOptionListValue();
        UsageRestriction.ListType listType = optionListValue instanceof UsageRestriction.ListType type
                ? type
                : UsageRestriction.ListType.NONE;
        return MINE_RESTRICTION_CACHE.allows("custom", listType,
                Configs.Mine.EXCAVATE_BLACKLIST.getStrings(),
                Configs.Mine.EXCAVATE_WHITELIST.getStrings(),
                blockState);
    }

    @Override
    protected int getTickInterval() {
        return 0;
    }

    @Override
    protected int getMaxEffectiveExecutionsPerTick() {
        return 0;
    }

    @Override
    protected int getScanGuardLimit() {
        return 0;
    }

    @Override
    protected Iterable<BlockPos> getIterationPositions(PrinterBox playerInteractionBox) {
        if (this.hasTrenchFillWork()) {
            if (!this.trenchWaterloggedTargets.isEmpty()) {
                return List.of(this.trenchWaterloggedTargets.iterator().next());
            }
            List<BlockPos> fillPositions = new ArrayList<>();
            for (BlockPos pos : this.trenchFillTargets) {
                if (!this.trenchFillInFlight.containsKey(pos)
                        && this.level != null && this.isFluid(this.level.getBlockState(pos))) {
                    fillPositions.add(pos);
                }
            }
            if (!fillPositions.isEmpty()) {
                Iterable<BlockPos> minePositions = this.candidates.isEmpty()
                        ? this.getMineScanPositions(playerInteractionBox)
                        : List.of();
                return () -> concatIterables(fillPositions, minePositions).iterator();
            }
        }
        if (!this.candidates.isEmpty()) {
            return List.of();
        }
        return this.getMineScanPositions(playerInteractionBox);
    }

    private Iterable<BlockPos> getMineScanPositions(PrinterBox playerInteractionBox) {
        List<PrinterBox> scanSourceBoxes = this.getScanSourceBoxes(playerInteractionBox);
        if (scanSourceBoxes.isEmpty()) {
            return List.of();
        }
        Predicate<BlockPos> selectionPredicate = this.createSelectionRangePredicate();

        return ScanCache.INSTANCE.iterable(
                NAME,
                scanSourceBoxes,
                this.level,
                fi.dy.masa.litematica.world.SchematicWorldHandler.getSchematicWorld(),
                this.player,
                this.getScanGuardLimit(),
                ScanIntent.MINE,
                this::isMineScanCandidate,
                pos -> this.canReachIterationPosition(pos) && selectionPredicate.test(pos)
        );
    }

    private static List<BlockPos> concatIterables(Iterable<BlockPos> first, Iterable<BlockPos> second) {
        List<BlockPos> merged = new ArrayList<>();
        for (BlockPos pos : first) {
            merged.add(pos);
        }
        for (BlockPos pos : second) {
            merged.add(pos);
        }
        return merged;
    }

    @Override
    protected void preprocess() {
        this.analyzer.beginTick();
        this.toolSession.beginTick();
        this.trenchFillSentThisTick = 0;
        this.refreshTrenchFillTargets();
        this.continueActiveMineTarget();
    }

    @Override
    protected boolean hasPendingIterationWork() {
        return !this.candidates.isEmpty() || this.hasTrenchFillWork();
    }

    @Override
    public int getPendingIterationWorkCount() {
        return this.getRetryQueueSize();
    }

    @Override
    protected void onRuntimeReset() {
        this.candidates.clear();
        this.activeMinePos = null;
        this.trenchFillTargets.clear();
        this.trenchWaterloggedTargets.clear();
        this.trenchFillInFlight.clear();
        this.lastTrenchFillTick = Long.MIN_VALUE;
        this.analyzer.reset();
        this.toolSession.reset();
    }

    @Override
    protected boolean canIterate() {
        return this.activeMinePos == null && !InteractionUtils.INSTANCE.hasActiveDestroyTarget();
    }

    @Override
    protected boolean iterationPositionsPrefilterReachAndSelection() {
        // 候选/挖沟目标在进入迭代前已各自过滤（扫描器或刷新逻辑），这里不再重复选区校验：
        // 挖沟模式的填充目标可能位于直线选区两侧之外。
        return true;
    }

    @Override
    protected boolean iterationPositionsAreExactCandidates() {
        return true;
    }

    @Override
    public boolean canIterationBlockPos(BlockPos pos) {
        if (this.hasTrenchFillWork() && this.trenchFillTargets.contains(pos)) {
            return this.level != null
                    && !this.trenchFillInFlight.containsKey(pos)
                    && this.isFluid(this.level.getBlockState(pos));
        }
        if (this.hasTrenchFillWork() && this.trenchWaterloggedTargets.contains(pos)) {
            return this.level != null && this.isWaterlogged(this.level.getBlockState(pos));
        }
        return this.isMineScanCandidate(pos);
    }

    @Override
    protected void executeIteration(BlockPos blockPos, AtomicReference<Boolean> skipIteration) {
        if (this.hasTrenchFillWork() && this.trenchFillTargets.contains(blockPos)) {
            this.executeTrenchFill(blockPos, skipIteration);
            return;
        }
        if (this.hasTrenchFillWork() && this.trenchWaterloggedTargets.contains(blockPos)) {
            this.executeTrenchWaterloggedBreak(blockPos, skipIteration);
            return;
        }
        MineBreakExecutor.Target target = this.analyzer.analyze(blockPos);
        if (target == null) {
            this.setIterationConsumedEffectiveExecution(false);
            return;
        }
        this.candidates.add(target);
        this.setIterationConsumedEffectiveExecution(false);
    }

    @Override
    protected void stopIteration(boolean interrupt) {
        if (ActionManager.INSTANCE.needWaitModifyLook || this.activeMinePos != null || this.candidates.isEmpty()) {
            return;
        }
        List<MineBreakExecutor.Target> orderedCandidates = this.refreshCandidateTargets();
        if (orderedCandidates.isEmpty()) {
            return;
        }
        MineBreakExecutor.Target nearest = orderedCandidates.get(0);
        MineBreakExecutor.Target selected = this.toolSession.selectTarget(orderedCandidates, this.analyzer, this.player);
        this.executeToolSession(selected, orderedCandidates);
    }

    /**
     * 每 tick 重验证保留的候选：目标消失/被限制/进入冷却就移除，其余重新分析刷新工具与进度。
     * 这样扫描短暂为空不会丢弃已受理的目标。
     */
    private List<MineBreakExecutor.Target> refreshCandidateTargets() {
        List<MineBreakExecutor.Target> refreshed = new ArrayList<>();
        for (MineBreakExecutor.Target queued : this.candidates.snapshot()) {
            BlockPos pos = queued.pos();
            if (!this.isMineScanCandidate(pos)) {
                this.candidates.remove(pos);
                continue;
            }
            MineBreakExecutor.Target current = this.analyzer.analyze(pos);
            if (current == null) {
                this.candidates.remove(pos);
                continue;
            }
            this.candidates.add(current);
            refreshed.add(current);
        }
        refreshed.sort(this.toolSession.comparator(this.player));
        return refreshed;
    }

    private void continueActiveMineTarget() {
        BlockPos pos = this.activeMinePos;
        if (pos == null) {
            return;
        }
        String dropReason = this.activeMineDropReason(pos);
        if (dropReason != null) {
            MineDebugLog.write("mine continue dropped pos=" + MineDebugLog.pos(pos) + " reason=" + dropReason);
            this.activeMinePos = null;
            this.setBlockPosCooldown(pos, ConfigUtils.getBreakCooldown());
            return;
        }
        BlockBreakResult result = InteractionUtils.INSTANCE.continueDestroyBlockForMine(pos, Direction.DOWN, true);
        MineResultReporter.record(pos, result);
        this.toolSession.onTargetResolved(result, pos);
        if (result != BlockBreakResult.IN_PROGRESS) {
            this.activeMinePos = null;
            this.setBlockPosCooldown(pos, ConfigUtils.getBreakCooldown());
        }
    }

    @Nullable
    private String activeMineDropReason(BlockPos pos) {
        if (pos == null || this.level == null) {
            return "no_level";
        }
        if (!this.canReachIterationPosition(pos)) {
            return "out_of_reach";
        }
        if (!InteractionUtils.canBreakBlock(pos)) {
            return "cannot_break";
        }
        if (!mineRestriction(this.level.getBlockState(pos))) {
            return "restriction";
        }
        return null;
    }

    private boolean isMineScanCandidate(BlockPos pos) {
        if (pos == null || this.level == null || this.player == null || this.gameMode == null) {
            return false;
        }

        if (this.isBlockPosOnCooldown(pos)
                || InteractionUtils.INSTANCE.isRecentlyBroken(pos)
                || InteractionUtils.INSTANCE.isPendingDelayedDestroy(pos)) {
            return false;
        }

        BlockState state = this.level.getBlockState(pos);
        if (state.isAir() || state.getBlock() instanceof LiquidBlock) {
            return false;
        }

        if (Configs.Break.BREAK_CHECK_HARDNESS.getBooleanValue() && state.getBlock().defaultDestroyTime() < 0) {
            return false;
        }

        return this.canReachIterationPosition(pos)
                && !this.player.blockActionRestricted(this.level, pos, this.gameMode.getPlayerMode())
                && mineRestriction(state);
    }

    private void executeToolSession(MineBreakExecutor.Target firstTarget,
                                    List<MineBreakExecutor.Target> orderedCandidates) {
        this.toolSession.startSession(firstTarget);
        if (!this.toolSession.ensureHandToolProtected(this.player, firstTarget)) {
            return;
        }
        BlockBreakResult result = this.executeSessionTarget(firstTarget, !this.analyzer.isCurrentToolEffective(firstTarget));
        if (this.toolSession.shouldStop(result, this.activeMinePos != null)) {
            return;
        }
        for (MineBreakExecutor.Target queuedTarget : orderedCandidates) {
            if (queuedTarget.pos().equals(firstTarget.pos())) {
                continue;
            }
            if (!this.toolSession.hasInstantBudget()) {
                break;
            }
            if (!this.toolSession.matchesSessionTool(this.analyzer, queuedTarget)) {
                continue;
            }
            if (!this.toolSession.isInsideFrontier(this.player, queuedTarget, MineToolSession.distanceScore(this.player, orderedCandidates.get(0)))) {
                break;
            }
            if (!this.toolSession.ensureHandToolProtected(this.player, queuedTarget)) {
                break;
            }
            result = this.executeSessionTarget(queuedTarget, false);
            if (this.toolSession.shouldStop(result, this.activeMinePos != null)) {
                break;
            }
        }
    }

    private BlockBreakResult executeSessionTarget(MineBreakExecutor.Target target, boolean allowToolSwitch) {
        boolean switchForRecovery = this.player != null
                && target.shouldSwitchToRecoveryTool(this.player.getMainHandItem());
        BlockBreakResult result = this.executeMineTarget(target, allowToolSwitch || switchForRecovery);
        if (result != BlockBreakResult.FAILED) {
            this.setBlockPosCooldown(target.pos(), ConfigUtils.getBreakCooldown());
        }
        if (result == BlockBreakResult.COMPLETED || result == BlockBreakResult.COMPLETED_WAIT) {
            this.toolSession.consumeInstantBudget();
        }
        if (result == BlockBreakResult.IN_PROGRESS
                || result == BlockBreakResult.COMPLETED
                || result == BlockBreakResult.COMPLETED_WAIT) {
            this.toolSession.consumeAction();
        }
        this.toolSession.onTargetResolved(result, target.pos());
        MineResultReporter.record(target.pos(), result);
        if (result != BlockBreakResult.IN_PROGRESS) {
            this.candidates.remove(target.pos());
        }
        return result;
    }

    private BlockBreakResult executeMineTarget(MineBreakExecutor.Target target, boolean allowToolSwitch) {
        BlockBreakResult result = InteractionUtils.INSTANCE.continueDestroyBlockForMine(target.pos(), Direction.DOWN, allowToolSwitch);
        if (result == BlockBreakResult.IN_PROGRESS) {
            this.activeMinePos = target.pos();
        }
        return result;
    }

    // ==================== 挖沟模式 ====================

    private boolean hasTrenchFillWork() {
        return Configs.Mine.MINE_TRENCH_MODE.getBooleanValue()
                && (!this.trenchFillTargets.isEmpty()
                || !this.trenchWaterloggedTargets.isEmpty()
                || !this.trenchFillInFlight.isEmpty());
    }

    private void refreshTrenchFillTargets() {
        if (!Configs.Mine.MINE_TRENCH_MODE.getBooleanValue()
                || this.level == null || this.getPlayerInteractionBox() == null) {
            this.trenchFillTargets.clear();
            this.trenchWaterloggedTargets.clear();
            this.trenchFillInFlight.clear();
            return;
        }
        PrinterBox interactionBox = this.getPlayerInteractionBox().get();
        if (interactionBox == null) {
            return;
        }
        Set<BlockPos> discovered = new LinkedHashSet<>();
        Set<BlockPos> waterlogged = new LinkedHashSet<>();
        for (PrinterBox box : this.getScanSourceBoxes(interactionBox)) {
            // 战壕 = 一格宽的直线选区；非线状选区安全空转
            if (box.minX != box.maxX && box.minZ != box.maxZ) {
                continue;
            }
            Direction[] sides = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
            for (BlockPos pos : box) {
                BlockState state = this.level.getBlockState(pos);
                if (this.isFluid(state)) {
                    discovered.add(pos.immutable());
                } else if (this.isWaterlogged(state)) {
                    waterlogged.add(pos.immutable());
                }
                for (Direction side : sides) {
                    BlockPos outside = pos.relative(side);
                    if (!box.contains(outside)) {
                        BlockState outsideState = this.level.getBlockState(outside);
                        if (this.isFluid(outsideState)) {
                            discovered.add(outside.immutable());
                        } else if (this.isWaterlogged(outsideState)) {
                            waterlogged.add(outside.immutable());
                        }
                    }
                }
            }
        }
        this.trenchWaterloggedTargets.removeIf(pos -> !waterlogged.contains(pos));
        this.trenchWaterloggedTargets.addAll(waterlogged);
        this.trenchFillTargets.removeIf(pos -> !discovered.contains(pos)
                && !this.trenchFillInFlight.containsKey(pos));
        this.trenchFillTargets.addAll(discovered);
        long now = this.level.getGameTime();
        this.trenchFillInFlight.entrySet().removeIf(entry ->
                !this.isFluid(this.level.getBlockState(entry.getKey()))
                        || now - entry.getValue() > 40L);
    }

    private void executeTrenchFill(BlockPos pos, AtomicReference<Boolean> skipIteration) {
        if (this.level == null || this.player == null || !this.isFluid(this.level.getBlockState(pos))) {
            this.trenchFillTargets.remove(pos);
            return;
        }
        int placementLimit = Configs.Placement.PLACE_BLOCKS_PER_TICK.getIntegerValue();
        if (placementLimit > 0 && this.trenchFillSentThisTick >= placementLimit) {
            skipIteration.set(true);
            return;
        }
        long now = this.level.getGameTime();
        int placementInterval = Configs.Placement.PLACE_INTERVAL.getIntegerValue();
        if (placementInterval > 0 && this.lastTrenchFillTick != Long.MIN_VALUE
                && now - this.lastTrenchFillTick < placementInterval) {
            skipIteration.set(true);
            return;
        }
        if (!InventoryUtils.switchToItems(this.player, new Item[]{Items.SAND})) {
            skipIteration.set(true);
            return;
        }
        Direction supportDirection = this.findTrenchSupport(pos);
        if (supportDirection == null) {
            skipIteration.set(true);
            return;
        }
        ActionManager.INSTANCE.queueClick(
                pos.relative(supportDirection),
                supportDirection.getOpposite(),
                Vec3.ZERO,
                false
        );
        if (ActionManager.INSTANCE.sendQueue(this.player).needWaitModifyLook) {
            skipIteration.set(true);
            return;
        }
        this.trenchFillInFlight.put(pos.immutable(), now);
        this.trenchFillSentThisTick++;
        this.lastTrenchFillTick = now;
    }

    private void executeTrenchWaterloggedBreak(BlockPos pos, AtomicReference<Boolean> skipIteration) {
        if (this.level == null || !this.isWaterlogged(this.level.getBlockState(pos))) {
            this.trenchWaterloggedTargets.remove(pos);
            return;
        }
        if (this.activeMinePos != null && !this.activeMinePos.equals(pos)) {
            skipIteration.set(true);
            return;
        }
        BlockBreakResult result = InteractionUtils.INSTANCE.continueDestroyBlockForMine(pos, Direction.DOWN, true);
        MineResultReporter.record(pos, result);
        if (result == BlockBreakResult.IN_PROGRESS) {
            this.activeMinePos = pos.immutable();
        } else {
            this.trenchWaterloggedTargets.remove(pos);
        }
        skipIteration.set(true);
    }

    private Direction findTrenchSupport(BlockPos pos) {
        Direction[] order = {Direction.DOWN, Direction.NORTH, Direction.SOUTH,
                Direction.EAST, Direction.WEST, Direction.UP};
        for (Direction direction : order) {
            BlockPos support = pos.relative(direction);
            BlockState state = this.level.getBlockState(support);
            if (!BlockUtils.isReplaceable(state) && PrinterUtils.canBeClicked(this.level, support)) {
                return direction;
            }
        }
        return null;
    }

    private boolean isFluid(BlockState state) {
        return state != null && state.getBlock() instanceof LiquidBlock;
    }

    private boolean isWaterlogged(BlockState state) {
        return state != null
                && !(state.getBlock() instanceof LiquidBlock)
                && !state.getFluidState().isEmpty();
    }
}
