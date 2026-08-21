package me.K1nse_.litematica.printer.handler.handlers;

import me.K1nse_.litematica.printer.config.Configs;
import me.K1nse_.litematica.printer.enums.PrintModeType;
import me.K1nse_.litematica.printer.handler.HudStatsManager;
import me.K1nse_.litematica.printer.handler.Module;
import me.K1nse_.litematica.printer.handler.scan.ScanCache;
import me.K1nse_.litematica.printer.handler.scan.ScanIntent;
import me.K1nse_.litematica.printer.printer.ActionManager;
import me.K1nse_.litematica.printer.printer.PrinterBox;
import me.K1nse_.litematica.printer.utils.InventoryUtils;
import me.K1nse_.litematica.printer.utils.RegistryFilterResolver;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

public class FluidHandler extends Module {
    public final static String NAME = "fluid";

    private List<String> fillBlocks = new ArrayList<>();
    private List<Item> fillItems = new ArrayList<>();
    private Item[] fillItemArray = new Item[0];

    private List<String> fluidBlocks = new ArrayList<>();
    private Set<Fluid> fluids = Set.of();

    public FluidHandler() {
        super(NAME, PrintModeType.FLUID, Configs.Core.FLUID, Configs.Fluid.FLUID_SELECTION_TYPE, true);
    }

    @Override
    protected int getTickInterval() {
        return Configs.Placement.PLACE_INTERVAL.getIntegerValue();
    }

    @Override
    protected int getMaxEffectiveExecutionsPerTick() {
        return Configs.Placement.PLACE_BLOCKS_PER_TICK.getIntegerValue();
    }

    @Override
    protected void preprocess() {
        // 填充方块
        List<String> fileBlocks = Configs.Fluid.FLUID_REPLACE_BLOCK_LIST.getStrings();
        if (!fileBlocks.equals(fillBlocks)) {
            fillBlocks = new ArrayList<>(fileBlocks);
            fillItems = new ArrayList<>();
            if (!fileBlocks.isEmpty()) {
                fillItems.addAll(RegistryFilterResolver.resolveItems(fillBlocks));
            }
            fillItemArray = fillItems.toArray(new Item[0]);
        }
        // 流体方块
        List<String> fluidBlocks = Configs.Fluid.FLUID_LIST.getStrings();
        if (!fluidBlocks.equals(this.fluidBlocks)) {
            this.fluidBlocks = new ArrayList<>(fluidBlocks);
            fluids = fluidBlocks.isEmpty() ? Set.of() : RegistryFilterResolver.resolveFluids(this.fluidBlocks);
        }
        if (fillItems.isEmpty()) {
            HudStatsManager.INSTANCE.recordStatus(HudStatsManager.Mode.FLUID, "无流体填充方块");
        } else if (this.fluidBlocks.isEmpty()) {
            HudStatsManager.INSTANCE.recordStatus(HudStatsManager.Mode.FLUID, "无目标流体配置");
        } else {
            HudStatsManager.INSTANCE.recordStatus(HudStatsManager.Mode.FLUID, "运行中");
        }
    }

    @Override
    protected boolean canIterate() {
        return !fillItems.isEmpty() && !fluidBlocks.isEmpty();
    }

    @Override
    protected boolean iterationPositionsPrefilterReachAndSelection() {
        return true;
    }

    @Override
    protected boolean iterationPositionsAreExactCandidates() {
        return true;
    }

    @Override
    protected Iterable<BlockPos> getIterationPositions(PrinterBox playerInteractionBox) {
        List<PrinterBox> scanSourceBoxes = this.getScanSourceBoxes(playerInteractionBox);
        if (scanSourceBoxes.isEmpty()) {
            return List.of();
        }
        Predicate<BlockPos> selectionPredicate = this.createSelectionRangePredicate();
        
        return ScanCache.INSTANCE.iterable(
                NAME,
                scanSourceBoxes,
                this.level,
                null,
                this.player,
                this.getScanGuardLimit(),
                ScanIntent.FLUID,
                this::isTargetFluid,
                pos -> this.canReachIterationPosition(pos) && selectionPredicate.test(pos)
        );
    }

    @Override
    public boolean canIterationBlockPos(BlockPos blockPos) {
        return this.isTargetFluid(blockPos);
    }

    @Override
    protected void executeIteration(BlockPos blockPos, AtomicReference<Boolean> skipIteration) {
        FluidState fluidState = level.getBlockState(blockPos).getFluidState();
        if (!this.isTargetFluid(fluidState)) {
            setIterationConsumedEffectiveExecution(false);
            return;
        }
        if (!InventoryUtils.switchToItems(player, fillItemArray)) {
            HudStatsManager.INSTANCE.recordDeferred(HudStatsManager.Mode.FLUID, "缺少流体填充方块");
            setIterationConsumedEffectiveExecution(false);
            if (me.K1nse_.litematica.printer.printer.zxy.inventory.InventoryUtils.shouldPauseForSwitchRequest()
                    || me.K1nse_.litematica.printer.utils.mods.TakeItOutUtils.isAwaitingStack()) {
                skipIteration.set(true);
            }
            return;
        }
        ActionManager.INSTANCE.queueClick(
                Configs.Print.PLACE_IN_AIR.getBooleanValue() ? blockPos : blockPos.above(),
                Direction.DOWN,
                Vec3.ZERO,
                false
        );
        HudStatsManager.INSTANCE.trackExpectedBlockChange(HudStatsManager.Mode.FLUID, blockPos, level.getBlockState(blockPos));
        HudStatsManager.INSTANCE.recordRateUnit(HudStatsManager.Mode.FLUID, 1);
        if (ActionManager.INSTANCE.sendQueue(player).needWaitModifyLook) {
            HudStatsManager.INSTANCE.recordDeferred(HudStatsManager.Mode.FLUID, "等待转头");
            skipIteration.set(true);
        } else {
            HudStatsManager.INSTANCE.recordStatus(HudStatsManager.Mode.FLUID, "运行中");
        }
    }

    private boolean isTargetFluid(BlockPos blockPos) {
        return this.level != null && this.isTargetFluid(this.level.getBlockState(blockPos).getFluidState());
    }

    private boolean isTargetFluid(FluidState fluidState) {
        return fluids.contains(fluidState.getType())
                && (Configs.Fluid.FILL_FLOWING_FLUID.getBooleanValue() || fluidState.isSource());
    }
}
