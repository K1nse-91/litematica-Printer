package me.K1nse_.litematica.printer.handler.handlers;

import me.K1nse_.litematica.printer.config.Configs;
import me.K1nse_.litematica.printer.enums.PrintModeType;
import me.K1nse_.litematica.printer.handler.Module;
import me.K1nse_.litematica.printer.handler.handlers.bedrock.BedrockCandidatePlanner;
import me.K1nse_.litematica.printer.handler.handlers.bedrock.BedrockController;
import me.K1nse_.litematica.printer.handler.handlers.bedrock.BedrockEnvironment;
import me.K1nse_.litematica.printer.handler.handlers.bedrock.BedrockInventory;
import me.K1nse_.litematica.printer.handler.handlers.bedrock.BedrockTargetBlocks;
import me.K1nse_.litematica.printer.I18n;
import me.K1nse_.litematica.printer.printer.PrinterBox;
import me.K1nse_.litematica.printer.utils.bedrock.BedrockUtils;
import me.K1nse_.litematica.printer.utils.minecraft.MessageUtils;
import me.K1nse_.litematica.printer.utils.mods.ModLoadUtils;
import net.minecraft.core.BlockPos;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class BedrockHandler extends Module {
    private final BedrockCandidatePlanner candidatePlanner = new BedrockCandidatePlanner();

    public BedrockHandler() {
        super("bedrock", PrintModeType.BEDROCK, Configs.Hotkeys.BEDROCK, null, true);
    }

    @Override
    protected int getTickInterval() {
        return 0;
    }

    @Override
    protected int getMaxEffectiveExecutionsPerTick() {
        return Math.max(1, Configs.Bedrock.BEDROCK_BLOCKS_PER_TICK.getIntegerValue());
    }

    @Override
    protected boolean canExecute() {
        // 外部破基岩模式（移植自 EMT）：交给 bedrockminer/blockminer 模组执行
        if (Configs.Bedrock.BEDROCK_EXTERNAL_MINER.getBooleanValue()) {
            if (!ModLoadUtils.isBedrockMinerLoaded() && !ModLoadUtils.isBlockMinerLoaded()) {
                BedrockController.clearHorizontalLookState();
                MessageUtils.setOverlayMessage(I18n.BEDROCK_EXTERNAL_MOD_NOT_LOADED.getName());
                return false;
            }
            if (player.isCreative()) {
                BedrockController.clearHorizontalLookState();
                MessageUtils.setOverlayMessage(I18n.BEDROCK_CREATIVE_MODE.getName());
                return false;
            }
            if (!BedrockUtils.isWorking()) {
                BedrockUtils.setWorking(true);
            }
            if (BedrockUtils.isBedrockMinerFeatureEnable()) {
                BedrockUtils.setBedrockMinerFeatureEnable(false);
            }
            return true;
        }
        if (player.isCreative()) {
            BedrockController.clearHorizontalLookState();
            MessageUtils.setOverlayMessage(I18n.BEDROCK_CREATIVE_MODE.getName());
            return false;
        }
        String warning = BedrockInventory.warningMessage();
        if (warning != null) {
            BedrockController.clearHorizontalLookState();
            MessageUtils.setOverlayMessage(me.K1nse_.litematica.printer.utils.minecraft.StringUtils.translatable(warning));
            return false;
        }
        return true;
    }

    @Override
    protected boolean canIterate() {
        BedrockController.tick();
        return BedrockController.canScanForTargets();
    }

    @Override
    protected void onRuntimeReset() {
        BedrockController.reset();
        if (Configs.Bedrock.BEDROCK_EXTERNAL_MINER.getBooleanValue()) {
            BedrockUtils.setWorking(false);
        }
    }

    @Override
    protected Iterable<BlockPos> getIterationPositions(PrinterBox playerInteractionBox) {
        BedrockController.clearSubmissionPlans();
        if (playerInteractionBox == null || this.level == null || this.player == null) {
            return List.of();
        }

        return this.candidatePlanner.iterable(
                playerInteractionBox,
                this.level,
                this.player,
                this.getMaxEffectiveExecutionsPerTick(),
                this.getScanGuardLimit()
        );
    }

    @Override
    public boolean canIterationBlockPos(BlockPos pos) {
        if (level == null || !BedrockTargetBlocks.isTargetBlock(level.getBlockState(pos))) {
            return false;
        }
        return BedrockController.canAccept(pos);
    }

    @Override
    protected boolean canReachIterationPosition(BlockPos pos) {
        return BedrockEnvironment.canInteract(pos);
    }

    @Override
    protected boolean requiresSelection1ModeRangeCheck() {
        return false;
    }

    @Override
    protected void executeIteration(BlockPos blockPos, AtomicReference<Boolean> skipIteration) {
        // 外部破基岩模式（移植自 EMT）：目标直接交给 bedrockminer/blockminer 模组
        if (Configs.Bedrock.BEDROCK_EXTERNAL_MINER.getBooleanValue()) {
            if (level == null || !BedrockTargetBlocks.isTargetBlock(level.getBlockState(blockPos))) {
                setIterationConsumedEffectiveExecution(false);
                return;
            }
            BedrockUtils.addToBreakList(blockPos, level);
            setIterationConsumedEffectiveExecution(true);
            skipIteration.set(true);
            return;
        }
        if (level == null || !BedrockTargetBlocks.isTargetBlock(level.getBlockState(blockPos))) {
            setIterationConsumedEffectiveExecution(false);
            return;
        }
        boolean submitted = BedrockController.submit(blockPos);
        setIterationConsumedEffectiveExecution(submitted);
        if (submitted) {
            // Allow a second same-tick submit when the controller still has safe capacity.
            skipIteration.set(!BedrockController.canScanForTargets());
        }
    }

    @Override
    protected void stopIteration(boolean interrupt) {
        if (!interrupt) {
            BedrockController.tick();
        }
    }

}
