package me.K1nse_.litematica.printer.guide.guides;

import me.K1nse_.litematica.printer.enums.BlockMatchResult;
import me.K1nse_.litematica.printer.guide.Guide;
import me.K1nse_.litematica.printer.guide.Result;
import me.K1nse_.litematica.printer.printer.SchematicBlockContext;
import me.K1nse_.litematica.printer.printer.action.Action;
import me.K1nse_.litematica.printer.printer.action.ClickAction;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * 栅栏门
 */
public class FenceGateGuide extends Guide {

    public FenceGateGuide(SchematicBlockContext context) {
        super(context);
    }

    @Override
    protected Result onBuildActionMissingBlock(BlockMatchResult state) {
        Direction facing = getProperty(requiredState, net.minecraft.world.level.block.FenceGateBlock.FACING).orElse(null);
        return Result.success(new Action().setLookDirection(facing));
    }

    @Override
    protected Result onBuildActionWrongState(BlockMatchResult state) {
        Direction facing = getProperty(requiredState, FenceGateBlock.FACING).orElseThrow();

        Direction currentFacing = getProperty(currentState, BlockStateProperties.HORIZONTAL_FACING).orElse(null);
        boolean openMismatch = getProperty(requiredState, BlockStateProperties.OPEN)
                .map(open -> !open.equals(getProperty(currentState, BlockStateProperties.OPEN).orElse(null)))
                .orElse(false);

        if (facing.getOpposite() == currentFacing || openMismatch) {
            return Result.success(new ClickAction()
                    .setSides(facing.getOpposite())
                    .setLookDirection(facing));
        }
        return Result.SKIP;
    }
}
