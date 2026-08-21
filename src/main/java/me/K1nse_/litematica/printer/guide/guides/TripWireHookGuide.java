package me.K1nse_.litematica.printer.guide.guides;

import me.K1nse_.litematica.printer.enums.BlockMatchResult;
import me.K1nse_.litematica.printer.guide.Guide;
import me.K1nse_.litematica.printer.guide.Result;
import me.K1nse_.litematica.printer.printer.SchematicBlockContext;
import me.K1nse_.litematica.printer.printer.action.Action;
import net.minecraft.world.level.block.TripWireHookBlock;

/**
 * 绊线钩
 */
public class TripWireHookGuide extends Guide {

    public TripWireHookGuide(SchematicBlockContext context) {
        super(context);
    }

    @Override
    protected Result onBuildActionMissingBlock(BlockMatchResult state) {
        var facing = getProperty(requiredState, TripWireHookBlock.FACING).orElseThrow();
        return Result.success(new Action()
                .setSides(facing.getOpposite())
                .setRequiresSupport()
        );
    }

    @Override
    protected Result onBuildActionWrongState(BlockMatchResult state) {
        return Result.SKIP;
    }
}
