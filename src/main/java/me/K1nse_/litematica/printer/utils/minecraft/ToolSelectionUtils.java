package me.K1nse_.litematica.printer.utils.minecraft;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Optional;

/**
 * 工具选择辅助：精准采集判定与"保掉落"用方块识别。
 * 用于挖掘模式在最佳工具能保住掉落物、而手持工具没有精准采集时强制换工具。
 */
public final class ToolSelectionUtils {
    private ToolSelectionUtils() {
    }

    /** 只有这类方块（玻璃类）需要精准采集才能保住掉落物 */
    public static boolean prefersSilkTouchForDrops(BlockState state) {
        if (state == null) {
            return false;
        }
        String path = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        return path.equals("glass")
                || path.equals("glass_pane")
                || path.endsWith("_stained_glass")
                || path.endsWith("_stained_glass_pane");
    }

    public static boolean hasSilkTouch(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        //#if MC > 12006
        for (Holder<Enchantment> enchantment : stack.getEnchantments().keySet()) {
            Optional<ResourceKey<Enchantment>> enchantmentKey = enchantment.unwrapKey();
            if (enchantmentKey.isPresent() && enchantmentKey.get() == Enchantments.SILK_TOUCH) {
                return true;
            }
        }
        //#else
        //$$ if (EnchantmentHelper.getItemEnchantmentLevel(Enchantments.SILK_TOUCH, stack) > 0) {
        //$$     return true;
        //$$ }
        //#endif
        return false;
    }
}
