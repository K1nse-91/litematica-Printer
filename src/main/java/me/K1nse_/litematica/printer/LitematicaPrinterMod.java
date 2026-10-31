package me.K1nse_.litematica.printer;

import fi.dy.masa.malilib.event.InitializationHandler;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.ModInitializer;

public class LitematicaPrinterMod implements ModInitializer, ClientModInitializer {
    @Override
    public void onInitialize() {
    }

    @Override
    public void onInitializeClient() {
        InitializationHandler.getInstance().registerInitializationHandler(new InitHandler());
        //#if MC >= 260100
        // 自动寻路客户端命令 /go（移植自 EMT）
        me.K1nse_.litematica.printer.go.GoCommand.register();
        //#endif
    }
}
