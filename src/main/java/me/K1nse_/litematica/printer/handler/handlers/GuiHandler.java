package me.K1nse_.litematica.printer.handler.handlers;

import me.K1nse_.litematica.printer.config.Configs;
import me.K1nse_.litematica.printer.handler.Module;

public class GuiHandler extends Module {
    public static final String NAME = "gui";

    public GuiHandler() {
        super(NAME, null, Configs.Core.RENDER_HUD, null, false);
    }
}
