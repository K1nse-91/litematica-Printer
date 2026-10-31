package me.K1nse_.litematica.printer.mixin.printer.mc;

import me.K1nse_.litematica.printer.printer.ActionManager;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 手动交互保护（移植自 EMT）：手动右键铁砧时给自动操作一个短暂让行窗口，
 * 避免打印机/挖掘抢走这一下点击导致铁砧界面被跳过。
 */
@Mixin(Minecraft.class)
public abstract class MixinMinecraftManualUse {
    @Inject(method = "startUseItem", at = @At("HEAD"))
    private void litematica_printer$prioritizeManualAnvil(CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (client.level != null && client.hitResult instanceof BlockHitResult hit
                && client.level.getBlockState(hit.getBlockPos()).getBlock() instanceof AnvilBlock) {
            ActionManager.prioritizeManualAnvilScreen();
        }
    }
}
