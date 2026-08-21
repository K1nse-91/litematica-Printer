package me.K1nse_.litematica.printer.handler;

import fi.dy.masa.malilib.config.options.ConfigBoolean;
import fi.dy.masa.malilib.config.options.ConfigOptionList;
import me.K1nse_.litematica.printer.enums.PrintModeType;
import org.jetbrains.annotations.Nullable;

/**
 * Compatibility layer for older handler classes.
 * New scheduler-facing modules should extend {@link Module} directly.
 */
@Deprecated
public abstract class ClientPlayerTickHandler extends Module {
    protected ClientPlayerTickHandler(String id, @Nullable PrintModeType printMode, @Nullable ConfigBoolean enableConfig, @Nullable ConfigOptionList selectionType, boolean useBox) {
        super(id, printMode, enableConfig, selectionType, useBox);
    }
}
