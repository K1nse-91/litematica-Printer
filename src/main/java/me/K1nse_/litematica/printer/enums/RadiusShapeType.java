package me.K1nse_.litematica.printer.enums;

import me.K1nse_.litematica.printer.I18n;
import me.K1nse_.litematica.printer.config.ConfigOptionListEntry;

public enum RadiusShapeType implements ConfigOptionListEntry<RadiusShapeType> {
    SPHERE("iteratorShapeType.sphere"),
    OCTAHEDRON("iteratorShapeType.octahedron"),
    CUBE("iteratorShapeType.cube");

    private final I18n i18n;

    RadiusShapeType(String translateKey) {
        this.i18n = I18n.of(translateKey);
    }

    @Override
    public I18n getI18n() {
        return i18n;
    }
}
