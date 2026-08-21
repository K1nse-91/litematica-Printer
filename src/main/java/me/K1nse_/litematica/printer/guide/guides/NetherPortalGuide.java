package me.K1nse_.litematica.printer.guide.guides;

import me.K1nse_.litematica.printer.enums.BlockMatchResult;
import me.K1nse_.litematica.printer.guide.Guide;
import me.K1nse_.litematica.printer.guide.Result;
import me.K1nse_.litematica.printer.printer.SchematicBlockContext;
import me.K1nse_.litematica.printer.printer.action.Action;
import net.minecraft.world.level.portal.PortalShape;
import net.minecraft.world.item.Items;

/**
 * 下界传送门
 */
public class NetherPortalGuide extends Guide {

    public NetherPortalGuide(SchematicBlockContext context) {
        super(context);
    }

    @Override
    protected Result onBuildActionMissingBlock(BlockMatchResult state) {
        boolean canCreatePortal = PortalShape.findEmptyPortalShape(level, blockPos, net.minecraft.core.Direction.Axis.X).isPresent();
        if (canCreatePortal) {
            return Result.success(new Action()
                    .setItems(Items.FLINT_AND_STEEL, Items.FIRE_CHARGE)
                    .setRequiresSupport());
        }
        return Result.SKIP;
    }

    @Override
    protected Result onBuildActionWrongState(BlockMatchResult state) {
        return Result.SKIP;
    }
}
