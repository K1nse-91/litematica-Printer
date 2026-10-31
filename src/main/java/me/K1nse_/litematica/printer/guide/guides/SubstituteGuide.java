package me.K1nse_.litematica.printer.guide.guides;

import me.K1nse_.litematica.printer.config.Configs;
import me.K1nse_.litematica.printer.enums.BlockMatchResult;
import me.K1nse_.litematica.printer.guide.Guide;
import me.K1nse_.litematica.printer.guide.Result;
import me.K1nse_.litematica.printer.printer.SchematicBlockContext;
import me.K1nse_.litematica.printer.printer.SubstitutePlacementCache;
import me.K1nse_.litematica.printer.printer.action.Action;
import me.K1nse_.litematica.printer.utils.minecraft.BlockUtils;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 材料替代放置（移植自 EMT）：
 * 目标方块在背包中缺失时，用配置的替代方块放置。
 * 仅处理没有专属 Guide 的普通方块；专属 Guide（楼梯/门等）优先级更高。
 */
public class SubstituteGuide extends Guide {

    public SubstituteGuide(SchematicBlockContext context) {
        super(context);
    }

    @Override
    protected boolean canExecute() {
        return SubstitutePlacementCache.active() && SubstitutePlacementCache.hasSubstitutes(this.requiredBlock);
    }

    @Override
    protected Result onBuildActionWrongBlock(BlockMatchResult state) {
        return Configs.Print.SUBSTITUTE_PLACEMENT.getBooleanValue() && SubstitutePlacementCache.isSubstituteOf(this.requiredBlock, this.currentBlock)
                ? Result.SKIP
                : Result.PASS;
    }

    @Override
    protected Result onBuildActionMissingBlock(BlockMatchResult state) {
        List<Item> items = new ArrayList<>();
        items.add(this.requiredBlock.asItem());

        for (net.minecraft.world.level.block.Block substitute : SubstitutePlacementCache.getSubstitutes(this.requiredBlock)) {
            Item item = substitute.asItem();
            if (item != Items.AIR && !items.contains(item)) {
                items.add(item);
            }
        }

        Action action = this.buildSubstituteAction();
        action.setItems(items.toArray(new Item[0]));

        // 珊瑚类方块：贴着墙放，且需要支撑（替代方块的放置规则与原方块一致）
        Identifier targetId = BlockUtils.getKey(this.requiredBlock);
        if (targetId.getPath().contains("coral") && !targetId.getPath().endsWith("_block")) {
            getProperty(this.requiredState, BlockStateProperties.HORIZONTAL_FACING)
                    .ifPresent(facing -> action.setSides(facing.getOpposite()));
            action.setRequiresSupport();
        }

        return Result.success(action);
    }

    /** 精简版默认放置上下文：带朝向的方块转向放置（完整版 buildTargetStateContext 在 DefaultGuide 中） */
    private Action buildSubstituteAction() {
        Action action = new Action();
        Direction facing = getProperty(this.requiredState, BlockStateProperties.HORIZONTAL_FACING)
                .or(() -> getProperty(this.requiredState, BlockStateProperties.FACING))
                .orElse(null);
        if (facing != null) {
            action.setLookDirection(facing.getOpposite());
        }
        return action;
    }
}
