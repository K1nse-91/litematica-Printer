package me.K1nse_.litematica.printer.guide.guides;

import me.K1nse_.litematica.printer.enums.BlockMatchResult;
import me.K1nse_.litematica.printer.guide.Guide;
import me.K1nse_.litematica.printer.guide.Result;
import me.K1nse_.litematica.printer.printer.SchematicBlockContext;
import me.K1nse_.litematica.printer.printer.action.ClickAction;
import net.minecraft.world.level.block.LeverBlock;

/**
 * 拉杆
 */
public class LeverGuide extends Guide {

    public LeverGuide(SchematicBlockContext context) {
        super(context);
    }

    @Override
    protected Result onBuildActionWrongState(BlockMatchResult state) {
        if (!getProperty(requiredState, LeverBlock.POWERED).equals(getProperty(currentState, LeverBlock.POWERED))) {
            return Result.success(new ClickAction());
        }
        return Result.SKIP;
    }
}
