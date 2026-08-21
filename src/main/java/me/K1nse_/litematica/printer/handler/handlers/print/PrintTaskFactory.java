package me.K1nse_.litematica.printer.handler.handlers.print;

import me.K1nse_.litematica.printer.printer.SchematicBlockContext;
import org.jetbrains.annotations.Nullable;

@FunctionalInterface
public interface PrintTaskFactory {
    @Nullable
    PrintTask tryCreate(SchematicBlockContext context);
}
