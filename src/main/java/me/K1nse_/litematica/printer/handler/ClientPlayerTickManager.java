package me.K1nse_.litematica.printer.handler;

import com.google.common.collect.ImmutableList;
import me.K1nse_.litematica.printer.handler.handlers.bedrock.BedrockController;
import me.K1nse_.litematica.printer.handler.scan.ScanCache;
import me.K1nse_.litematica.printer.handler.handlers.*;
import me.K1nse_.litematica.printer.printer.ActionManager;
import me.K1nse_.litematica.printer.printer.RttReplayController;
import me.K1nse_.litematica.printer.printer.zxy.inventory.SwitchItem;
import me.K1nse_.litematica.printer.utils.CooldownUtils;
import me.K1nse_.litematica.printer.utils.InteractionUtils;
import me.K1nse_.litematica.printer.utils.InventorySwitchGuard;
import me.K1nse_.litematica.printer.utils.minecraft.NetworkUtils;
import me.K1nse_.litematica.printer.utils.mods.TakeItOutUtils;
import net.minecraft.client.Minecraft;

@SuppressWarnings("SpellCheckingInspection")
public class ClientPlayerTickManager {
    public static final Minecraft mc = Minecraft.getInstance();

    public static final GuiHandler GUI = Modules.GUI;
    public static final PrintHandler PRINT = Modules.PRINT;
    public static final FillHandler FILL = Modules.FILL;
    public static final MineHandler MINE = Modules.MINE;
    public static final FluidHandler FLUID = Modules.FLUID;
    public static final BedrockHandler BEDROCK = Modules.BEDROCK;

    public static final ImmutableList<Module> VALUES = Modules.VALUES;
    private static final TickScheduler SCHEDULER = new TickScheduler(VALUES);

    public static void tick() {
        SCHEDULER.tick();
    }

    public static long getCurrentHandlerTime() {
        return TickContext.currentGameTime();
    }

    public static int getPacketTick() {
        return SCHEDULER.getPacketTick();
    }

    public static void setPacketTick(int packetTick) {
        SCHEDULER.setPacketTick(packetTick);
    }

    public static int getPacketEpoch() {
        return SCHEDULER.getPacketEpoch();
    }

    public static void recordInboundPacket() {
        SCHEDULER.recordInboundPacket();
    }

    public static void resetRuntime(String reason) {
        ActionManager.INSTANCE.clearQueue();
        NetworkUtils.clearScopedLookOverride();
        RttReplayController.INSTANCE.reset();
        ScanCache.INSTANCE.clear();
        CooldownUtils.INSTANCE.clearAllCooldowns();
        InteractionUtils.INSTANCE.resetRuntime();
        InventorySwitchGuard.reset();
        TakeItOutUtils.resetPending();
        SwitchItem.reSet();
        me.K1nse_.litematica.printer.printer.zxy.inventory.InventoryUtils.resetRuntime();
        BedrockController.reset();
        HudStatsManager.INSTANCE.resetAll();
        SCHEDULER.resetRuntime();
        for (Module module : VALUES) {
            module.resetRuntimeState();
        }
    }

    public static String getLastPauseReason() {
        return SCHEDULER.getLastPauseReason();
    }
}
