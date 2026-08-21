package me.K1nse_.litematica.printer.guide;

import me.K1nse_.litematica.printer.enums.BlockMatchResult;
import me.K1nse_.litematica.printer.printer.SchematicBlockContext;

public class SkipGuide extends Guide {

    public SkipGuide(SchematicBlockContext context) {
        super(context);
    }

    @Override
    protected Result onBuildAction(BlockMatchResult state) {
        return Result.SKIP;
    }
}