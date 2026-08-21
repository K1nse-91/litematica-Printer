package me.K1nse_.litematica.printer.printer;

import lombok.Setter;
import me.K1nse_.litematica.printer.Reference;
import me.K1nse_.litematica.printer.config.Configs;
import me.K1nse_.litematica.printer.mixin_extension.MultiPlayerGameModeExtension;
import me.K1nse_.litematica.printer.printer.zxy.inventory.SwitchItem;
import me.K1nse_.litematica.printer.utils.minecraft.DirectionUtils;
import me.K1nse_.litematica.printer.utils.InventoryUtils;
import me.K1nse_.litematica.printer.utils.minecraft.NetworkUtils;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

//#if MC > 12105
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.world.entity.player.Input;
//#else
//$$ import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
//#endif

@SuppressWarnings("SpellCheckingInspection")
public class ActionManager {
    public static final ActionManager INSTANCE = new ActionManager();
    private static final float LOOK_SETTLED_EPSILON_DEGREES = 1.0F;
    private static final double STALE_WAIT_MOVE_DISTANCE_SQR = 0.75D * 0.75D;

    private QueuedClick queuedClick;
    @Setter
    @Nullable
    public PlayerLook look;
    public boolean needWaitModifyLook = false;
    private boolean waitForHorizontalLook = true;
    private boolean actionRequiresWaitModifyLook = false;
    private long lastQueuedLookTick = Long.MIN_VALUE;
    private float lastQueuedLookYaw;
    private float lastQueuedLookPitch;

    private ActionManager() {
    }

    public void queueClick(@NotNull BlockPos target, @NotNull Direction side, @NotNull Vec3 hitModifier, boolean useShift) {
        this.queueClick(target, side, hitModifier, useShift, 1);
    }

    public void queueClick(@NotNull BlockPos target, @NotNull Direction side, @NotNull Vec3 hitModifier, boolean useShift, int clickRepeatCount) {
        this.queueClick(target, side, hitModifier, useShift, clickRepeatCount, null);
    }

    public void queueClick(@NotNull BlockPos target, @NotNull Direction side, @NotNull Vec3 hitModifier, boolean useShift, int clickRepeatCount, @Nullable Item[] expectedItems) {
        if (Configs.Placement.PLACE_INTERVAL.getIntegerValue() != 0) {
            if (this.queuedClick != null) {
                System.out.println("Was not ready yet.");
                return;
            }
        }
        this.queuedClick = new QueuedClick(target, side, hitModifier, useShift, clickRepeatCount);
        this.queuedClick.expectItems(expectedItems);
    }

    public void useProtocolHitModifier(@NotNull Vec3 hitModifier) {
        if (this.queuedClick != null) {
            this.queuedClick.useProtocolHit(hitModifier);
        }
    }

    public ActionManager sendQueue(@Nullable LocalPlayer player) {
        QueuedClick click = this.queuedClick;
        if (click == null || player == null) {
            clearQueue();
            return this;
        }
        if (shouldDropStaleQueuedClick(player, click)) {
            clearQueue();
            return this;
        }
        if (!needWaitModifyLook && look != null && shouldSendQueuedLook(look)) {
            NetworkUtils.sendLookPacket(player, look);
            this.recordQueuedLook(look);
        }
        if (shouldWaitForServerLook(player, click)) {
            needWaitModifyLook = true;
            return this;
        }
        if (needWaitModifyLook) {
            needWaitModifyLook = false;
        }
        if (!isHoldingExpectedItem(player, click)) {
            clearQueue();
            return this;
        }
        Direction direction;
        if (look == null) {
            direction = click.side;
        } else {
            direction = DirectionUtils.getHorizontalDirection(look.yaw);
        }
        Vec3 hitVec;
        if (!click.useProtocol) {
            Vec3 targetCenter = Vec3.atCenterOf(click.target);
            Vec3 sideOffset = Vec3.atLowerCornerOf(DirectionUtils.getVector(click.side)).scale(0.5);
            Vec3 rotatedHitModifier = click.hitModifier.yRot((direction.toYRot() + 90) % 360).scale(0.5);
            hitVec = targetCenter.add(sideOffset).add(rotatedHitModifier);
        } else {
            hitVec = click.hitModifier;
        }
        if (InventoryUtils.getOrderlyStoreItem() != null) {
            if (InventoryUtils.getOrderlyStoreItem().isEmpty()) {
                SwitchItem.removeItem(InventoryUtils.getOrderlyStoreItem());
            } else {
                SwitchItem.syncUseTime(InventoryUtils.getOrderlyStoreItem());
            }
        }
        boolean wasSneak = player.isShiftKeyDown();
        if (click.useShift && !wasSneak) {
            setShift(player, true);
        } else if (!click.useShift && wasSneak) {
            setShift(player, false);
        }
        MultiPlayerGameModeExtension gameModeExtension = (MultiPlayerGameModeExtension) Reference.MINECRAFT.gameMode;
        if (gameModeExtension != null) {
            BlockHitResult blockHitResult = new BlockHitResult(hitVec, click.side, click.target, false);
            for (int i = 0; i < click.repeatCount; i++) {
                gameModeExtension.litematica_printer$useItemOn(true, InteractionHand.MAIN_HAND, blockHitResult);
            }
        }
        if (click.useShift && !wasSneak) {
            setShift(player, false);
        } else if (!click.useShift && wasSneak) {
            setShift(player, true);
        }
        clearQueue();
        return this;
    }

    public void setShift(LocalPlayer player, boolean shift) {
        //#if MC > 12105
        Input input = new Input(player.input.keyPresses.forward(), player.input.keyPresses.backward(), player.input.keyPresses.left(), player.input.keyPresses.right(), player.input.keyPresses.jump(), shift, player.input.keyPresses.sprint());
        ServerboundPlayerInputPacket packet = new ServerboundPlayerInputPacket(input);
        //#else
        //$$ ServerboundPlayerCommandPacket packet = new ServerboundPlayerCommandPacket(player, shift ? ServerboundPlayerCommandPacket.Action.PRESS_SHIFT_KEY : ServerboundPlayerCommandPacket.Action.RELEASE_SHIFT_KEY);
        //#endif
        player.setShiftKeyDown(shift);
        NetworkUtils.sendPacket(packet);
    }

    public void setWaitForHorizontalLook(boolean waitForHorizontalLook) {
        this.waitForHorizontalLook = waitForHorizontalLook;
    }

    public void setNeedWaitModifyLookFromAction(boolean actionRequiresWaitModifyLook) {
        this.actionRequiresWaitModifyLook = actionRequiresWaitModifyLook;
    }

    private boolean shouldWaitForServerLook(LocalPlayer player, QueuedClick click) {
        if ((!this.waitForHorizontalLook && !this.actionRequiresWaitModifyLook)
                || click.useProtocol
                || this.needWaitModifyLook
                || this.look == null) {
            return false;
        }
        Direction lookDirection = DirectionUtils.orderedByNearest(this.look.yaw, this.look.pitch)[0];
        return lookDirection.getAxis().isHorizontal()
                && !isPlayerLookSettled(player, this.look);
    }

    private boolean shouldDropStaleQueuedClick(LocalPlayer player, QueuedClick click) {
        if (!this.needWaitModifyLook || click.queuedPlayerPosition == null) {
            return false;
        }
        long currentTick = Reference.MINECRAFT.level == null ? Long.MIN_VALUE : Reference.MINECRAFT.level.getGameTime();
        if (currentTick == Long.MIN_VALUE || currentTick <= click.queuedTick) {
            return false;
        }
        return player.position().distanceToSqr(click.queuedPlayerPosition) > STALE_WAIT_MOVE_DISTANCE_SQR;
    }

    private static boolean isHoldingExpectedItem(LocalPlayer player, QueuedClick click) {
        if (click.expectedItems == null || click.expectedItems.length == 0) {
            return true;
        }
        Item heldItem = player.getMainHandItem().getItem();
        for (Item expectedItem : click.expectedItems) {
            if (heldItem.equals(expectedItem)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isPlayerLookSettled(LocalPlayer player, PlayerLook look) {
        return Math.abs(Mth.wrapDegrees(player.getYRot() - look.yaw)) <= LOOK_SETTLED_EPSILON_DEGREES
                && Math.abs(player.getXRot() - look.pitch) <= LOOK_SETTLED_EPSILON_DEGREES;
    }

    private boolean shouldSendQueuedLook(PlayerLook look) {
        long tick = Reference.MINECRAFT.level == null ? Long.MIN_VALUE : Reference.MINECRAFT.level.getGameTime();
        if (tick == Long.MIN_VALUE || this.lastQueuedLookTick != tick) {
            return true;
        }
        return Math.abs(Mth.wrapDegrees(this.lastQueuedLookYaw - look.yaw)) > LOOK_SETTLED_EPSILON_DEGREES
                || Math.abs(this.lastQueuedLookPitch - look.pitch) > LOOK_SETTLED_EPSILON_DEGREES;
    }

    private void recordQueuedLook(PlayerLook look) {
        this.lastQueuedLookTick = Reference.MINECRAFT.level == null ? Long.MIN_VALUE : Reference.MINECRAFT.level.getGameTime();
        this.lastQueuedLookYaw = look.yaw;
        this.lastQueuedLookPitch = look.pitch;
    }

    public void clearQueue() {
        this.queuedClick = null;
        this.needWaitModifyLook = false;
        this.waitForHorizontalLook = true;
        this.actionRequiresWaitModifyLook = false;
        this.look = null;
    }
}
