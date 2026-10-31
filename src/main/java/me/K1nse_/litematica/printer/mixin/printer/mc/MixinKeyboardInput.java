//#if MC >= 260100
package me.K1nse_.litematica.printer.mixin.printer.mc;

import me.K1nse_.litematica.printer.go.GoExecutor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public abstract class MixinKeyboardInput {
   @Inject(method = "tick", at = @At("TAIL"))
   private void printer$goOverrideInput(CallbackInfo ci) {
      GoExecutor.onApplyInput(Minecraft.getInstance().player);
   }
}

//#endif
