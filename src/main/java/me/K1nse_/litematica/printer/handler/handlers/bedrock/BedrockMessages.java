package me.K1nse_.litematica.printer.handler.handlers.bedrock;

import me.K1nse_.litematica.printer.utils.minecraft.MessageUtils;
import me.K1nse_.litematica.printer.utils.minecraft.StringUtils;

public final class BedrockMessages {
    private BedrockMessages() {
    }

    public static void actionBar(String key) {
        MessageUtils.setOverlayMessage(StringUtils.translatable(key), false);
    }
}
