package me.K1nse_.litematica.printer.enums;

import me.K1nse_.litematica.printer.I18n;
import me.K1nse_.litematica.printer.config.ConfigOptionListEntry;

public enum FillBlockModeType implements ConfigOptionListEntry<FillBlockModeType> {
    BLOCKLIST("fillBlockModeType.blocklist"),
    HANDHELD("fillBlockModeType.handheld");

    private final I18n i18n;

    FillBlockModeType(String translateKey) {
        this.i18n = I18n.of(translateKey);
    }

    @Override
    public I18n getI18n() {
        return i18n;
    }
}
