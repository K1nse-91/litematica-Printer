package me.K1nse_.litematica.printer.guide.guides;

import me.K1nse_.litematica.printer.enums.BlockMatchResult;
import me.K1nse_.litematica.printer.guide.Guide;
import me.K1nse_.litematica.printer.guide.Result;
import me.K1nse_.litematica.printer.printer.SchematicBlockContext;
import me.K1nse_.litematica.printer.printer.action.ClickAction;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 雪层
 */
public class SnowGuide extends Guide {

    public SnowGuide(SchematicBlockContext context) {
        super(context);
    }

    @Override
    protected Result onBuildActionWrongState(BlockMatchResult state) {
        int requiredLayers = getProperty(requiredState, SnowLayerBlock.LAYERS).orElseThrow();
        Optional<Integer> layers = getProperty(currentState, SnowLayerBlock.LAYERS);

        if (layers.isPresent()) {
            if (layers.get() < requiredLayers) {
                Map<Direction, Vec3> sides = new HashMap<>();
                sides.put(Direction.UP, new Vec3(0, (layers.get() / 8d) - 1, 0));
                return Result.success(new ClickAction().setItem(Items.SNOW).setSides(sides));
            }
        }

        return Result.SKIP;
    }
}
