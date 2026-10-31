package me.K1nse_.litematica.printer.utils;

import me.K1nse_.litematica.printer.config.Configs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;

/**
 * 声音包确认追踪器（移植自 EMT）。
 * 纯发包放置/破坏时，本地不直接播放音效，而是登记一个待确认记录；
 * 服务端方块更新包命中（放置：方块类型一致；破坏：变为空气）后才播放音效。
 * 超时（40 tick）的记录会被清理，未确认的音效直接丢弃。
 */
public final class PacketSoundConfirmationTracker {
    private static final long TIMEOUT_TICKS = 40L;
    private static final Map<BlockPos, PendingSound> PENDING_SOUNDS = new HashMap<>();

    private PacketSoundConfirmationTracker() {
    }

    public static void trackPlacement(BlockPos pos, BlockState expectedState) {
        if (pos != null && expectedState != null && Configs.Print.PRINT_SOUND.getBooleanValue()) {
            track(pos, expectedState, SoundType.PLACEMENT);
        }
    }

    public static void trackBreak(BlockPos pos, BlockState brokenState) {
        if (pos != null && brokenState != null && Configs.Break.BREAK_SOUND.getBooleanValue()) {
            track(pos, brokenState, SoundType.BREAK);
        }
    }

    private static void track(BlockPos pos, BlockState state, SoundType type) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level != null) {
            prune(level.getGameTime());
            PENDING_SOUNDS.put(pos.immutable(), new PendingSound(state, type, level.getGameTime() + TIMEOUT_TICKS));
        }
    }

    public static void confirmServerBlockUpdate(BlockPos pos, BlockState updatedState) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || pos == null || updatedState == null) {
            return;
        }
        prune(level.getGameTime());
        PendingSound pending = PENDING_SOUNDS.get(pos);
        if (pending == null) {
            return;
        }
        boolean confirmed = pending.type == SoundType.PLACEMENT
                ? updatedState.getBlock() == pending.state.getBlock()
                : updatedState.isAir();
        if (confirmed) {
            PENDING_SOUNDS.remove(pos);
            //#if MC > 11802
            if (pending.type == SoundType.PLACEMENT) {
                level.playLocalSound(pos, pending.state.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 1.0F, 0.8F, false);
            } else {
                level.playLocalSound(pos, pending.state.getSoundType().getBreakSound(), SoundSource.BLOCKS, 1.0F, 0.8F, false);
            }
            //#else
            //$$ if (pending.type == SoundType.PLACEMENT) {
            //$$     level.playLocalSound(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D,
            //$$             pending.state.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 1.0F, 0.8F, false);
            //$$ } else {
            //$$     level.playLocalSound(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D,
            //$$             pending.state.getSoundType().getBreakSound(), SoundSource.BLOCKS, 1.0F, 0.8F, false);
            //$$ }
            //#endif
        }
    }

    private static void prune(long currentTick) {
        PENDING_SOUNDS.entrySet().removeIf(entry -> entry.getValue().expiresAtTick < currentTick);
    }

    private record PendingSound(BlockState state, SoundType type, long expiresAtTick) {
    }

    private enum SoundType {
        PLACEMENT,
        BREAK
    }
}
