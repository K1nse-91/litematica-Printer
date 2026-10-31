//#if MC >= 260100
package me.K1nse_.litematica.printer.mixin.printer.mc;

import me.K1nse_.litematica.printer.go.GoExecutor;
import me.K1nse_.litematica.printer.go.GoManager;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LocalPlayer.class)
public abstract class MixinLocalPlayerGo {
   @Inject(method = "aiStep", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/ClientInput;tick()V", shift = Shift.AFTER))
   private void printer$goOverwriteInputBeforeSprint(CallbackInfo ci) {
      GoExecutor.onApplyInput((LocalPlayer)(Object)this);
   }

   @Inject(method = "applyInput", at = @At("HEAD"))
   private void printer$goOverwriteInput(CallbackInfo ci) {
      GoExecutor.onApplyInput((LocalPlayer)(Object)this);
   }

   @Inject(method = "isControlledCamera", at = @At("HEAD"), cancellable = true)
   private void printer$goForceControlledCamera(CallbackInfoReturnable<Boolean> cir) {
      if (GoManager.INSTANCE.isActive()) {
         cir.setReturnValue(true);
      }
   }
}

//#endif
