package me.K1nse_.litematica.printer.printer.zxy.utils;

import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.selection.AreaSelection;
import fi.dy.masa.litematica.selection.Box;
import me.K1nse_.litematica.printer.I18n;
import me.K1nse_.litematica.printer.config.Configs;
import me.K1nse_.litematica.printer.printer.PrinterBox;
import me.K1nse_.litematica.printer.printer.zxy.inventory.InventoryUtils;
import me.K1nse_.litematica.printer.printer.zxy.inventory.SwitchItem;
import me.K1nse_.litematica.printer.utils.FilterUtils;
import me.K1nse_.litematica.printer.utils.minecraft.MessageUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ServerboundContainerSlotStateChangedPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CrafterMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

//#if MC >= 12102
import net.minecraft.world.inventory.ContainerInput;
//#else
//$$ import net.minecraft.world.inventory.ClickType;
//#endif

/**
 * 背包同步（移植自 EMT）：对准容器按热键后，把 Litematica 选区内所有同名容器
 * 的格子逐一调整成与第一个容器完全一致（数量与物品），并高亮处理进度。
 */
public class ZxyUtils {
    private static final Minecraft client = Minecraft.getInstance();
    public static String syncInventoryId = "syncInventory";
    public static LinkedHashSet<BlockPos> syncPosList = new LinkedHashSet<>();
    public static ArrayList<ItemStack> targetBlockInv;
    public static boolean[] targetDisabledSlots;
    static boolean syncDeferred = false;
    public static int num = 0;
    static BlockPos blockPos = null;
    static final Map<BlockPos, Integer> syncFailCount = new HashMap<>();
    static int syncFailNumTime = 0;
    static int packetBudget = 0;
    static long packetBudgetTick = Long.MIN_VALUE;
    static Set<BlockPos> highlightPosList = new LinkedHashSet<>();
    static Map<ItemStack, Integer> targetItemsCount = new HashMap<>();
    static Map<ItemStack, Integer> playerItemsCount = new HashMap<>();
    /** 打开界面后延迟一次 tick 再读容器内容 */
    static int syncCloseScreen = 0;

    private ZxyUtils() {
    }

    private static void getReadyColor() {
        HighlightBlockRenderer.createHighlightBlockList(syncInventoryId, Configs.Special.SYNC_INVENTORY_COLOR);
        highlightPosList = HighlightBlockRenderer.getHighlightBlockPosList(syncInventoryId);
    }

    public static void startOrOffSyncInventory() {
        getReadyColor();
        if (client.hitResult != null && client.hitResult.getType() == HitResult.Type.BLOCK && syncPosList.isEmpty()) {
            BlockPos pos = ((BlockHitResult) client.hitResult).getBlockPos();
            if (client.level == null) {
                return;
            }
            BlockState blockState = client.level.getBlockState(pos);
            Block block = blockState.getBlock();
            BlockEntity blockEntity = client.level.getBlockEntity(pos);
            boolean isInventory = InventoryUtils.isInventory(client.level, pos);

            try {
                boolean shulkerClosed = blockEntity instanceof ShulkerBoxBlockEntity entity
                        && !client.level.noCollision(
                        Shulker.getProgressDeltaAabb(1.0F, blockState.getValue(ShulkerBoxBlock.FACING), 0.0F, 0.5F, Vec3.atBottomCenterOf(pos))
                                .move(pos)
                                .deflate(1.0E-6))
                        && entity.getAnimationStatus() == ShulkerBoxBlockEntity.AnimationStatus.CLOSED;
                if (!((!isInventory || blockState.getMenuProvider(client.level, pos) != null) && !shulkerClosed)) {
                    if (!isInventory) {
                        MessageUtils.setOverlayMessage(I18n.INVENTORY_SYNC_NOT_CONTAINER.getName());
                        return;
                    }
                    MessageUtils.setOverlayMessage(I18n.INVENTORY_SYNC_CONTAINER_CANNOT_OPEN.getName());
                }
            } catch (Exception e) {
                MessageUtils.setOverlayMessage(I18n.INVENTORY_SYNC_NOT_CONTAINER.getName());
                return;
            }

            String blockName = BuiltInRegistries.BLOCK.getKey(block).toString();
            syncPosList.addAll(filterBlocksByName(blockName));
            if (!syncPosList.isEmpty()) {
                if (client.player == null) {
                    return;
                }
                client.player.closeContainer();
                if (!openInv(pos, false)) {
                    syncPosList = new LinkedHashSet<>();
                    return;
                }
                highlightPosList.addAll(syncPosList);
                syncCloseScreen++;
                num = 1;
            }
        } else if (!syncPosList.isEmpty()) {
            syncPosList.forEach(highlightPosList::remove);
            syncPosList = new LinkedHashSet<>();
            if (client.player != null) {
                client.player.clientSideCloseContainer();
            }
            num = 0;
            MessageUtils.setOverlayMessage(I18n.INVENTORY_SYNC_CANCELLED.getName());
        }
    }

    public static boolean openInv(BlockPos pos, boolean ignoreThePrompt) {
        if (client.player != null && !me.K1nse_.litematica.printer.utils.ConfigUtils.canInteracted(pos)) {
            if (!ignoreThePrompt) {
                MessageUtils.setOverlayMessage(I18n.INVENTORY_SYNC_TOO_FAR.getName());
            }
            return false;
        } else if (client.gameMode != null && client.player != null) {
            client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(pos), Direction.DOWN, pos, false));
            return true;
        } else {
            return false;
        }
    }

    private static void itemsCount(Map<ItemStack, Integer> itemsCount, ItemStack itemStack) {
        Optional<Map.Entry<ItemStack, Integer>> entry = itemsCount.entrySet()
                .stream()
                .filter(e -> ItemStack.isSameItemSameComponents(e.getKey(), itemStack))
                .findFirst();
        if (entry.isPresent()) {
            Map.Entry<ItemStack, Integer> e = entry.get();
            itemsCount.put(e.getKey(), e.getValue() + itemStack.getCount());
        } else {
            itemsCount.put(itemStack, itemStack.getCount());
        }
    }

    public static void syncInv() {
        ensurePacketBudget();
        switch (num) {
            case 1:
                if (!syncDeferred) {
                    syncDeferred = true;
                    return;
                }
                syncDeferred = false;
                targetBlockInv = new ArrayList<>();
                targetItemsCount = new HashMap<>();
                targetDisabledSlots = null;
                if (client.player != null && !client.player.containerMenu.equals(client.player.inventoryMenu)) {
                    //#if MC >= 12004
                    if (client.player.containerMenu instanceof CrafterMenu crafterMenu && Configs.Special.SYNC_INVENTORY_CRAFTER.getBooleanValue()) {
                        boolean[] disabled = new boolean[9];
                        for (int i = 0; i < disabled.length; i++) {
                            disabled[i] = crafterMenu.isSlotDisabled(i);
                        }
                        targetDisabledSlots = disabled;
                    }
                    //#endif
                    for (int i = 0; i < client.player.containerMenu.slots.get(0).container.getContainerSize(); i++) {
                        ItemStack copy = client.player.containerMenu.slots.get(i).getItem().copy();
                        itemsCount(targetItemsCount, copy);
                        targetBlockInv.add(copy);
                    }
                    client.player.closeContainer();
                    num = 2;
                } else {
                    syncPosList.forEach(highlightPosList::remove);
                    syncPosList = new LinkedHashSet<>();
                    num = 0;
                }
                break;
            case 2:
                if (client.player == null) {
                    return;
                }
                playerItemsCount = new HashMap<>();
                MessageUtils.setOverlayMessage(I18n.INVENTORY_SYNC_REMAINING.getName(syncPosList.size()));
                if (!client.player.containerMenu.equals(client.player.inventoryMenu)) {
                    return;
                }
                NonNullList<Slot> slots = client.player.inventoryMenu.slots;
                slots.forEach(slot -> itemsCount(playerItemsCount, slot.getItem()));
                if (Configs.Special.SYNC_INVENTORY_CHECK.getBooleanValue()
                        && !targetItemsCount.entrySet()
                        .stream()
                        .allMatch(target -> playerItemsCount.entrySet()
                                .stream()
                                .anyMatch(player -> ItemStack.isSameItemSameComponents(player.getKey(), target.getKey()) && target.getValue() <= player.getValue()))) {
                    return;
                }
                List<BlockPos> retryPositions = new ArrayList<>();
                Iterator<BlockPos> iterator = syncPosList.iterator();
                while (iterator.hasNext()) {
                    BlockPos pos = iterator.next();
                    if (openInv(pos, true)) {
                        syncFailCount.remove(pos);
                        syncCloseScreen++;
                        blockPos = pos;
                        num = 3;
                        break;
                    }
                    iterator.remove();
                    if (client.player != null && !me.K1nse_.litematica.printer.utils.ConfigUtils.canInteracted(pos)) {
                        retryPositions.add(pos);
                    } else {
                        int failCount = syncFailCount.getOrDefault(pos, 0) + 1;
                        if (failCount >= 5) {
                            syncFailCount.remove(pos);
                            highlightPosList.remove(pos);
                            MessageUtils.setOverlayMessage(I18n.INVENTORY_SYNC_CONTAINER_CANNOT_OPEN.getName());
                        } else {
                            syncFailCount.put(pos, failCount);
                            retryPositions.add(pos);
                        }
                    }
                }
                syncPosList.addAll(retryPositions);
                if (syncPosList.isEmpty()) {
                    num = 0;
                    MessageUtils.setOverlayMessage(I18n.INVENTORY_SYNC_COMPLETE.getName());
                }
                break;
            case 3:
                if (!syncDeferred) {
                    syncDeferred = true;
                    return;
                }
                syncDeferred = false;
                AbstractContainerMenu sc = client.player.containerMenu;
                if (sc.equals(client.player.inventoryMenu)) {
                    return;
                }
                int size = Math.min(targetBlockInv.size(), sc.slots.get(0).container.getContainerSize());
                //#if MC >= 12004
                CrafterMenu crafterMenu = sc instanceof CrafterMenu m && targetDisabledSlots != null ? m : null;
                if (crafterMenu != null && packetBudget <= 0) {
                    deferRestOfPass();
                    return;
                }
                if (crafterMenu != null) {
                    for (int i = 0; i < 9; i++) {
                        boolean wantDisabled = targetDisabledSlots[i];
                        if (!wantDisabled && crafterMenu.isSlotDisabled(i)) {
                            setCrafterSlotState(crafterMenu, i, true);
                        } else if (wantDisabled && !crafterMenu.isSlotDisabled(i) && !sc.slots.get(i).getItem().isEmpty()) {
                            syncClick(sc.containerId, i, 1, CLICK_THROW);
                        }
                    }
                    for (int i = 0; i < 9; i++) {
                        if (targetDisabledSlots[i] && !crafterMenu.isSlotDisabled(i)) {
                            setCrafterSlotState(crafterMenu, i, false);
                        }
                    }
                }
                //#endif
                int times = 0;
                for (int i = 0; i < size; i++) {
                    if (packetBudget <= 0) {
                        deferRestOfPass();
                        return;
                    }
                    ItemStack item1 = sc.slots.get(i).getItem();
                    ItemStack item2 = targetBlockInv.get(i).copy();
                    int currNum = item1.getCount();
                    int tarNum = item2.getCount();
                    boolean same = ItemStack.isSameItemSameComponents(item1, item2) && !item1.isEmpty();
                    if (!ItemStack.isSameItemSameComponents(item1, item2) || currNum != tarNum) {
                        if (same) {
                            while (currNum > tarNum) {
                                if (packetBudget <= 0) {
                                    deferRestOfPass();
                                    return;
                                }
                                syncClick(sc.containerId, i, 0, CLICK_THROW);
                                currNum--;
                            }
                        } else {
                            if (packetBudget <= 0) {
                                deferRestOfPass();
                                return;
                            }
                            syncClick(sc.containerId, i, 1, CLICK_THROW);
                            times++;
                        }
                        boolean thereAreItems = false;
                        for (int i1 = size; i1 < sc.slots.size(); i1++) {
                            ItemStack stack = sc.slots.get(i1).getItem();
                            ItemStack currStack = sc.slots.get(i).getItem();
                            currNum = currStack.getCount();
                            if (ItemStack.isSameItemSameComponents(item2, stack) && !stack.isEmpty()) {
                                thereAreItems = true;
                                if (packetBudget <= 0) {
                                    deferRestOfPass();
                                    return;
                                }
                                int i2 = stack.getCount();
                                syncClick(sc.containerId, i1, 0, CLICK_PICKUP);
                                while (currNum < tarNum && i2 > 0) {
                                    syncClick(sc.containerId, i, 1, CLICK_PICKUP);
                                    currNum++;
                                    i2--;
                                }
                                syncClick(sc.containerId, i1, 0, CLICK_PICKUP);
                            }
                            if (currNum != tarNum) {
                                times++;
                            }
                        }
                        if (!thereAreItems) {
                            times++;
                        }
                    }
                }
                if (blockPos != null) {
                    if (times == 0) {
                        syncPosList.remove(blockPos);
                        highlightPosList.remove(blockPos);
                        syncFailCount.remove(blockPos);
                    } else {
                        int failCount = syncFailCount.getOrDefault(blockPos, 0) + 1;
                        if (failCount >= 5) {
                            syncFailCount.remove(blockPos);
                            syncPosList.remove(blockPos);
                            highlightPosList.remove(blockPos);
                            MessageUtils.setOverlayMessage(I18n.INVENTORY_SYNC_CONTAINER_CANNOT_OPEN.getName());
                        } else {
                            syncFailCount.put(blockPos, failCount);
                            syncPosList.remove(blockPos);
                            syncPosList.add(blockPos);
                        }
                    }
                    blockPos = null;
                }
                syncFailNumTime = 0;
                client.player.closeContainer();
                num = 2;
        }
    }

    //#if MC >= 12004
    private static void setCrafterSlotState(CrafterMenu menu, int slot, boolean enabled) {
        if (client.player != null) {
            menu.setSlotState(slot, enabled);
            client.player.connection.send(new ServerboundContainerSlotStateChangedPacket(slot, menu.containerId, enabled));
        }
    }
    //#endif

    private static void ensurePacketBudget() {
        long tick = client.level == null ? 0L : client.level.getGameTime();
        if (tick != packetBudgetTick) {
            packetBudgetTick = tick;
            packetBudget = Math.max(1, Configs.Special.SYNC_PACKET_LIMIT.getIntegerValue());
        }
    }

    //#if MC >= 12102
    private static final ContainerInput CLICK_THROW = ContainerInput.THROW;
    private static final ContainerInput CLICK_PICKUP = ContainerInput.PICKUP;

    private static void syncClick(int containerId, int slot, int button, ContainerInput type) {
        client.gameMode.handleContainerInput(containerId, slot, button, type, client.player);
        packetBudget--;
    }
    //#else
    //$$ private static final ClickType CLICK_THROW = ClickType.THROW;
    //$$ private static final ClickType CLICK_PICKUP = ClickType.PICKUP;
    //$$
    //$$ private static void syncClick(int containerId, int slot, int button, ClickType type) {
    //$$     client.gameMode.handleInventoryMouseClick(containerId, slot, button, type);
    //$$     packetBudget--;
    //$$ }
    //#endif

    private static void deferRestOfPass() {
        syncDeferred = true;
        syncFailNumTime = 0;
    }

    public static void tick() {
        if (syncDeferred) {
            if (num != 1 && num != 3) {
                syncDeferred = false;
            } else {
                syncInv();
            }
        }
        if (num == 2) {
            syncInv();
        }
        if (num == 3) {
            syncFailNumTime++;
            if (syncFailNumTime >= 40) {
                syncFailNumTime = 0;
                if (client.player != null) {
                    client.player.closeContainer();
                }
                if (blockPos != null) {
                    int failCount = syncFailCount.getOrDefault(blockPos, 0) + 1;
                    if (failCount >= 5) {
                        syncFailCount.remove(blockPos);
                        syncPosList.remove(blockPos);
                        highlightPosList.remove(blockPos);
                        MessageUtils.setOverlayMessage(I18n.INVENTORY_SYNC_CONTAINER_CANNOT_OPEN.getName());
                    } else {
                        syncFailCount.put(blockPos, failCount);
                        syncPosList.remove(blockPos);
                        syncPosList.add(blockPos);
                    }
                    blockPos = null;
                }
                num = 2;
            }
        }
        if (num == 0 && !InventoryUtils.isOpenHandler && syncCloseScreen > 0) {
            syncCloseScreen--;
        }
    }

    public static void switchPlayerInvToHotbarAir(int slot) {
        if (client.player == null) {
            return;
        }
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

    public static Collection<BlockPos> filterBlocksByName(String blockName) {
        Set<BlockPos> blocks = new LinkedHashSet<>();
        if (client.level == null || client.player == null) {
            return blocks;
        }
        AreaSelection selection = DataManager.getSelectionManager().getCurrentSelection();
        if (selection == null) {
            return blocks;
        }
        int renderDistance = Configs.Special.SYNC_HIGHLIGHT_RENDER_DISTANCE.getIntegerValue();
        double renderDistanceSq = (double) renderDistance * renderDistance;

        for (Box box : selection.getAllSubRegionBoxes()) {
            if (box.getPos1() == null || box.getPos2() == null) {
                continue;
            }
            PrinterBox printerBox = new PrinterBox(box.getPos1(), box.getPos2());
            for (int cx = printerBox.minX >> 4; cx <= printerBox.maxX >> 4; cx++) {
                for (int cz = printerBox.minZ >> 4; cz <= printerBox.maxZ >> 4; cz++) {
                    LevelChunk chunk = (LevelChunk) client.level.getChunk(cx, cz, ChunkStatus.FULL, false);
                    if (chunk == null) {
                        continue;
                    }
                    for (BlockPos pos : chunk.getBlockEntities().keySet()) {
                        if (!printerBox.contains(pos)) {
                            continue;
                        }
                        BlockState state = chunk.getBlockState(pos);
                        if (!FilterUtils.matchName(blockName, state)
                                || (state.getBlock() instanceof ChestBlock && state.getValue(ChestBlock.TYPE) != ChestType.RIGHT)) {
                            continue;
                        }
                        if (renderDistance > 0 && pos.distToLowCornerSqr(client.player.getX(), client.player.getY(), client.player.getZ()) > renderDistanceSq) {
                            continue;
                        }
                        blocks.add(pos.immutable());
                    }
                }
            }
        }
        return blocks;
    }

    public static void exitGameReSet() {
        SwitchItem.reSet();
    }
}
