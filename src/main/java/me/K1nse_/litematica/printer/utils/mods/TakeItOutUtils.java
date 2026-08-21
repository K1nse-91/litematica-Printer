package me.K1nse_.litematica.printer.utils.mods;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.NonNullList;
import me.K1nse_.litematica.printer.printer.ActionManager;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ShulkerBoxBlock;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

public final class TakeItOutUtils {
    private static final String MOD_ID = "takeitout";
    private static final String CLIENT_CLASS = "net.maxbel.takeitout.client.TakeitoutClient";
    private static final String SOURCES_CLASS = "net.maxbel.takeitout.client.WorldContainerSources";
    private static final String SHULKER_PAYLOAD_CLASS = "net.maxbel.takeitout.Takeitout$GetShulkerStackPayload";
    private static final String CLIENT_PLAY_NETWORKING_CLASS = "net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking";
    private static final long MAX_LOCAL_PENDING_MS = 4_000L;
    private static final int SETTLE_TICKS_AFTER_ARRIVAL = 0;

    private static final Minecraft client = Minecraft.getInstance();
    private static Item localPendingItem;
    private static long localPendingStartedAtMs;
    private static int localPendingSettleTicks = -1;

    private TakeItOutUtils() {
    }

    public static boolean isLoaded() {
        return FabricLoader.getInstance().isModLoaded(MOD_ID);
    }

    public static boolean isAutoTakeoutEnabled() {
        if (!isLoaded()) {
            return false;
        }
        try {
            Field field = Class.forName(CLIENT_CLASS).getField("AUTOTAKEOUT");
            return field.getBoolean(null);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return false;
        }
    }

    public static boolean isAwaitingStack() {
        ItemStack awaitingStack = getAwaitingStack();
        if (awaitingStack != null && !awaitingStack.isEmpty()) {
            beginLocalPending(awaitingStack.getItem());
            return true;
        }
        return isLocalPending();
    }

    public static boolean tryRequestItem(Item item) {
        if (item == null || !isAutoTakeoutEnabled()) {
            return false;
        }
        if (isAwaitingStack()) {
            return true;
        }

        ItemStack required = new ItemStack(item);
        if (required.isEmpty()) {
            return false;
        }
        return tryRequestFromInventoryShulker(required) || tryRequestFromWorldContainers(required);
    }

    public static void resetPending() {
        clearLocalPending();
        if (!isLoaded()) {
            return;
        }
        try {
            setAwaitingStack(ItemStack.EMPTY);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private static boolean tryRequestFromWorldContainers(ItemStack required) {
        try {
            Class<?> sourcesClass = Class.forName(SOURCES_CLASS);
            Method requestStack = sourcesClass.getMethod(
                    "requestStack",
                    Minecraft.class,
                    ItemStack.class,
                    boolean.class,
                    boolean.class
            );
            Object result = requestStack.invoke(null, client, singleStack(required), isSingleItemMode(), false);
            if (Boolean.TRUE.equals(result)) {
                beginLocalPending(required.getItem());
                return true;
            }
            return false;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return false;
        }
    }

    private static boolean tryRequestFromInventoryShulker(ItemStack required) {
        if (client.player == null) {
            return false;
        }
        Inventory inventory = client.player.getInventory();
        int size = Math.min(36, inventory.getContainerSize());
        for (int shulkerSlot = 0; shulkerSlot < size; shulkerSlot++) {
            ItemStack shulker = inventory.getItem(shulkerSlot);
            if (!isSingleShulker(shulker)) {
                continue;
            }
            int innerSlot = findStoredItemSlot(shulker, required.getItem());
            if (innerSlot == -1) {
                continue;
            }
            return sendShulkerRequest(innerSlot, shulkerSlot, required);
        }
        return false;
    }

    private static boolean sendShulkerRequest(int innerSlot, int shulkerSlot, ItemStack required) {
        try {
            Class<?> payloadClass = Class.forName(SHULKER_PAYLOAD_CLASS);
            Constructor<?> constructor = payloadClass.getConstructor(int.class, int.class, boolean.class);
            Object payload = constructor.newInstance(innerSlot, shulkerSlot, isSingleItemMode());
            if (!canSend(payloadClass)) {
                return false;
            }
            setAwaitingStack(singleStack(required));
            sendPayload(payload);
            beginLocalPending(required.getItem());
            return true;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            clearAwaitingStackIfSameItem(required);
            return false;
        }
    }

    private static boolean canSend(Class<?> payloadClass) {
        try {
            Field idField = payloadClass.getField("ID");
            Object id = idField.get(null);
            Class<?> networkingClass = Class.forName(CLIENT_PLAY_NETWORKING_CLASS);
            for (Method method : networkingClass.getMethods()) {
                if (!method.getName().equals("canSend") || method.getParameterCount() != 1) {
                    continue;
                }
                if (!method.getParameterTypes()[0].isInstance(id)) {
                    continue;
                }
                Object result = method.invoke(null, id);
                return Boolean.TRUE.equals(result);
            }
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return true;
        }
        return true;
    }

    private static void beginLocalPending(Item item) {
        if (item == null) {
            return;
        }
        localPendingItem = item;
        localPendingStartedAtMs = System.currentTimeMillis();
        localPendingSettleTicks = -1;
        ActionManager.INSTANCE.clearQueue();
    }

    private static boolean isLocalPending() {
        if (localPendingItem == null) {
            return false;
        }
        ActionManager.INSTANCE.clearQueue();
        if (System.currentTimeMillis() - localPendingStartedAtMs > MAX_LOCAL_PENDING_MS) {
            clearLocalPending();
            return false;
        }
        if (!hasInventoryItem(localPendingItem)) {
            return true;
        }
        if (localPendingSettleTicks < 0) {
            localPendingSettleTicks = SETTLE_TICKS_AFTER_ARRIVAL;
        }
        if (localPendingSettleTicks-- > 0) {
            return true;
        }
        clearLocalPending();
        return false;
    }

    private static boolean hasInventoryItem(Item item) {
        if (client.player == null || item == null) {
            return false;
        }
        Inventory inventory = client.player.getInventory();
        int size = Math.min(36, inventory.getContainerSize());
        for (int slot = 0; slot < size; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty() && stack.is(item)) {
                return true;
            }
        }
        return false;
    }

    private static void clearLocalPending() {
        localPendingItem = null;
        localPendingStartedAtMs = 0L;
        localPendingSettleTicks = -1;
    }

    private static void sendPayload(Object payload) throws ReflectiveOperationException {
        Class<?> networkingClass = Class.forName(CLIENT_PLAY_NETWORKING_CLASS);
        for (Method method : networkingClass.getMethods()) {
            if (!method.getName().equals("send")
                    || method.getParameterCount() != 1
                    || !Modifier.isStatic(method.getModifiers())) {
                continue;
            }
            if (method.getParameterTypes()[0].isInstance(payload)) {
                method.invoke(null, payload);
                return;
            }
        }
        throw new NoSuchMethodException("ClientPlayNetworking.send(payload)");
    }

    private static boolean isSingleItemMode() {
        try {
            Field field = Class.forName(CLIENT_CLASS).getField("TAKE_SINGLE_ITEM_MODE");
            return field.getBoolean(null);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return false;
        }
    }

    private static ItemStack getAwaitingStack() {
        if (!isLoaded()) {
            return ItemStack.EMPTY;
        }
        try {
            Object value = Class.forName(CLIENT_CLASS).getField("awaitingStack").get(null);
            return value instanceof ItemStack stack ? stack : ItemStack.EMPTY;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return ItemStack.EMPTY;
        }
    }

    private static void setAwaitingStack(ItemStack stack) throws ReflectiveOperationException {
        Class.forName(CLIENT_CLASS).getField("awaitingStack").set(null, stack.copy());
    }

    private static void clearAwaitingStackIfSameItem(ItemStack stack) {
        ItemStack awaitingStack = getAwaitingStack();
        if (awaitingStack.isEmpty() || !awaitingStack.is(stack.getItem())) {
            return;
        }
        try {
            setAwaitingStack(ItemStack.EMPTY);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private static boolean isSingleShulker(ItemStack stack) {
        return !stack.isEmpty()
                && stack.getCount() == 1
                && stack.getItem() instanceof BlockItem blockItem
                && blockItem.getBlock() instanceof ShulkerBoxBlock;
    }

    private static int findStoredItemSlot(ItemStack shulker, Item item) {
        NonNullList<ItemStack> storedItems = fi.dy.masa.malilib.util.InventoryUtils.getStoredItems(shulker, -1);
        for (int slot = 0; slot < storedItems.size(); slot++) {
            ItemStack stack = storedItems.get(slot);
            if (!stack.isEmpty() && stack.is(item)) {
                return slot;
            }
        }
        return -1;
    }

    private static ItemStack singleStack(ItemStack stack) {
        ItemStack copy = stack.copy();
        copy.setCount(1);
        return copy;
    }
}
