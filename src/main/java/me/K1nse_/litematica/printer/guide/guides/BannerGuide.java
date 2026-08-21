package me.K1nse_.litematica.printer.guide.guides;

import me.K1nse_.litematica.printer.enums.BlockMatchResult;
import me.K1nse_.litematica.printer.guide.Guide;
import me.K1nse_.litematica.printer.guide.Result;
import me.K1nse_.litematica.printer.printer.SchematicBlockContext;
import me.K1nse_.litematica.printer.printer.action.Action;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.BannerBlock;
import net.minecraft.world.level.block.WallBannerBlock;
import net.minecraft.world.level.block.WallTorchBlock;

/**
 * 旗帜。
 */
public class BannerGuide extends Guide {

    public BannerGuide(SchematicBlockContext context) {
        super(context);
    }

    @Override
    protected Result onBuildActionMissingBlock(BlockMatchResult state) {
        Direction facing = getProperty(requiredState, WallTorchBlock.FACING).orElse(null);

        if (requiredBlock instanceof BannerBlock) {
            int rotation = getProperty(requiredState, BannerBlock.ROTATION).orElseThrow();
            return Result.success(new Action()
                    .setSides(Direction.DOWN)
                    .setLookRotation(rotation)
                    .setRequiresSupport());
        }
        if (requiredBlock instanceof WallBannerBlock && facing != null) {
            return Result.success(new Action()
                    .setSides(facing.getOpposite())
                    .setLookDirection(facing.getOpposite())
                    .setRequiresSupport());
        }
        return Result.SKIP;
    }
}
