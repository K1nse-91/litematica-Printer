package me.K1nse_.litematica.printer.handler.handlers.print;

import me.K1nse_.litematica.printer.printer.SchematicBlockContext;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class PrintTasks {
    private static final List<PrintTaskFactory> FACTORIES = List.of(
            WaterPrintTask::tryCreate
    );

    private PrintTasks() {
    }

    @Nullable
    public static PrintTask tryCreate(SchematicBlockContext context) {
        for (PrintTaskFactory factory : FACTORIES) {
            PrintTask task = factory.tryCreate(context);
            if (task != null) {
                return task;
            }
        }
        return null;
    }
}
