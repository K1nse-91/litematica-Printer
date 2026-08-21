package me.K1nse_.litematica.printer.printer.zxy.utils;

import me.K1nse_.litematica.printer.printer.zxy.inventory.SwitchItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

public class ZxyUtils {
    private static final Minecraft client = Minecraft.getInstance();

    public static void switchPlayerInvToHotbarAir(int slot) {
        if (client.player == null) return;
        LocalPlayer player = client.player;
        AbstractContainerMenu sc = player.containerMenu;
        NonNullList<Slot> slots = sc.slots;
        int i = sc.equals(player.inventoryMenu) ? 9 : 0;
        for (; i < slots.size(); i++) {
            if (slots.get(i).getItem().isEmpty() && slots.get(i).container instanceof Inventory) {
                fi.dy.masa.malilib.util.InventoryUtils.swapSlots(sc, i, slot);
                return;
            }
        }
    }

    public static void exitGameReSet() {
        SwitchItem.reSet();
    }
}
