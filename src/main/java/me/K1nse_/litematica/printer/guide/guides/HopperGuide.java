package me.K1nse_.litematica.printer.guide.guides;

import me.K1nse_.litematica.printer.enums.BlockMatchResult;
import me.K1nse_.litematica.printer.guide.Guide;
import me.K1nse_.litematica.printer.guide.Result;
import me.K1nse_.litematica.printer.printer.SchematicBlockContext;
import me.K1nse_.litematica.printer.printer.action.Action;
import net.minecraft.world.level.block.HopperBlock;

/**
 * 漏斗
 */
public class HopperGuide extends Guide {

    public HopperGuide(SchematicBlockContext context) {
        super(context);
    }

    @Override
    protected Result onBuildActionMissingBlock(BlockMatchResult state) {
        var hopperFacing = getProperty(requiredState, HopperBlock.FACING).orElseThrow();
        return Result.success(new Action().setSides(hopperFacing));
    }
}
