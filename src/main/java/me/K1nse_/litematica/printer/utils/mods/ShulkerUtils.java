package me.K1nse_.litematica.printer.utils.mods;

import fi.dy.masa.malilib.config.IConfigOptionListEntry;
import me.K1nse_.litematica.printer.config.Configs;
import me.K1nse_.litematica.printer.enums.QuickShulkerModeType;
import me.K1nse_.litematica.printer.I18n;
import me.K1nse_.litematica.printer.utils.minecraft.MessageUtils;
import net.kyrptonaught.quickshulker.client.ClientUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

@SuppressWarnings({"DataFlowIssue", "SpellCheckingInspection"})
public class ShulkerUtils {
    static final Minecraft client = Minecraft.getInstance();

    public static boolean openShulker(ItemStack stack, int shulkerBoxSlot) {
        if (client.player == null || client.gameMode == null) {
            return false;
        }
        IConfigOptionListEntry openMode = Configs.Placement.QUICK_SHULKER_MODE.getOptionListValue();
        if (openMode == QuickShulkerModeType.CLICK_SLOT
                || openMode == QuickShulkerModeType.AXSHULKERS
                || shouldBlindOpen(stack)) {
            client.gameMode.handleContainerInput(client.player.containerMenu.containerId, shulkerBoxSlot, 1, ContainerInput.PICKUP, client.player);
            return true;
        } else if (openMode == QuickShulkerModeType.INVOKE) {
            if (ModLoadUtils.isQuickShulkerLoaded()) {
                try {
                    ClientUtil.CheckAndSend(stack, shulkerBoxSlot);
                    return true;
                } catch (Exception ignored) {
                    return false;
                }
            } else {
                MessageUtils.addMessage(I18n.SHULKER_MOD_NOT_LOADED.getName());
            }
        }
        return false;
    }

    /** 判断是否为 AxShulkers 服务器插件管理的潜影盒（内容在服务器数据库，客户端只有 UUID 标签）。 */
    public static boolean isAxShulkersShulker(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        //#if MC > 12101
        CustomData customData = stack.getComponents().get(DataComponents.CUSTOM_DATA);
        if (customData != null) {
            CompoundTag tag = customData.copyTag();
            return tag != null && tag.contains("AxShulkers-UUID");
        }
        return false;
        //#else
        //$$ return stack.getTag() != null && stack.getTag().contains("AxShulkers-UUID");
        //#endif
    }

    /** AxShulkers 潜影盒本地读不到内容 → 必须盲开（跳过预检，打开后检查容器内容）。 */
    public static boolean shouldBlindOpen(ItemStack stack) {
        return isAxShulkersShulker(stack);
    }
}