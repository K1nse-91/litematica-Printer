package me.K1nse_.litematica.printer.enums;

import me.K1nse_.litematica.printer.I18n;
import me.K1nse_.litematica.printer.config.ConfigOptionListEntry;

public enum QuickShulkerModeType implements ConfigOptionListEntry<QuickShulkerModeType> {
    CLICK_SLOT("quickShulkerMode.click_slot"),
    INVOKE("quickShulkerMode.invoke"),
    AXSHULKERS("quickShulkerMode.axshulkers");

    private final I18n i18n;

    QuickShulkerModeType(String translateKey) {
        this.i18n = I18n.of(translateKey);
    }

    @Override
    public I18n getI18n() {
        return i18n;
    }
}