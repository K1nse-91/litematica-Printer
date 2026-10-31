package me.K1nse_.litematica.printer.mixin.printer.mc;

import me.K1nse_.litematica.printer.printer.zxy.inventory.InventoryUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 快速潜影盒守卫（移植自 EMT）：程序自动打开潜影盒时抑制容器界面弹出，
 * 避免界面闪现抢焦点、误触手动操作。
 */
@Mixin(Minecraft.class)
public abstract class MixinContainerScreenGuard {
    @Inject(method = "setScreenAndShow", at = @At("HEAD"), cancellable = true)
    private void litematica_printer$suppressAutomatedShulkerScreen(@Nullable Screen screen, CallbackInfo ci) {
        if (screen instanceof AbstractContainerScreen && InventoryUtils.shouldSuppressContainerScreen()) {
            ci.cancel();
        }
    }
}
