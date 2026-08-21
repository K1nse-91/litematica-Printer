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
import me.K1nse_.litematica.printer.utils.minecraft.MessageUtils;
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
