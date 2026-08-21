package me.K1nse_.litematica.printer.mixin;

import me.K1nse_.litematica.printer.printer.zxy.inventory.InventoryUtils;
import me.K1nse_.litematica.printer.printer.zxy.inventory.SwitchItem;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static me.K1nse_.litematica.printer.printer.zxy.inventory.InventoryUtils.isOpenHandler;
import static me.K1nse_.litematica.printer.printer.zxy.inventory.SwitchItem.reSwitchItem;

@Mixin(ClientPacketListener.class)
public abstract class MixinClientPacketListener {

    @Inject(at = @At("TAIL"), method = "handleContainerContent")
    public void onInventory(ClientboundContainerSetContentPacket packet, CallbackInfo ci) {
        if (isOpenHandler) {
            InventoryUtils.switchInv();
        }
        if (reSwitchItem != null) {
            SwitchItem.reSwitchItem();
        }
    }
}
