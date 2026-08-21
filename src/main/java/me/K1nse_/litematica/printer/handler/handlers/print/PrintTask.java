package me.K1nse_.litematica.printer.handler.handlers.print;

import fi.dy.masa.litematica.world.WorldSchematic;
import me.K1nse_.litematica.printer.printer.SchematicBlockContext;
import me.K1nse_.litematica.printer.printer.action.Action;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

public interface PrintTask {
    BlockPos pos();

    default boolean owns(@Nullable BlockPos pos) {
        return pos != null && pos.equals(this.pos());
    }

    boolean shouldKeep(ClientLevel level, WorldSchematic schematic);

    PrintTaskBuildResult buildAction(SchematicBlockContext context);

    @Nullable
    PrintTaskAction createActionHandle(SchematicBlockContext context, Action action);
}
