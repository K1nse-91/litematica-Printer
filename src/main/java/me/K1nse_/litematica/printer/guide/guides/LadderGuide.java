package me.K1nse_.litematica.printer.guide.guides;

import me.K1nse_.litematica.printer.enums.BlockMatchResult;
import me.K1nse_.litematica.printer.guide.Guide;
import me.K1nse_.litematica.printer.guide.Result;
import me.K1nse_.litematica.printer.printer.SchematicBlockContext;
import me.K1nse_.litematica.printer.printer.action.Action;
import net.minecraft.world.level.block.LadderBlock;

/**
 * 梯子
 */
public class LadderGuide extends Guide {

    public LadderGuide(SchematicBlockContext context) {
        super(context);
    }

    @Override
    protected Result onBuildActionMissingBlock(BlockMatchResult state) {
        var ladderFacing = getProperty(requiredState, LadderBlock.FACING).orElseThrow();
        return Result.success(new Action()
                .setSides(ladderFacing)
                .setLookDirection(ladderFacing.getOpposite()));
    }
}
