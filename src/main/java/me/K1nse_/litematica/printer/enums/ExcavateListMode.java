package me.K1nse_.litematica.printer.enums;

import me.K1nse_.litematica.printer.I18n;
import me.K1nse_.litematica.printer.config.ConfigOptionListEntry;

public enum ExcavateListMode implements ConfigOptionListEntry<ExcavateListMode> {
    TWEAKEROO("excavateListMode.tweakeroo"),
    CUSTOM("excavateListMode.custom");

    private final I18n i18n;

    ExcavateListMode(String translateKey) {
        this.i18n = I18n.of(translateKey);
    }

    @Override
    public I18n getI18n() {
        return i18n;
    }
}
