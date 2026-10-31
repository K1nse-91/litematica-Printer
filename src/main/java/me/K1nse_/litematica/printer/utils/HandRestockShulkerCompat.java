package me.K1nse_.litematica.printer.utils;

import me.K1nse_.litematica.printer.config.Configs;
import me.K1nse_.litematica.printer.printer.zxy.inventory.InventoryUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 手持物品自动补货（移植自 EMT）：手里的方块/烟花用完后，自动用快速潜影盒从盒中取同款补回原槽位。
 */
public final class HandRestockShulkerCompat {
    private static final int OFFHAND_INVENTORY_SLOT = 40;
    private static final int INVENTORY_SLOT_COUNT = 41;
    private static final int DROP_SUPPRESS_TICKS = 3;
    private static final int CRAFT_GRID_SLOT_COUNT = 4;

    private static Item pendingItem;
    private static int pendingTargetInventorySlot = -1;
    private static final ItemStack[] prevSlots = new ItemStack[INVENTORY_SLOT_COUNT];
    private static final ItemStack[] prevCraftGrid = new ItemStack[CRAFT_GRID_SLOT_COUNT];
    private static long lastLocalDropTick = Long.MIN_VALUE;

    private HandRestockShulkerCompat() {
    }

    public static void onHandStackConsumed(Player player, InteractionHand hand, ItemStack before, ItemStack after) {
        if (!(player instanceof LocalPlayer localPlayer) || before == null || before.isEmpty()) {
            return;
        }
        Item item = before.getItem();
        boolean shrunk = after == null || after.isEmpty()
                || (after.is(item) && after.getCount() < before.getCount());
        if (item == Items.FIREWORK_ROCKET && !player.isCreative()) {
            tryRestock(localPlayer, hand, item, 1);
        } else if (shrunk) {
            tryRestock(localPlayer, hand, item, 0);
        }
    }

    private static void tryRestock(LocalPlayer player, InteractionHand hand, Item item, int predictedDepletion) {
        if (!Configs.Placement.HAND_RESTOCK_SHULKER_COMPAT.getBooleanValue()
                || !Configs.Placement.QUICK_SHULKER.getBooleanValue()) {
            return;
        }
        if (InventoryUtils.isOpenHandler || InventoryUtils.hasRecentlyOpenedShulker(player)) {
            return;
        }
        int targetSlot = hand == InteractionHand.OFF_HAND ? OFFHAND_INVENTORY_SLOT : player.getInventory().getSelectedSlot();
        if (targetSlot < 0) {
            return;
        }
        if (countMainInventoryIncludingOffhand(player, item) - predictedDepletion <= 0
                && InventoryUtils.countAvailableIncludingShulkers(player, item) > 0) {
            pendingItem = item;
            pendingTargetInventorySlot = targetSlot;
            InventoryUtils.addQuickShulkerDemand(item);
        }
    }

    private static int countMainInventoryIncludingOffhand(LocalPlayer player, Item item) {
        int count = me.K1nse_.litematica.printer.utils.InventoryUtils.countMatchingMainInventory(player, stack -> stack.is(item));
        ItemStack offhand = player.getInventory().getItem(OFFHAND_INVENTORY_SLOT);
        if (!offhand.isEmpty() && offhand.is(item)) {
            count += offhand.getCount();
        }
        return count;
    }

    /** 快速潜影盒取物完成后调用：把物品搬回原槽位 */
    public static void onQuickShulkerTransferFinished(Player player) {
        Item item = pendingItem;
        int targetInventorySlot = pendingTargetInventorySlot;
        clearPendingReturn();
        if (item != null && targetInventorySlot >= 0 && player instanceof LocalPlayer localPlayer
                && player.getInventory().getItem(targetInventorySlot).isEmpty()) {
            moveStackToInventorySlot(localPlayer, item, targetInventorySlot);
        }
    }

    public static void markLocalDrop() {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            lastLocalDropTick = currentTick(client.player);
        }
    }

    public static void clientTick(LocalPlayer player) {
        detectPassiveConsumption(player);
    }

    private static void detectPassiveConsumption(LocalPlayer player) {
        Inventory inventory = player.getInventory();
        boolean quiet = player.isAlive() && player.containerMenu.equals(player.inventoryMenu)
                && player.inventoryMenu.getCarried().isEmpty();
        if (quiet) {
            long tick = currentTick(player);
            boolean dropRecent = lastLocalDropTick != Long.MIN_VALUE
                    && tick >= lastLocalDropTick && tick - lastLocalDropTick <= DROP_SUPPRESS_TICKS;
            for (int slot = 0; slot < INVENTORY_SLOT_COUNT && !dropRecent; slot++) {
                ItemStack before = prevSlots[slot];
                if (before != null && !before.isEmpty() && inventory.getItem(slot).isEmpty()) {
                    Item item = before.getItem();
                    int beforeTotal = countItem(prevSlots, item) + countItemInStacks(prevCraftGrid, item);
                    int afterTotal = countItem(inventory, item) + countCraftGrid(player, item);
                    if (beforeTotal > afterTotal) {
                        tryRestockPassive(player, item, slot);
                        break;
                    }
                }
            }
        }
        for (int slot = 0; slot < INVENTORY_SLOT_COUNT; slot++) {
            prevSlots[slot] = inventory.getItem(slot).copy();
        }
        snapshotCraftGrid(player);
    }

    private static void tryRestockPassive(LocalPlayer player, Item item, int slot) {
        if (!Configs.Placement.HAND_RESTOCK_SHULKER_COMPAT.getBooleanValue()
                || !Configs.Placement.QUICK_SHULKER.getBooleanValue()) {
            return;
        }
        if (InventoryUtils.isOpenHandler || InventoryUtils.hasRecentlyOpenedShulker(player)) {
            return;
        }
        if (InventoryUtils.countAvailableIncludingShulkers(player, item) > 0) {
            pendingItem = item;
            pendingTargetInventorySlot = slot;
            InventoryUtils.addQuickShulkerDemand(item);
        }
    }

    public static void clearPendingReturn() {
        pendingItem = null;
        pendingTargetInventorySlot = -1;
    }

    private static int countCraftGrid(LocalPlayer player, Item item) {
        AbstractContainerMenu menu = player.inventoryMenu;
        int count = 0;
        for (int i = 0; i < CRAFT_GRID_SLOT_COUNT && 1 + i < menu.slots.size(); i++) {
            ItemStack stack = menu.getSlot(1 + i).getItem();
            if (!stack.isEmpty() && stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static void snapshotCraftGrid(LocalPlayer player) {
        AbstractContainerMenu menu = player.inventoryMenu;
        for (int i = 0; i < CRAFT_GRID_SLOT_COUNT; i++) {
            int menuSlot = 1 + i;
            prevCraftGrid[i] = menuSlot < menu.slots.size() ? menu.getSlot(menuSlot).getItem().copy() : ItemStack.EMPTY;
        }
    }

    private static int countItemInStacks(ItemStack[] stacks, Item item) {
        int count = 0;
        for (ItemStack stack : stacks) {
            if (stack != null && !stack.isEmpty() && stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static int countItem(ItemStack[] slots, Item item) {
        int count = 0;
        for (int slot = 0; slot < INVENTORY_SLOT_COUNT; slot++) {
            ItemStack stack = slots[slot];
            if (stack != null && !stack.isEmpty() && stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static int countItem(Inventory inventory, Item item) {
        int count = 0;
        for (int slot = 0; slot < INVENTORY_SLOT_COUNT; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty() && stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static void moveStackToInventorySlot(LocalPlayer player, Item item, int targetInventorySlot) {
        Minecraft client = Minecraft.getInstance();
        if (client.gameMode == null) {
            return;
        }
        AbstractContainerMenu menu = player.containerMenu;
        Inventory inventory = player.getInventory();
        int sourceInventorySlot = -1;
        int size = Math.min(36, inventory.getContainerSize());
        for (int slot = 0; slot < size; slot++) {
            if (slot != targetInventorySlot) {
                ItemStack stack = inventory.getItem(slot);
                if (!stack.isEmpty() && stack.is(item)) {
                    sourceInventorySlot = slot;
                    break;
                }
            }
        }
        if (sourceInventorySlot < 0 && targetInventorySlot != OFFHAND_INVENTORY_SLOT
                && !inventory.getItem(OFFHAND_INVENTORY_SLOT).isEmpty()
                && inventory.getItem(OFFHAND_INVENTORY_SLOT).is(item)) {
            sourceInventorySlot = OFFHAND_INVENTORY_SLOT;
        }
        if (sourceInventorySlot < 0) {
            return;
        }
        int sourceMenuSlot = findMenuSlotForInventorySlot(menu, player, sourceInventorySlot);
        if (sourceMenuSlot < 0) {
            return;
        }
        if (targetInventorySlot == OFFHAND_INVENTORY_SLOT) {
            client.gameMode.handleContainerInput(menu.containerId, sourceMenuSlot, OFFHAND_INVENTORY_SLOT,
                    ContainerInput.SWAP, player);
            return;
        }
        int targetMenuSlot = findMenuSlotForInventorySlot(menu, player, targetInventorySlot);
        if (targetMenuSlot < 0) {
            return;
        }
        client.gameMode.handleContainerInput(menu.containerId, sourceMenuSlot, 0, ContainerInput.PICKUP, player);
        client.gameMode.handleContainerInput(menu.containerId, targetMenuSlot, 0, ContainerInput.PICKUP, player);
        if (!menu.getCarried().isEmpty()) {
            client.gameMode.handleContainerInput(menu.containerId, sourceMenuSlot, 0, ContainerInput.PICKUP, player);
        }
    }

    private static int findMenuSlotForInventorySlot(AbstractContainerMenu menu, LocalPlayer player, int inventorySlot) {
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot slot = menu.slots.get(i);
            if (slot.container == player.getInventory() && slot.getContainerSlot() == inventorySlot) {
                return i;
            }
        }
        return -1;
    }

    private static long currentTick(Player player) {
        return player.level() == null ? 0L : player.level().getGameTime();
    }
}
