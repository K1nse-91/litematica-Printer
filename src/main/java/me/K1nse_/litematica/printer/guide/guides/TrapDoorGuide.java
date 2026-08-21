package me.K1nse_.litematica.printer.guide.guides;

import me.K1nse_.litematica.printer.enums.BlockMatchResult;
import me.K1nse_.litematica.printer.guide.Guide;
import me.K1nse_.litematica.printer.guide.Result;
import me.K1nse_.litematica.printer.printer.SchematicBlockContext;
import me.K1nse_.litematica.printer.printer.action.Action;
import me.K1nse_.litematica.printer.printer.action.ClickAction;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;

/**
 * 活板门
 */
public class TrapDoorGuide extends Guide {

    public TrapDoorGuide(SchematicBlockContext context) {
        super(context);
    }

    @Override
    protected Result onBuildActionMissingBlock(BlockMatchResult state) {
        Direction facing = getProperty(requiredState, TrapDoorBlock.FACING).orElse(null);
        Half half = getProperty(requiredState, TrapDoorBlock.HALF).orElse(Half.BOTTOM);
        if (facing == null) return Result.SKIP;

        Direction side = half == Half.TOP ? Direction.UP : Direction.DOWN;
        return Result.success(new Action()
                .setSides(side)
                .setLookDirection(facing.getOpposite()));
    }

    @Override
    protected Result onBuildActionWrongState(BlockMatchResult state) {
        // 铁活板门无法手动交互
        if (requiredState.is(Blocks.IRON_TRAPDOOR)) {
            return Result.SKIP;
        }
        // 开关状态不一致 → 右键点击切换
        if (!getProperty(requiredState, TrapDoorBlock.OPEN)
                .equals(getProperty(currentState, BlockStateProperties.OPEN))) {
            return Result.success(new ClickAction());
        }
        return Result.SKIP;
    }
}
