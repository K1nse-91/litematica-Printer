package me.K1nse_.litematica.printer.guide.guides;

import me.K1nse_.litematica.printer.enums.BlockMatchResult;
import me.K1nse_.litematica.printer.guide.Guide;
import me.K1nse_.litematica.printer.guide.Result;
import me.K1nse_.litematica.printer.printer.SchematicBlockContext;
import me.K1nse_.litematica.printer.printer.action.Action;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.LanternBlock;

/**
 * 灯笼
 */
public class LanternGuide extends Guide {

    public LanternGuide(SchematicBlockContext context) {
        super(context);
    }

    @Override
    protected Result onBuildActionMissingBlock(BlockMatchResult state) {
        if (getProperty(requiredState, LanternBlock.HANGING).orElse(false)) {
            return Result.success(new Action().setLookDirection(Direction.UP));
        }
        return Result.success(new Action().setLookDirection(Direction.DOWN));
    }
}
