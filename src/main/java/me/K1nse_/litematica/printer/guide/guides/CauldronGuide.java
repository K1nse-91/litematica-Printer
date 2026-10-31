package me.K1nse_.litematica.printer.guide.guides;

import me.K1nse_.litematica.printer.config.Configs;
import me.K1nse_.litematica.printer.enums.BlockMatchResult;
import me.K1nse_.litematica.printer.guide.Guide;
import me.K1nse_.litematica.printer.guide.Result;
import me.K1nse_.litematica.printer.printer.SchematicBlockContext;
import me.K1nse_.litematica.printer.printer.action.ClickAction;
import me.K1nse_.litematica.printer.utils.InteractionUtils;
import me.K1nse_.litematica.printer.utils.InventoryUtils;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.item.Items;

import java.util.Optional;

/**
 * 炼药锅
 */
public class CauldronGuide extends Guide {

    public CauldronGuide(SchematicBlockContext context) {
        super(context);
    }

    @Override
    protected Result onBuildActionWrongState(BlockMatchResult state) {
        Optional<Integer> currentLevel = getProperty(currentState, LayeredCauldronBlock.LEVEL);
        Optional<Integer> requiredLevel = getProperty(requiredState, LayeredCauldronBlock.LEVEL);

        if (currentLevel.isEmpty() || requiredLevel.isEmpty()) {
            return Result.SKIP;
        }

        if (currentLevel.get() > requiredLevel.get()) {
            if (InventoryUtils.playerHasAccessToItem(client.player, Items.GLASS_BOTTLE)) {
                return Result.success(new ClickAction().setItem(Items.GLASS_BOTTLE));
            }
        }
        if (currentLevel.get() < requiredLevel.get()) {
            if (InventoryUtils.playerHasAccessToItem(client.player, Items.POTION)) {
                return Result.success(new ClickAction().setItem(Items.POTION));
            }
        }
        return Result.SKIP;
    }

    @Override
    protected Result onBuildActionWrongBlock(BlockMatchResult state) {
        // 炼药锅填充（移植自 EMT）：空锅 → 目标液体锅
        if (Configs.Print.FILL_CAULDRON.getBooleanValue()) {
            Result fill = this.buildFillAction();
            if (fill != null) {
                return fill;
            }
        }
        if (Configs.Print.BREAK_WRONG_BLOCK.getBooleanValue()
                && InteractionUtils.canBreakBlock(blockPos)) {
            InteractionUtils.INSTANCE.add(context);
        }
        return Result.SKIP;
    }

    /** Fill an empty cauldron into the required liquid cauldron (ported from EMT) */
    private Result buildFillAction() {
        if (!currentState.is(net.minecraft.world.level.block.Blocks.CAULDRON)) {
            return null;
        }
        if (requiredState.is(net.minecraft.world.level.block.Blocks.WATER_CAULDRON)
                && InventoryUtils.playerHasAccessToItem(client.player, Items.WATER_BUCKET)) {
            return Result.success(new ClickAction().setItem(Items.WATER_BUCKET));
        }
        if (requiredState.is(net.minecraft.world.level.block.Blocks.LAVA_CAULDRON)
                && InventoryUtils.playerHasAccessToItem(client.player, Items.LAVA_BUCKET)) {
            return Result.success(new ClickAction().setItem(Items.LAVA_BUCKET));
        }
        if (requiredState.is(net.minecraft.world.level.block.Blocks.POWDER_SNOW_CAULDRON)
                && InventoryUtils.playerHasAccessToItem(client.player, Items.POWDER_SNOW_BUCKET)) {
            return Result.success(new ClickAction().setItem(Items.POWDER_SNOW_BUCKET));
        }
        return null;
    }
}