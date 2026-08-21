package me.K1nse_.litematica.printer.handler.handlers.print;

import me.K1nse_.litematica.printer.printer.action.Action;
import org.jetbrains.annotations.Nullable;

public record PrintTaskBuildResult(@Nullable Action action, boolean handled, @Nullable PrintTaskAction actionHandle) {
    public static final PrintTaskBuildResult PASS = new PrintTaskBuildResult(null, false, null);
    public static final PrintTaskBuildResult SKIP = new PrintTaskBuildResult(null, true, null);

    public static PrintTaskBuildResult action(Action action, PrintTaskAction actionHandle) {
        return new PrintTaskBuildResult(action, true, actionHandle);
    }

    public boolean hasAction() {
        return this.action != null;
    }
}
