package me.K1nse_.litematica.printer.mixin.printer.malilib;

import fi.dy.masa.malilib.config.IConfigStringList;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.button.ConfigButtonStringList;
import me.K1nse_.litematica.printer.gui.ChooseBlockScreen;
import me.K1nse_.litematica.printer.utils.minecraft.BlockUtils;
import me.K1nse_.litematica.printer.utils.minecraft.IdentifierUtils;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.world.level.block.Block;
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
 * 覆盖：方块破坏限制、挖掘、填充、排流体、破基岩 的黑/白名单及方块名单。
 */
@Mixin(value = ConfigButtonStringList.class, remap = false)
public abstract class MixinConfigButtonStringList {
    /** 所有应使用可视化方块选择界面的名单配置名 */
    private static final Set<String> BLOCK_LIST_CONFIGS = Set.of(
            "breakWhitelist", "breakBlacklist",       // 方块破坏限制 白/黑名单
            "excavateWhitelist", "excavateBlacklist", // 挖掘 白/黑名单
            "bedrockWhitelist",                       // 破基岩 白名单
            "fillBlockList",                          // 填充 方块名单
            "fluidReplaceBlockList", "fluidList"      // 排流体 方块名单 / 液体名单
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
        for (String id : this.config.getStrings()) {
            Block block = BlockUtils.getBlock(IdentifierUtils.of(id));
            if (block != null) {
                selected.add(block);
            }
        }

        GuiBase.openGui(new ChooseBlockScreen(selected, blocks -> {
            List<String> ids = new ArrayList<>();
            for (Block block : blocks) {
                ids.add(BlockUtils.getKeyString(block));
            }
            this.config.setStrings(ids);
        }));

        cir.setReturnValue(true);
    }
}