package me.K1nse_.litematica.printer.printer.zxy.inventory;

import me.K1nse_.litematica.printer.I18n;
import me.K1nse_.litematica.printer.utils.minecraft.MessageUtils;
import me.K1nse_.litematica.printer.utils.mods.ModLoadUtils;
import me.K1nse_.litematica.printer.utils.mods.ShulkerUtils;
import me.K1nse_.litematica.printer.config.Configs;
import me.K1nse_.litematica.printer.mixin.printer.litematica.InventoryUtilsAccessor;
import me.K1nse_.litematica.printer.printer.zxy.utils.ZxyUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;

public class InventoryUtils {
    private static int shulkerCooldown = 0;
    private static int openHandlerTimeout = 0;
    private static final int OPEN_HANDLER_TIMEOUT_TICKS = 40;

    /** AxShulkers 盲开：本轮已试过（打开后发现没货）的背包槽位，避免重复试空盒。 */
    private static final java.util.Set<Integer> axShulkersAttemptedSlots = new java.util.HashSet<>();

    /** 缺失冷却：所有盒子都找过仍没有时，冷却期内不再盲开，避免无限寻找。 */
    private static final int MISSING_ITEM_COOLDOWN_TICKS = 100;
    /** 取物失败冷却：盒子里有货但取不出（背包满等）时短冷却后重试，不标记该盒已试。 */
    private static final int RETRY_ITEM_COOLDOWN_TICKS = 20;
    private static final Map<Item, Integer> missingItemCooldown = new HashMap<>();

    private static final Minecraft client = Minecraft.getInstance();

    public static boolean isInventory(Level world, BlockPos pos) {
        return fi.dy.masa.malilib.util.InventoryUtils.getInventory(world, pos) != null;
    }

    public static boolean canOpenInv(BlockPos pos) {
        if (client.level != null) {
            BlockState blockState = client.level.getBlockState(pos);
            BlockEntity blockEntity = client.level.getBlockEntity(pos);
            boolean isInventory = InventoryUtils.isInventory(client.level, pos);
            try {
                if ((isInventory && blockState.getMenuProvider(client.level, pos) == null) ||
                        (blockEntity instanceof ShulkerBoxBlockEntity entity &&
                                //#if MC > 260100
                                //$$ !client.level.noCollision(Shulker.getProgressDeltaAabb(1.0F, blockState.getValue(BlockStateProperties.FACING), 0.0F, 0.5F, Vec3.atBottomCenterOf(pos)).move(pos).deflate(1.0E-6)) &&
                                //#elseif MC > 12103
                                !client.level.noCollision(Shulker.getProgressDeltaAabb(1.0F, blockState.getValue(BlockStateProperties.FACING), 0.0F, 0.5F, pos.getBottomCenter()).move(pos).deflate(1.0E-6)) &&
                                //#elseif MC <= 12103 && MC > 12004
                                //$$ !client.level.noCollision(Shulker.getProgressDeltaAabb(1.0F, blockState.getValue(BlockStateProperties.FACING), 0.0F, 0.5F).move(pos).deflate(1.0E-6)) &&
                                //#elseif MC <= 12004
                                //$$ !client.level.noCollision(Shulker.getProgressDeltaAabb(blockState.getValue(BlockStateProperties.FACING), 0.0f, 0.5f).move(pos).deflate(1.0E-6)) &&
                                //#endif
                                entity.getAnimationStatus() == ShulkerBoxBlockEntity.AnimationStatus.CLOSED)) {
                    return false;
                } else if (!isInventory) {
                    return false;
                }
            } catch (Exception e) {
                return false;
            }
            return true;
        } else {
            return false;
        }
    }

    public static HashSet<Item> lastNeedItemList = new LinkedHashSet<>();
    public static boolean isOpenHandler = false;

    public static boolean switchItem() {
        if (!lastNeedItemList.isEmpty() && !isOpenHandler) {
            LocalPlayer player = client.player;
            if (player == null) {
                clearSwitchRequest();
                return false;
            }
            AbstractContainerMenu sc = player.containerMenu;
            if (!player.containerMenu.equals(player.inventoryMenu)) return true;
            //排除合成栏 装备栏 副手
            if (Configs.Placement.STORE_ORDERLY.getBooleanValue() && sc.slots.stream().skip(9).limit(sc.slots.size() - 10).noneMatch(slot -> slot.getItem().isEmpty())
                    && Configs.Placement.QUICK_SHULKER.getBooleanValue()) {
                SwitchItem.checkItems();
                return true;
            }

            if (Configs.Placement.QUICK_SHULKER.getBooleanValue()) {
                if (shulkerCooldown > 0) {
                    return true;
                }
                if (openShulker(lastNeedItemList)) {
                    return true;
                }
            }
            clearSwitchRequest();
        }
        return false;
    }

    public static boolean hasPendingSwitchRequest() {
        return isOpenHandler || !lastNeedItemList.isEmpty();
    }

    public static boolean shouldPauseForSwitchRequest() {
        return Configs.Placement.QUICK_SHULKER.getBooleanValue() && hasPendingSwitchRequest();
    }

    public static void resetRuntime() {
        clearSwitchRequest();
        shulkerCooldown = 0;
        ModLoadUtils.closeScreen = 0;
    }

    static int shulkerBoxSlot = -1;

    public static void switchInv() {
        LocalPlayer player = Minecraft.getInstance().player;
        AbstractContainerMenu sc = player.containerMenu;
        if (sc.equals(player.inventoryMenu)) {
            return;
        }
        NonNullList<Slot> slots = sc.slots;
        boolean foundTarget = false;
        for (Item item : lastNeedItemList) {
            for (int y = 0; y < slots.get(0).container.getContainerSize(); y++) {
                if (slots.get(y).getItem().getItem().equals(item)) {
                    foundTarget = true;
                    String[] str = fi.dy.masa.litematica.config.Configs.Generic.PICK_BLOCKABLE_SLOTS.getStringValue().split(",");
                    if (str.length == 0) return;
                    for (String s : str) {
                        if (s == null) break;
                        try {
                            int c = Integer.parseInt(s) - 1;
                            if (BuiltInRegistries.ITEM.getKey(player.getInventory().getItem(c).getItem()).toString().contains("shulker_box") &&
                                    Configs.Placement.QUICK_SHULKER.getBooleanValue()) {
                                MessageUtils.setOverlayMessage(I18n.INVENTORY_SHULKER_OCCUPIED.getName(), false);
                                continue;
                            }
                            SwitchItem.newItem(slots.get(y).getItem(), y, shulkerBoxSlot);
                            int a = InventoryUtilsAccessor.getEmptyPickBlockableHotbarSlot(player.getInventory()) == -1 ?
                                    InventoryUtilsAccessor.getPickBlockTargetSlot(player) :
                                    InventoryUtilsAccessor.getEmptyPickBlockableHotbarSlot(player.getInventory());
                            c = a == -1 ? c : a;
                            ZxyUtils.switchPlayerInvToHotbarAir(c);
                            fi.dy.masa.malilib.util.InventoryUtils.swapSlots(sc, y, c);
                            me.K1nse_.litematica.printer.utils.InventoryUtils.setSelectedSlot(player.getInventory(), c);
                            me.K1nse_.litematica.printer.utils.InventoryUtils.syncSelectedHotbarSlot();
                            me.K1nse_.litematica.printer.utils.InventorySwitchGuard.markSwitchIfNeeded(item);
                            player.closeContainer();
                            //刷新濳影盒
                            if (shulkerBoxSlot != -1) {
                                client.gameMode.handleContainerInput(sc.containerId, shulkerBoxSlot, 0, ContainerInput.PICKUP, client.player);
                                client.gameMode.handleContainerInput(sc.containerId, shulkerBoxSlot, 0, ContainerInput.PICKUP, client.player);
                            }
                            clearSwitchRequest();
                            return;
                        } catch (Exception e) {
                            System.out.println("Item switch error");
                        }
                    }
                }
            }
        }
        // AxShulkers 盲开：打开的容器里没有目标物品 → 记录该槽位已试，并立即清空请求恢复调度。
        // 每次请求最多盲开 1 个盒子，避免连续开盒（每次一次网络往返）暂停拖慢其他模式；
        // 打印器下一个方块需要材料时再试下一个未试过的盒子（已试记录跨请求保留）。
        if (shulkerBoxSlot >= 0
                && shulkerBoxSlot < player.inventoryMenu.slots.size()) {
            ItemStack source = player.inventoryMenu.slots.get(shulkerBoxSlot).getItem();
            if (ShulkerUtils.shouldBlindOpen(source)) {
                if (foundTarget) {
                    // 盒子里有目标物品但取物失败（背包满/槽位占用等暂时问题）→ 短冷却后重试，不标记该盒已试
                    for (Item item : lastNeedItemList) {
                        missingItemCooldown.put(item, RETRY_ITEM_COOLDOWN_TICKS);
                    }
                } else {
                    // 盒子里确实没有目标物品 → 标记已试，下一次请求再试下一个盒子
                    axShulkersAttemptedSlots.add(shulkerBoxSlot);
                }
                shulkerBoxSlot = -1;
                isOpenHandler = false;
                openHandlerTimeout = 0;
                lastNeedItemList = new LinkedHashSet<>(); // 清空请求，恢复调度
                AbstractContainerMenu sc2 = player.containerMenu;
                if (!sc2.equals(player.inventoryMenu)) {
                    player.closeContainer();
                }
                return;
            }
        }
        clearSwitchRequest();
        AbstractContainerMenu sc2 = player.containerMenu;
        if (!sc2.equals(player.inventoryMenu)) {
            player.closeContainer();
        }
    }

    private static boolean openShulker(HashSet<Item> items) {
        if (shulkerCooldown > 0) {
            return false;
        }
        for (Item item : items) {
            if (missingItemCooldown.containsKey(item)) {
                continue; // 缺失/取物失败冷却中，跳过该物品
            }
            AbstractContainerMenu sc = Minecraft.getInstance().player.inventoryMenu;
            for (int i = 9; i < sc.slots.size(); i++) {
                ItemStack stack = sc.slots.get(i).getItem();
                String itemid = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                if (itemid.contains("shulker_box") && stack.getCount() == 1) {
                    // AxShulkers：内容在服务器数据库，本地读不到 → 盲开（打开后由 switchInv 检查容器内容）
                    if (ShulkerUtils.shouldBlindOpen(stack)) {
                        if (axShulkersAttemptedSlots.contains(i)) {
                            continue; // 本轮已试过该槽位（空盒），跳过
                        }
                        try {
                            shulkerBoxSlot = i;
                            if (!ShulkerUtils.openShulker(stack, shulkerBoxSlot)) {
                                shulkerBoxSlot = -1;
                                continue;
                            }
                            ModLoadUtils.closeScreen++;
                            isOpenHandler = true;
                            openHandlerTimeout = OPEN_HANDLER_TIMEOUT_TICKS;
                            shulkerCooldown = Configs.Placement.QUICK_SHULKER_COOLDOWN.getIntegerValue();
                            return true;
                        } catch (Exception e) {
                            shulkerBoxSlot = -1;
                            axShulkersAttemptedSlots.add(i);
                        }
                    }
                    NonNullList<ItemStack> items1 = fi.dy.masa.malilib.util.InventoryUtils.getStoredItems(stack, -1);
                    if (items1.stream().anyMatch(s1 -> s1.getItem().equals(item))) {
                        try {
                            shulkerBoxSlot = i;
                            if (!ShulkerUtils.openShulker(stack, shulkerBoxSlot)) {
                                shulkerBoxSlot = -1;
                                continue;
                            }
                            ModLoadUtils.closeScreen++;
                            isOpenHandler = true;
                            openHandlerTimeout = OPEN_HANDLER_TIMEOUT_TICKS;
                            shulkerCooldown = Configs.Placement.QUICK_SHULKER_COOLDOWN.getIntegerValue();
                            return true;
                        } catch (Exception e) {
                        }
                    }
                }
            }
        }
        // 所有物品在所有潜影盒里都没找到 → 标记缺失冷却，避免打印机持续请求时下一 tick 又全部重试一遍
        for (Item item : items) {
            if (!missingItemCooldown.containsKey(item)) {
                missingItemCooldown.put(item, MISSING_ITEM_COOLDOWN_TICKS);
            }
        }
        return false;
    }

    public static void tick() {
        if (ModLoadUtils.closeScreen > 0) {
            ModLoadUtils.closeScreen--;
        }
        if (isOpenHandler && openHandlerTimeout > 0 && --openHandlerTimeout <= 0) {
            clearSwitchRequest();
        }
        if (shulkerCooldown > 0) {
            shulkerCooldown--;
        }
        // 缺失/取物失败冷却递减，到期后允许重新寻找
        if (!missingItemCooldown.isEmpty()) {
            Iterator<Map.Entry<Item, Integer>> it = missingItemCooldown.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<Item, Integer> entry = it.next();
                int left = entry.getValue() - 1;
                if (left <= 0) {
                    it.remove();
                } else {
                    entry.setValue(left);
                }
            }
        }
    }

    private static void clearSwitchRequest() {
        shulkerBoxSlot = -1;
        lastNeedItemList = new LinkedHashSet<>();
        isOpenHandler = false;
        openHandlerTimeout = 0;
        axShulkersAttemptedSlots.clear();
    }
}
