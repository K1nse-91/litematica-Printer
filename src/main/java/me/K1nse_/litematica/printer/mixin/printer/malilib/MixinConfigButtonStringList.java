package me.K1nse_.litematica.printer.mixin.printer.malilib;

import fi.dy.masa.malilib.config.IConfigStringList;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.button.ConfigButtonStringList;
import me.K1nse_.litematica.printer.gui.ChooseBlockScreen;
import me.K1nse_.litematica.printer.utils.minecraft.BlockUtils;
import me.K1nse_.litematica.printer.utils.minecraft.IdentifierUtils;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 拦截字符串列表配置按钮的点击，把方块类名单配置改成可视化方块选择界面。
 * 覆盖：方块破坏限制、挖掘、填充、排流体、破基岩 的黑/白名单及方块名单，
 * 以及打印的 跳过放置 / 覆盖打印 列表。
 * 非方块条目（标签 #、方块状态、中文名、“,c”包含匹配等）不会被界面改动，
 * 打开界面时会原样保留并在保存时写回，避免丢配置。
 */
@Mixin(value = ConfigButtonStringList.class, remap = false)
public abstract class MixinConfigButtonStringList {
    /** 所有应使用可视化方块选择界面的名单配置名 */
    private static final Set<String> BLOCK_LIST_CONFIGS = Set.of(
            "breakWhitelist", "breakBlacklist",             // 方块破坏限制 白/黑名单
            "breakVeilList",                                // 瞬时破坏名单
            "breakFluidList",                               // 不破坏的流体/方块
            "excavateWhitelist", "excavateBlacklist",       // 挖掘 白/黑名单
            "bedrockWhitelist",                             // 破基岩 白名单
            "fillBlockList",                                // 填充 方块名单
            "fluidReplaceBlockList", "fluidList",           // 排流体 方块/液体名单
            "printSkipList",                                // 打印 跳过放置
            "printReplaceableList",                         // 打印 覆盖打印
            "printScanWhitelistList",                       // 打印 扫描白名单
            "printSubstituteList",                          // 打印 材料替代
            "printAutoFillComposterWhitelist",              // 堆肥桶填充白名单
            "walkScanWhitelistList",                        // 自动寻路扫描白名单
            "renderOnlyBlockList"                           // 只渲染的方块名单
    );

    @Shadow
    private IConfigStringList config;

    @Inject(method = "onMouseClickedImpl", at = @At("HEAD"), cancellable = true)
    private void litematica_printer$openBlockList(MouseButtonEvent click, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        String name = this.config.getName();
        if (!BLOCK_LIST_CONFIGS.contains(name)) {
            return;
        }

        Set<Block> selected = new HashSet<>();
        List<String> rawEntries = new ArrayList<>();
        for (String id : this.config.getStrings()) {
            Block block = litematica_printer$resolveBlock(id);
            if (block != null) {
                selected.add(block);
            } else {
                rawEntries.add(id);
            }
        }

        GuiBase.openGui(new ChooseBlockScreen(selected, rawEntries, blocks -> {
            List<String> ids = new ArrayList<>();
            for (Block block : blocks) {
                ids.add(BlockUtils.getKeyString(block));
            }
            ids.addAll(rawEntries);
            this.config.setStrings(ids);
        }));

        cir.setReturnValue(true);
    }

    /** 仅解析纯方块 ID；标签、方块状态、中文名、包含匹配等一律视为原始条目保留 */
    private static Block litematica_printer$resolveBlock(String entry) {
        if (entry == null || entry.isEmpty()
                || entry.startsWith("#") || entry.contains("[") || entry.contains(",") || entry.contains(" ")) {
            return null;
        }
        try {
            Block block = BlockUtils.getBlock(IdentifierUtils.of(entry));
            return block == Blocks.AIR ? null : block;
        } catch (Exception ignored) {
            return null;
        }
    }
}
