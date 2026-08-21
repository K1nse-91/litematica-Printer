package me.K1nse_.litematica.printer.handler.handlers.bedrock;

import me.K1nse_.litematica.printer.printer.ActionManager;
import me.K1nse_.litematica.printer.printer.PlayerLook;
import me.K1nse_.litematica.printer.handler.ClientPlayerTickManager;
import me.K1nse_.litematica.printer.utils.InteractionUtils;
import me.K1nse_.litematica.printer.utils.minecraft.DirectionUtils;
import me.K1nse_.litematica.printer.utils.minecraft.NetworkUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

public final class BedrockPlacer {
    private static final Minecraft CLIENT = Minecraft.getInstance();
    private static final Map<BlockPos, PendingHorizontalPlacement> pendingHorizontalPistonPlacements = new HashMap<>();

    private BedrockPlacer() {
    }

    public static void clearHorizontalLookState() {
        pendingHorizontalPistonPlacements.clear();
        NetworkUtils.clearScopedLookOverride();
    }

    public static boolean hasPendingHorizontalLook(BlockPos pistonPos) {
        return pistonPos != null && pendingHorizontalPistonPlacements.containsKey(pistonPos.immutable());
    }

    public static boolean placeSimple(BlockPos supportPos, Direction clickedFace, Item item) {
        LocalPlayer player = CLIENT.player;
        if (player == null || CLIENT.gameMode == null) {
            return false;
        }
        if (!BedrockInventory.switchToOffhand(item)) {
            return false;
        }
        PlayerLook look = new PlayerLook(clickedFace.getOpposite());
        NetworkUtils.sendLookPacketIgnoringQueuedLook(player, look);
        // Use center of the support block for more reliable interaction
        BlockHitResult hitResult = new BlockHitResult(Vec3.atCenterOf(supportPos), clickedFace, supportPos, false);
        placeBlockAggressively(player, hitResult, true);
        return true;
    }

    public static boolean placePiston(BlockPos pistonPos, Direction facing) {
        return placePiston(pistonPos, facing, pistonPos.relative(facing.getOpposite()));
    }

    public static boolean preparePistonPlacementLook(BlockPos pistonPos, Direction facing) {
        LocalPlayer player = CLIENT.player;
        if (player == null || CLIENT.gameMode == null) {
            return false;
        }

        PlayerLook look = new PlayerLook(facing.getOpposite());
        return !ensureHorizontalLookSettled(player, pistonPos, facing, look, false);
    }

    public static boolean placePiston(BlockPos pistonPos, Direction facing, BlockPos... preferredAnchors) {
        LocalPlayer player = CLIENT.player;
        if (player == null || CLIENT.gameMode == null) {
            NetworkUtils.clearScopedLookOverride();
            return false;
        }
        if (!BedrockInventory.switchToOffhand(Blocks.PISTON.asItem())) {
            NetworkUtils.clearScopedLookOverride();
            return false;
        }

        // Pistons face opposite to the direction the player is looking when placed.
        // We want the resulting piston facing to match `facing`, so look at the opposite side.
        PlayerLook look = new PlayerLook(facing.getOpposite());
        if (ensureHorizontalLookSettled(player, pistonPos, facing, look, true)) {
            return false;
        }
        applyPlacementLook(player, look);

        BlockPos clickedPos = pistonPos.relative(facing.getOpposite());
        Direction clickedFace = facing;
        if (CLIENT.level != null) {
            BlockPos[] anchors = preferredAnchors != null && preferredAnchors.length > 0
                    ? preferredAnchors
                    : new BlockPos[]{clickedPos};
            BedrockEnvironment.PlacementInteraction placementInteraction =
                    BedrockEnvironment.findPlacementInteraction(CLIENT.level, pistonPos, anchors);
            if (placementInteraction != null) {
                clickedPos = placementInteraction.anchorPos();
                clickedFace = placementInteraction.clickedFace();
            }
        }

        BlockHitResult hitResult = new BlockHitResult(Vec3.atCenterOf(clickedPos), clickedFace, clickedPos, false);

        placeBlockAggressively(player, hitResult, false);
        NetworkUtils.clearScopedLookOverride();
        return true;
    }

    private static void placeBlockAggressively(LocalPlayer player, BlockHitResult hitResult, boolean allowLocalUseFallback) {
        boolean useShift = CLIENT.level != null && BedrockTargetBlocks.requiresSneakPlacement(CLIENT.level.getBlockState(hitResult.getBlockPos()));
        boolean wasSneak = player.isShiftKeyDown();
        if (useShift && !wasSneak) {
            ActionManager.INSTANCE.setShift(player, true);
        }
        try {
            InteractionUtils.INSTANCE.useItemOn(false, InteractionHand.OFF_HAND, hitResult);
            if (allowLocalUseFallback) {
                ItemStack offhand = player.getOffhandItem();
                if (!offhand.isEmpty()) {
                    offhand.useOn(new UseOnContext(player, InteractionHand.OFF_HAND, hitResult));
                }
            }
        } finally {
            if (useShift && !wasSneak) {
                ActionManager.INSTANCE.setShift(player, false);
            }
        }
    }

    private static boolean ensureHorizontalLookSettled(LocalPlayer player, BlockPos pistonPos, Direction facing, PlayerLook look, boolean consumeReadyPlacement) {
        Direction lookDirection = DirectionUtils.orderedByNearest(look.getYaw(), look.getPitch())[0];
        BlockPos pendingKey = pistonPos.immutable();
        if (!lookDirection.getAxis().isHorizontal()) {
            pendingHorizontalPistonPlacements.remove(pendingKey);
            NetworkUtils.clearScopedLookOverride();
            return false;
        }

        PendingHorizontalPlacement pendingPlacement = pendingHorizontalPistonPlacements.get(pendingKey);
        if (pendingPlacement != null && facing == pendingPlacement.facing()) {
            NetworkUtils.setScopedLookOverride(look);
            if (!isHorizontalLookReady(pendingPlacement)) {
                return true;
            }
            if (consumeReadyPlacement) {
                pendingHorizontalPistonPlacements.remove(pendingKey);
            }
            return false;
        }

        long sentTick = ClientPlayerTickManager.getCurrentHandlerTime();
        int packetEpoch = ClientPlayerTickManager.getPacketEpoch();
        pendingHorizontalPistonPlacements.put(pendingKey, new PendingHorizontalPlacement(facing, sentTick, packetEpoch));
        NetworkUtils.setScopedLookOverride(look);
        NetworkUtils.sendLookPacketIgnoringQueuedLook(player, look);
        return true;
    }

    private static boolean isHorizontalLookReady(PendingHorizontalPlacement pendingPlacement) {
        long now = ClientPlayerTickManager.getCurrentHandlerTime();
        if (now <= pendingPlacement.sentTick()) {
            return false;
        }
        if (ClientPlayerTickManager.getPacketEpoch() > pendingPlacement.packetEpoch()) {
            return true;
        }
        return now - pendingPlacement.sentTick() >= 2L;
    }

    private static void applyPlacementLook(LocalPlayer player, PlayerLook look) {
        NetworkUtils.sendLookPacketIgnoringQueuedLook(player, look);
    }

    private record PendingHorizontalPlacement(Direction facing, long sentTick, int packetEpoch) {
    }
}
