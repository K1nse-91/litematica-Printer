package me.K1nse_.litematica.printer.mixin.printer.mc;

import me.K1nse_.litematica.printer.config.Configs;
import me.K1nse_.litematica.printer.utils.minecraft.BeaconEffectSync;
import net.minecraft.client.gui.screens.inventory.BeaconScreen;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(BeaconScreen.class)
public abstract class BeaconScreenMixin {
    @Shadow
    @Final
    private List<?> beaconButtons;

    @Inject(method = "init", at = @At("HEAD"))
    private void litematica_printer$syncBeaconEffects(CallbackInfo ci) {
        BeaconEffectSync.syncFromConfig();
    }

    @Inject(method = "updateButtons", at = @At("TAIL"))
    private void litematica_printer$unlockBeaconEffects(CallbackInfo ci) {
        if (!Configs.Special.UNLOCK_BEACON_EFFECTS.getBooleanValue()) {
            return;
        }
        for (Object button : this.beaconButtons) {
            ((BeaconButtonAccessor) button).litematica_printer$updateStatus(4);
        }
    }
}
