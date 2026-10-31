package me.K1nse_.litematica.printer.mixin.printer.mc;

import me.K1nse_.litematica.printer.utils.HandRestockShulkerCompat;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 手持物品消耗检测（移植自 EMT）：使用物品完成时对比前后堆叠，触发潜影盒补货。
 */
@Mixin(value = LivingEntity.class, priority = 1020)
public abstract class MixinLivingEntity {
    @Unique
    private ItemStack litematica_printer$usingSnapshot = ItemStack.EMPTY;
    @Unique
    private InteractionHand litematica_printer$usingHand;

    @Inject(method = "startUsingItem", at = @At("HEAD"))
    private void litematica_printer$captureUsingStart(InteractionHand hand, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self instanceof LocalPlayer player) {
            this.litematica_printer$usingSnapshot = player.getItemInHand(hand).copy();
            this.litematica_printer$usingHand = hand;
        }
    }

    @Inject(method = "completeUsingItem", at = @At("TAIL"))
    private void litematica_printer$detectUsingConsumption(CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self instanceof LocalPlayer player && this.litematica_printer$usingHand != null) {
            InteractionHand hand = this.litematica_printer$usingHand;
            this.litematica_printer$usingHand = null;
            HandRestockShulkerCompat.onHandStackConsumed(player, hand,
                    this.litematica_printer$usingSnapshot, player.getItemInHand(hand));
        }
    }
}
