package me.K1nse_.litematica.printer.mixin.printer.mc;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(targets = "net.minecraft.client.gui.screens.inventory.BeaconScreen$BeaconButton")
public interface BeaconButtonAccessor {
    @Invoker("updateStatus")
    void litematica_printer$updateStatus(int level);
}
