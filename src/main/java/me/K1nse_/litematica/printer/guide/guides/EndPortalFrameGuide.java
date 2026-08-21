package me.K1nse_.litematica.printer.guide.guides;

import me.K1nse_.litematica.printer.enums.BlockMatchResult;
import me.K1nse_.litematica.printer.guide.Guide;
import me.K1nse_.litematica.printer.guide.Result;
import me.K1nse_.litematica.printer.printer.SchematicBlockContext;
import me.K1nse_.litematica.printer.printer.action.ClickAction;
import net.minecraft.world.level.block.EndPortalFrameBlock;
import net.minecraft.world.item.Items;

/**
 * 末地传送门框架
 */
public class EndPortalFrameGuide extends Guide {

    public EndPortalFrameGuide(SchematicBlockContext context) {
        super(context);
    }

    @Override
    protected Result onBuildActionWrongState(BlockMatchResult state) {
        boolean requiredHasEye = getProperty(requiredState, EndPortalFrameBlock.HAS_EYE).orElseThrow();
        boolean currentHasEye = getProperty(currentState, EndPortalFrameBlock.HAS_EYE).orElseThrow();
        if (requiredHasEye && !currentHasEye) {
            return Result.success(new ClickAction().setItem(Items.ENDER_EYE));
        }
        return Result.SKIP;
    }
}
