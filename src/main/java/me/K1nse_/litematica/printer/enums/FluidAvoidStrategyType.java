package me.K1nse_.litematica.printer.enums;

import me.K1nse_.litematica.printer.I18n;
import me.K1nse_.litematica.printer.config.ConfigOptionListEntry;

public enum FluidAvoidStrategyType implements ConfigOptionListEntry<FluidAvoidStrategyType> {
    FIVE_FACES("breakFluidStrategy.fiveFaces"),
    SIX_FACES("breakFluidStrategy.sixFaces");

    private final I18n i18n;

    FluidAvoidStrategyType(String translateKey) {
        this.i18n = I18n.of(translateKey);
    }

    @Override
    public I18n getI18n() {
        return this.i18n;
    }
}
