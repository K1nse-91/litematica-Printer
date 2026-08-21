package me.K1nse_.litematica.printer.guide.guides;

import me.K1nse_.litematica.printer.enums.BlockMatchResult;
import me.K1nse_.litematica.printer.guide.Guide;
import me.K1nse_.litematica.printer.guide.Result;
import me.K1nse_.litematica.printer.printer.SchematicBlockContext;
import me.K1nse_.litematica.printer.printer.action.ClickAction;
import me.K1nse_.litematica.printer.Reference;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.item.Items;

/**
 * 篝火
 */
public class CampfireGuide extends Guide {

    public CampfireGuide(SchematicBlockContext context) {
        super(context);
    }

    @Override
    protected Result onBuildActionWrongState(BlockMatchResult state) {
        boolean requiredLit = getProperty(requiredState, CampfireBlock.LIT).orElseThrow();
        boolean currentLit = getProperty(currentState, CampfireBlock.LIT).orElseThrow();

        if (!requiredLit && currentLit) {
            return Result.success(new ClickAction().setItems(Reference.SHOVEL_ITEMS).setSides(Direction.UP));
        }
        if (requiredLit && !currentLit) {
            return Result.success(new ClickAction().setItems(Items.FLINT_AND_STEEL, Items.FIRE_CHARGE));
        }

        return Result.SKIP;
    }
}
