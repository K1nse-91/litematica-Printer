package me.K1nse_.litematica.printer.guide.guides;

import me.K1nse_.litematica.printer.enums.BlockMatchResult;
import me.K1nse_.litematica.printer.guide.Guide;
import me.K1nse_.litematica.printer.guide.Result;
import me.K1nse_.litematica.printer.printer.SchematicBlockContext;
import me.K1nse_.litematica.printer.printer.action.Action;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.AmethystClusterBlock;

/**
 * 紫水晶芽
 */
public class AmethystGuide extends Guide {

    public AmethystGuide(SchematicBlockContext context) {
        super(context);
    }

    @Override
    protected Result onBuildActionMissingBlock(BlockMatchResult state) {
        Direction attachDirection = getProperty(requiredState, AmethystClusterBlock.FACING)
                .orElseThrow()
                .getOpposite();

        return Result.success(new Action()
                .setSides(attachDirection)
                .setRequiresSupport());
    }
}
