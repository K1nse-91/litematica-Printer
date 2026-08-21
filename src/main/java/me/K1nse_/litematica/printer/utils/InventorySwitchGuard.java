package me.K1nse_.litematica.printer.utils;

import me.K1nse_.litematica.printer.handler.ClientPlayerTickManager;
import me.K1nse_.litematica.printer.printer.ActionManager;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.Item;

public final class InventorySwitchGuard {
    private static final Minecraft client = Minecraft.getInstance();
    private static final int MAX_WAIT_TICKS = 10;

    private static Item pendingItem;
    private static long pendingStartedTick;
    private static int pendingStartedPacketEpoch;

    private InventorySwitchGuard() {
    }

    public static void reset() {
        clear();
    }

    public static boolean markSwitchIfNeeded(Item item) {
        if (item == null) {
            return false;
        }
        pendingItem = item;
        pendingStartedTick = ClientPlayerTickManager.getCurrentHandlerTime();
        pendingStartedPacketEpoch = ClientPlayerTickManager.getPacketEpoch();
        ActionManager.INSTANCE.clearQueue();
        return true;
    }

    public static boolean isWaiting() {
        if (pendingItem == null) {
            return false;
        }
        ActionManager.INSTANCE.clearQueue();
        long age = ClientPlayerTickManager.getCurrentHandlerTime() - pendingStartedTick;
        if (age > MAX_WAIT_TICKS) {
            clear();
            return false;
        }
        if (age <= 0) {
            return true;
        }
        if (isMainHandReady(pendingItem) && ClientPlayerTickManager.getPacketEpoch() > pendingStartedPacketEpoch) {
            clear();
            return false;
        }
        return true;
    }

    private static void clear() {
        pendingItem = null;
        pendingStartedTick = 0L;
        pendingStartedPacketEpoch = 0;
    }

    private static boolean isMainHandReady(Item item) {
        return client.player != null && client.player.getMainHandItem().is(item);
    }
}
