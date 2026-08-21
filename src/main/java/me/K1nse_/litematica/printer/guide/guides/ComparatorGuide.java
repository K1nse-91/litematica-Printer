package me.K1nse_.litematica.printer.guide.guides;

import me.K1nse_.litematica.printer.enums.BlockMatchResult;
import me.K1nse_.litematica.printer.guide.Guide;
import me.K1nse_.litematica.printer.guide.Result;
import me.K1nse_.litematica.printer.printer.SchematicBlockContext;
import me.K1nse_.litematica.printer.printer.action.ClickAction;
import net.minecraft.world.level.block.ComparatorBlock;

/**
 * 红石比较器
 */
public class ComparatorGuide extends Guide {

    public ComparatorGuide(SchematicBlockContext context) {
        super(context);
    }

    @Override
    protected Result onBuildActionWrongState(BlockMatchResult state) {
        if (!getProperty(requiredState, ComparatorBlock.MODE).equals(getProperty(currentState, ComparatorBlock.MODE))) {
            return Result.success(new ClickAction());
        }
        return Result.SKIP;
    }
}
