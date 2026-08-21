package me.K1nse_.litematica.printer.printer;

import lombok.Data;
import me.K1nse_.litematica.printer.utils.minecraft.DirectionUtils;
import net.minecraft.core.Direction;

@Data
public class PlayerLook {
    public final float yaw;
    public final float pitch;

    public PlayerLook(float yaw, float pitch) {
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public PlayerLook(Direction lookDirection) {
        this(DirectionUtils.getRequiredYaw(lookDirection), DirectionUtils.getRequiredPitch(lookDirection));
    }

    public PlayerLook(Direction lookDirectionYaw, Direction lookDirectionPitch) {
        this(DirectionUtils.getRequiredYaw(lookDirectionYaw), DirectionUtils.getRequiredPitch(lookDirectionPitch));
    }

    public PlayerLook(int rotation) {
        this(DirectionUtils.rotationToPlayerYaw(rotation), 0);
    }
}
