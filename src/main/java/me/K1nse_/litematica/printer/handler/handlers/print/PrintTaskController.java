package me.K1nse_.litematica.printer.handler.handlers.print;

import fi.dy.masa.litematica.world.WorldSchematic;
import me.K1nse_.litematica.printer.printer.SchematicBlockContext;
import me.K1nse_.litematica.printer.printer.action.Action;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

public class PrintTaskController {
    @Nullable
    private PrintTask activeTask;

    public boolean hasActiveTask() {
        return this.activeTask != null;
    }

    public void clear() {
        this.activeTask = null;
    }

    @Nullable
    public BlockPos getActiveTargetPos(@Nullable ClientLevel level, @Nullable WorldSchematic schematic) {
        this.cleanup(level, schematic);
        return this.activeTask == null ? null : this.activeTask.pos();
    }

    public boolean isActiveTaskPos(@Nullable BlockPos pos) {
        return this.activeTask != null && this.activeTask.owns(pos);
    }

    public PrintTaskBuildResult buildAction(SchematicBlockContext context) {
        this.cleanup(context.level, context.schematic);
        if (this.activeTask != null) {
            if (!this.activeTask.owns(context.blockPos)) {
                return PrintTaskBuildResult.SKIP;
            }
            PrintTaskBuildResult result = this.activeTask.buildAction(context);
            this.cleanup(context.level, context.schematic);
            return result;
        }

        PrintTask task = PrintTasks.tryCreate(context);
        if (task == null) {
            return PrintTaskBuildResult.PASS;
        }

        this.activeTask = task;
        PrintTaskBuildResult result = task.buildAction(context);
        this.cleanup(context.level, context.schematic);
        return result;
    }

    @Nullable
    public PrintTaskAction createActionHandle(SchematicBlockContext context, Action action) {
        this.cleanup(context.level, context.schematic);
        if (this.activeTask == null || !this.activeTask.owns(context.blockPos)) {
            return null;
        }
        return this.activeTask.createActionHandle(context, action);
    }

    public void onActionSuccess(PrintTaskAction actionHandle, SchematicBlockContext context, Action action) {
        actionHandle.onSuccess(context, action);
        this.cleanup(context.level, context.schematic);
    }

    public void onActionQueued(PrintTaskAction actionHandle, SchematicBlockContext context, Action action) {
        actionHandle.onQueued(context, action);
        this.cleanup(context.level, context.schematic);
    }

    public void onActionFailure(PrintTaskAction actionHandle, SchematicBlockContext context, Action action) {
        actionHandle.onFailure(context, action);
        this.cleanup(context.level, context.schematic);
    }

    private void cleanup(@Nullable ClientLevel level, @Nullable WorldSchematic schematic) {
        if (this.activeTask == null) {
            return;
        }
        if (level == null || schematic == null || !this.activeTask.shouldKeep(level, schematic)) {
            this.activeTask = null;
        }
    }
}
