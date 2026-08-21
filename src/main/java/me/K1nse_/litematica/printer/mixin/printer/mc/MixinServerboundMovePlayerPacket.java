package me.K1nse_.litematica.printer.mixin.printer.mc;

import me.K1nse_.litematica.printer.printer.ActionManager;
import me.K1nse_.litematica.printer.printer.PlayerLook;
import me.K1nse_.litematica.printer.utils.minecraft.NetworkUtils;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(value = ServerboundMovePlayerPacket.class, priority = 1010)
public class MixinServerboundMovePlayerPacket {
    //#if MC > 12101
    @ModifyVariable(method = "<init>(DDDFFZZZZ)V", at = @At("HEAD"), ordinal = 0, argsOnly = true)
    //#else
    //$$ @ModifyVariable(method = "<init>(DDDFFZZZ)V", at = @At("HEAD"), ordinal = 0, argsOnly = true)
    //#endif
    private static float modifyLookYaw(float yaw) {
        if (NetworkUtils.shouldBypassQueuedLookOverride()) {
            return yaw;
        }
        PlayerLook scopedLook = NetworkUtils.getScopedLookOverride();
        if (scopedLook != null) {
            return scopedLook.yaw;
        }
        PlayerLook playerLook = ActionManager.INSTANCE.look;
        if (playerLook != null) {
            return playerLook.yaw;
        }
        return yaw;
    }

    //#if MC > 12101
    @ModifyVariable(method = "<init>(DDDFFZZZZ)V", at = @At("HEAD"), ordinal = 1, argsOnly = true)
    //#else
    //$$ @ModifyVariable(method = "<init>(DDDFFZZZ)V", at = @At("HEAD"), ordinal = 1, argsOnly = true)
    //#endif
    private static float modifyLookPitch(float pitch) {
        if (NetworkUtils.shouldBypassQueuedLookOverride()) {
            return pitch;
        }
        PlayerLook scopedLook = NetworkUtils.getScopedLookOverride();
        if (scopedLook != null) {
            return scopedLook.pitch;
        }
        PlayerLook playerLook = ActionManager.INSTANCE.look;
        if (playerLook != null) {
            return playerLook.pitch;
        }
        return pitch;
    }
}
