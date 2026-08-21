package me.K1nse_.litematica.printer.guide.guides;

import me.K1nse_.litematica.printer.enums.BlockMatchResult;
import me.K1nse_.litematica.printer.guide.Guide;
import me.K1nse_.litematica.printer.guide.Result;
import me.K1nse_.litematica.printer.printer.SchematicBlockContext;
import me.K1nse_.litematica.printer.printer.action.Action;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.AnvilBlock;

/**
 * 铁砧。
 */
public class AnvilGuide extends Guide {

    public AnvilGuide(SchematicBlockContext context) {
        super(context);
    }

    @Override
    protected Result onBuildActionMissingBlock(BlockMatchResult state) {
        Direction anvilFacing = getProperty(requiredState, AnvilBlock.FACING).orElseThrow();
        return Result.success(new Action().setLookDirection(anvilFacing.getCounterClockWise()));
    }
}
