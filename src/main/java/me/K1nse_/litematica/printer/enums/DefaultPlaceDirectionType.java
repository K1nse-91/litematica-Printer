package me.K1nse_.litematica.printer.enums;

import me.K1nse_.litematica.printer.I18n;
import me.K1nse_.litematica.printer.config.ConfigOptionListEntry;
import net.minecraft.core.Direction;

/**
 * 默认放置朝向（移植自 EMT）：无朝向方块在多个可选面时，优先使用指定方向。
 */
public enum DefaultPlaceDirectionType implements ConfigOptionListEntry<DefaultPlaceDirectionType> {
    NONE("placeDefaultDirection.none", null),
    UP("placeDefaultDirection.up", Direction.UP),
    DOWN("placeDefaultDirection.down", Direction.DOWN),
    NORTH("placeDefaultDirection.north", Direction.NORTH),
    SOUTH("placeDefaultDirection.south", Direction.SOUTH),
    EAST("placeDefaultDirection.east", Direction.EAST),
    WEST("placeDefaultDirection.west", Direction.WEST);

    private final I18n i18n;
    private final Direction direction;

    DefaultPlaceDirectionType(String translateKey, Direction direction) {
        this.i18n = I18n.of(translateKey);
        this.direction = direction;
    }

    @Override
    public I18n getI18n() {
        return this.i18n;
    }

    public Direction toDirection() {
        return this.direction;
    }
}
