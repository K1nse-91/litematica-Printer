package me.K1nse_.litematica.printer.handler;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

final class GuiBlockInfoBuffer {
    private final List<GuiBlockInfo> entries = new ArrayList<>();
    private int renderIndex;
    private int cacheTicks;

    void tickCache() {
        if (this.cacheTicks > 0) {
            this.cacheTicks--;
            return;
        }
        this.clear();
    }

    void resetForTracking(boolean track) {
        if (track || !this.entries.isEmpty()) {
            this.clear();
        }
    }

    void add(@Nullable GuiBlockInfo info) {
        if (info == null) {
            return;
        }
        this.entries.add(info);
        this.cacheTicks = 20;
    }

    @Nullable
    GuiBlockInfo current() {
        if (this.entries.isEmpty()) {
            return null;
        }
        if (this.renderIndex >= this.entries.size()) {
            this.renderIndex = 0;
            return this.entries.get(this.entries.size() - 1);
        }
        return this.entries.get(this.renderIndex++);
    }

    @Nullable
    GuiBlockInfo latest() {
        if (this.entries.isEmpty()) {
            return null;
        }
        return this.entries.get(this.entries.size() - 1);
    }

    int size() {
        return this.entries.size();
    }

    int renderIndex() {
        return this.renderIndex;
    }

    private void clear() {
        this.entries.clear();
        this.renderIndex = 0;
    }
}
