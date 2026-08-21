package me.K1nse_.litematica.printer.handler.handlers.print;

import me.K1nse_.litematica.printer.I18n;
import me.K1nse_.litematica.printer.config.Configs;
import me.K1nse_.litematica.printer.handler.HudStatsManager;
import me.K1nse_.litematica.printer.interfaces.Implementation;
import me.K1nse_.litematica.printer.printer.ActionManager;
import me.K1nse_.litematica.printer.printer.PlayerLook;
import me.K1nse_.litematica.printer.printer.SchematicBlockContext;
import me.K1nse_.litematica.printer.printer.action.Action;
import me.K1nse_.litematica.printer.printer.action.ClickAction;
import me.K1nse_.litematica.printer.utils.ConfigUtils;
import me.K1nse_.litematica.printer.utils.InventoryUtils;
import me.K1nse_.litematica.printer.utils.minecraft.DirectionUtils;
import me.K1nse_.litematica.printer.utils.minecraft.MessageUtils;
import me.K1nse_.litematica.printer.utils.mods.LitematicaUtils;
import me.K1nse_.litematica.printer.utils.mods.TakeItOutUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public final class PrintPlacementExecutor {
    private static final Item[] EMPTY_HAND_ITEMS = {Items.AIR};

    public PrintPlacementResult execute(SchematicBlockContext context, Action action, @Nullable PrintTaskAction taskAction) {
        BlockPos blockPos = context.blockPos;
        if (Configs.Placement.FALLING_CHECK.getBooleanValue() && context.requiredState.getBlock() instanceof FallingBlock) {
            BlockPos downPos = blockPos.below();
            if (FallingBlock.isFree(context.level.getBlockState(downPos))) {
                HudStatsManager.INSTANCE.recordDeferred(HudStatsManager.Mode.PRINT, "下落方块无支撑");
                MessageUtils.setOverlayMessage(I18n.FALLING_BLOCK_NO_SUPPORT.getName(context.requiredBlockName().getString()));
                return PrintPlacementResult.failure(false, shouldStopAfterTaskAction(taskAction));
            }
        }

        Direction side = action.getValidSide(context.level, blockPos);
        if (side == null) {
            HudStatsManager.INSTANCE.recordDeferred(HudStatsManager.Mode.PRINT, "无有效放置面");
            return PrintPlacementResult.failure(false, shouldStopAfterTaskAction(taskAction));
        }

        Item[] requiredItems = normalizeRequiredItems(action.getRequiredItems(context.requiredState.getBlock()));
        if (!InventoryUtils.switchToItems(context.client.player, requiredItems)) {
            HudStatsManager.INSTANCE.recordDeferred(HudStatsManager.Mode.PRINT, "缺少材料");
            // 缺少材料属于无效放置，不应消耗每 tick 的有效放置预算（与重构前行为一致）。
            return PrintPlacementResult.failure(false,
                    shouldStopAfterTaskAction(taskAction)
                            || me.K1nse_.litematica.printer.printer.zxy.inventory.InventoryUtils.shouldPauseForSwitchRequest()
                            || TakeItOutUtils.isAwaitingStack());
        }
        if (!InventoryUtils.isHoldingAnyItem(context.client.player, requiredItems)) {
            HudStatsManager.INSTANCE.recordDeferred(HudStatsManager.Mode.PRINT, "等待物品同步");
            return PrintPlacementResult.failure(false, true);
        }

        boolean useShift = getUseShift(context, action, side);
        action.queueAction(blockPos, side, useShift, context.client.player, requiredItems);
        Vec3 hitModifier = LitematicaUtils.usePrecisionPlacement(blockPos, context.requiredState);
        if (hitModifier != null) {
            ActionManager.INSTANCE.useProtocolHitModifier(hitModifier);
        }
        ActionManager.INSTANCE.setLook(adjustHorizontalLook(action.getPlayerLook(), context));
        ActionManager.INSTANCE.setNeedWaitModifyLookFromAction(action.isNeedWaitModifyLook());
        HudStatsManager.INSTANCE.trackExpectedBlockState(HudStatsManager.Mode.PRINT, blockPos, context.requiredState);
        HudStatsManager.INSTANCE.recordRateUnit(HudStatsManager.Mode.PRINT, 1);

        boolean consumedEffectiveExecution = action.isConsumeEffectiveExecution();
        boolean needWaitModifyLook = ActionManager.INSTANCE.sendQueue(context.client.player).needWaitModifyLook;
        PrintPlacementResult.TaskEvent taskEvent = needWaitModifyLook
                ? PrintPlacementResult.TaskEvent.QUEUED
                : PrintPlacementResult.TaskEvent.SUCCESS;

        if (needWaitModifyLook) {
            HudStatsManager.INSTANCE.recordDeferred(HudStatsManager.Mode.PRINT, "等待转头");
        } else {
            HudStatsManager.INSTANCE.recordStatus(HudStatsManager.Mode.PRINT, "运行中");
        }

        boolean skipIteration = needWaitModifyLook
                || shouldStopAfterTaskAction(taskAction);
        int cooldownTicks = action.getCooldownTicksOverride() >= 0
                ? action.getCooldownTicksOverride()
                : ConfigUtils.getPlaceCooldown();
        return new PrintPlacementResult(consumedEffectiveExecution, skipIteration, taskEvent, cooldownTicks);
    }

    private static boolean getUseShift(SchematicBlockContext context, Action action, Direction side) {
        if (action.getShift() != null) {
            return action.getShift();
        }
        return (Implementation.isInteractive(context.level.getBlockState(context.blockPos.relative(side)).getBlock())
                && !(action instanceof ClickAction))
                || Configs.Print.PRINT_FORCED_SNEAK.getBooleanValue();
    }

    @Nullable
    private static PlayerLook adjustHorizontalLook(@Nullable PlayerLook playerLook, SchematicBlockContext context) {
        if (playerLook == null) {
            return null;
        }
        Direction primaryLookDirection = DirectionUtils.orderedByNearest(playerLook.getYaw(), playerLook.getPitch())[0];
        if (primaryLookDirection.getAxis().isHorizontal()) {
            float currentPitch = context.client.player.getXRot();
            currentPitch = Math.max(-40.0F, Math.min(40.0F, currentPitch));
            ActionManager.INSTANCE.setWaitForHorizontalLook(false);
            return new PlayerLook(playerLook.getYaw(), currentPitch);
        }
        return playerLook;
    }

    private static Item[] normalizeRequiredItems(@Nullable Item[] requiredItems) {
        return requiredItems == null || requiredItems.length == 0 ? EMPTY_HAND_ITEMS : requiredItems;
    }

    private static boolean shouldStopAfterTaskAction(@Nullable PrintTaskAction taskAction) {
        return taskAction != null && taskAction.stopIterationAfterAction();
    }
}
