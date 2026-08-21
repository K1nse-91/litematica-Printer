package me.K1nse_.litematica.printer.guide.guides;

import me.K1nse_.litematica.printer.config.Configs;
import me.K1nse_.litematica.printer.enums.BlockMatchResult;
import me.K1nse_.litematica.printer.guide.Guide;
import me.K1nse_.litematica.printer.guide.Result;
import me.K1nse_.litematica.printer.printer.SchematicBlockContext;
import me.K1nse_.litematica.printer.utils.minecraft.BlockStateUtils;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * 水源/含水方块的无状态兜底规则。
 * 破冰放水这类跨 tick 流程由 PrintTaskController 接管。
 */
public class WaterGuide extends Guide {
    public WaterGuide(SchematicBlockContext context) {
        super(context);
    }

    @Override
    protected boolean canExecute() {
        return BlockStateUtils.isWaterBlock(requiredState);
    }

    @Override
    protected Result onBuildAction(BlockMatchResult state) {
        if (shouldSkipWaterloggedTarget()) {
            return Result.SKIP;
        }
        if (isWaterloggedTarget()) {
            return Result.PASS;
        }
        return Result.SKIP;
    }

    @Override
    protected Result onBuildActionCorrect(BlockMatchResult state) {
        return isWaterloggedTarget() ? Result.PASS : Result.SKIP;
    }

    private boolean shouldSkipWaterloggedTarget() {
        return Configs.Print.SKIP_WATERLOGGED_BLOCK.getBooleanValue() && isWaterloggedTarget();
    }

    private boolean isWaterloggedTarget() {
        return requiredState.hasProperty(BlockStateProperties.WATERLOGGED)
                && requiredState.getValue(BlockStateProperties.WATERLOGGED);
    }
}
