package me.K1nse_.litematica.printer.config;

import fi.dy.masa.malilib.hotkeys.IKeybind;
import fi.dy.masa.malilib.hotkeys.KeyAction;
import me.K1nse_.litematica.printer.gui.ConfigUi;
import me.K1nse_.litematica.printer.render.Render2D;
import net.minecraft.client.Minecraft;


//监听按键
public class HotkeysCallback {
    private static final Minecraft client = Minecraft.getInstance();

    public static boolean onKeyAction(KeyAction action, IKeybind key) {
        if (client.player == null || client.level == null) {
            return false;
        }
        if (key == Configs.Hotkeys.OPEN_SCREEN.getKeybind()) {
            //#if MC > 260100
            //$$ client.gui.setScreen(new ConfigUi());
            //#else
            client.setScreen(new ConfigUi());
            //#endif
            return true;
        }
        if (key == Configs.Hotkeys.HUD_DRAG.getKeybind()) {
            if (action == KeyAction.PRESS) {
                Render2D.startHudDrag();
            } else if (action == KeyAction.RELEASE) {
                Render2D.stopHudDrag();
            }
            return true;
        }

        return false;
    }
}
