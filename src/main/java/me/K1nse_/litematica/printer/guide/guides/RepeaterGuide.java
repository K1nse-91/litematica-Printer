package me.K1nse_.litematica.printer.guide.guides;

import me.K1nse_.litematica.printer.enums.BlockMatchResult;
import me.K1nse_.litematica.printer.guide.Guide;
import me.K1nse_.litematica.printer.guide.Result;
import me.K1nse_.litematica.printer.printer.SchematicBlockContext;
import me.K1nse_.litematica.printer.printer.action.ClickAction;
import net.minecraft.world.level.block.RepeaterBlock;

/**
 * 红石中继器
 */
public class RepeaterGuide extends Guide {

    public RepeaterGuide(SchematicBlockContext context) {
        super(context);
    }

    @Override
    protected Result onBuildActionWrongState(BlockMatchResult state) {
        if (!getProperty(requiredState, RepeaterBlock.DELAY).equals(getProperty(currentState, RepeaterBlock.DELAY))) {
            return Result.success(new ClickAction());
        }
        return Result.SKIP;
    }
}
