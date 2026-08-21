package me.K1nse_.litematica.printer.printer;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class QueuedClick {
    final BlockPos target;
    final Direction side;
    Vec3 hitModifier;
    final boolean useShift;
    boolean useProtocol;
    final int repeatCount;
    @Nullable
    final Vec3 queuedPlayerPosition;
    final long queuedTick;
    @Nullable
    Item[] expectedItems;

    QueuedClick(@NotNull BlockPos target, @NotNull Direction side, @NotNull Vec3 hitModifier, boolean useShift, int repeatCount) {
        this.target = target;
        this.side = side;
        this.hitModifier = hitModifier;
        this.useShift = useShift;
        this.repeatCount = Math.max(1, repeatCount);
        Minecraft client = Minecraft.getInstance();
        this.queuedPlayerPosition = client.player == null ? null : client.player.position();
        this.queuedTick = client.level == null ? Long.MIN_VALUE : client.level.getGameTime();
    }

    void useProtocolHit(Vec3 hitModifier) {
        this.hitModifier = hitModifier;
        this.useProtocol = true;
    }

    void expectItems(@Nullable Item[] expectedItems) {
        this.expectedItems = expectedItems == null ? null : expectedItems.clone();
    }
}
