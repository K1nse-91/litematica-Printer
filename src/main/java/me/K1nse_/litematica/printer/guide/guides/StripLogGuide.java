package me.K1nse_.litematica.printer.guide.guides;

import me.K1nse_.litematica.printer.config.Configs;
import me.K1nse_.litematica.printer.enums.BlockMatchResult;
import me.K1nse_.litematica.printer.guide.Guide;
import me.K1nse_.litematica.printer.guide.Result;
import me.K1nse_.litematica.printer.printer.SchematicBlockContext;
import me.K1nse_.litematica.printer.printer.action.Action;
import me.K1nse_.litematica.printer.printer.action.ClickAction;
import me.K1nse_.litematica.printer.Reference;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import java.util.HashMap;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.Map;

/**
 * 去皮原木
 */
public class StripLogGuide extends Guide {

    /** 由注册表推导去皮映射（原木/木头/菌柄/菌核 → stripped_ 前缀），避免依赖 Fabric 内部 accessor */
    private static final Map<Block, Block> STRIPPED_LOGS = buildStrippables();

    private static Map<Block, Block> buildStrippables() {
        Map<Block, Block> map = new HashMap<>();
        for (Block block : BuiltInRegistries.BLOCK) {
            Identifier id = BuiltInRegistries.BLOCK.getKey(block);
            String path = id.getPath();
            if (path.startsWith("stripped_")) {
                continue;
            }
            if (!(path.endsWith("_log") || path.endsWith("_wood") || path.endsWith("_stem") || path.endsWith("_hyphae"))) {
                continue;
            }
            Identifier strippedId = Identifier.fromNamespaceAndPath(id.getNamespace(), "stripped_" + path);
            if (BuiltInRegistries.BLOCK.containsKey(strippedId)) {
                Block stripped = BuiltInRegistries.BLOCK.getOptional(strippedId).orElse(null);
                if (stripped != null) {
                    map.put(block, stripped);
                }
            }
        }
        return map;
    }

    public StripLogGuide(SchematicBlockContext context) {
        super(context);
    }

    @Override
    protected Result onBuildActionMissingBlock(BlockMatchResult state) {
        Direction.Axis axis = getProperty(requiredState, BlockStateProperties.AXIS).orElse(null);
        if (axis == null) return Result.PASS;

        Action action = new Action().setSides(axis);

        // 配置启用去皮时，可接受原版或去皮版本
        if (Configs.Print.STRIP_LOGS.getBooleanValue()) {
            for (Map.Entry<Block, Block> entry : STRIPPED_LOGS.entrySet()) {
                if (requiredBlock == entry.getValue()) {
                    action.setItems(entry.getValue().asItem(), entry.getKey().asItem());
                    return Result.success(action);
                }
            }
        }

        return Result.success(action);
    }

    @Override
    protected Result onBuildActionWrongBlock(BlockMatchResult state) {
        Block stripped = STRIPPED_LOGS.get(currentBlock);
        if (stripped != null && stripped == requiredBlock) {
            return Result.success(new ClickAction().setItems(Reference.AXE_ITEMS));
        }
        return Result.SKIP;
    }
}
