package me.K1nse_.litematica.printer.gui;

import fi.dy.masa.malilib.gui.button.ButtonBase;
import fi.dy.masa.malilib.render.GuiContext;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.List;

/**
 * 方块单元格（液态玻璃）：圆角悬停/选中、数量胶囊、中文名。
 */
public class BlockIconButton extends ButtonBase {
    private static final int SELECTED_FILL = 0x330A84FF;
    private static final int SELECTED_EDGE = 0xCC64D2FF;
    private static final int HOVER_FILL = 0x1FFFFFFF;
    private static final int HOVER_EDGE = 0x2EFFFFFF;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int COUNT = 0xCCFFFFFF;
    private static final int COUNT_NONE = 0x59FFFFFF;
    private static final int RADIUS = 6;

    private final Block block;
    private final String displayName;
    private final int availableCount;
    private boolean selected;

    public BlockIconButton(Block block, int availableCount, int x, int y, int width, int height, boolean selected) {
        super(x, y, width, height);
        this.block = block;
        this.availableCount = availableCount;
        this.selected = selected;
        this.displayName = block.getName().getString();
        List<String> hover = new ArrayList<>();
        hover.add(this.displayName);
        hover.add("可取 " + availableCount);
        hover.add(BuiltInRegistries.BLOCK.getKey(block).toString());
        this.setHoverStrings(hover);
    }

    public Block getBlock() {
        return this.block;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    @Override
    public void render(GuiContext ctx, int mouseX, int mouseY, boolean hovered) {
        int w = this.width - 2;
        int h = this.height - 2;
        int x = this.x + 1;
        int y = this.y;

        if (this.selected) {
            Glass.rect(ctx, x - 1, y - 1, w + 2, h + 2, RADIUS + 1, 0x2664D2FF);
            Glass.rect(ctx, x, y, w, h, RADIUS, SELECTED_FILL);
            Glass.rect(ctx, x + 1, y + 1, w - 2, (h - 2) / 2, Math.max(0, RADIUS - 1), 0x14FFFFFF);
            Glass.ring(ctx, x, y, w, h, RADIUS, SELECTED_EDGE);
        } else if (hovered) {
            Glass.rect(ctx, x, y, w, h, RADIUS, HOVER_FILL);
            Glass.ring(ctx, x, y, w, h, RADIUS, HOVER_EDGE);
        }

        ItemStack stack = this.block.asItem().getDefaultInstance();
        int iconX = this.x + (this.width - 16) / 2;
        int iconY = this.y + 2;
        ctx.renderItem(stack, iconX, iconY);


        // 中文名
        String text = this.displayName;
        int maxWidth = this.width - 4;
        if (ctx.fontRenderer().width(text) > maxWidth) {
            text = ctx.fontRenderer().plainSubstrByWidth(text, Math.max(10, maxWidth - ctx.fontRenderer().width("…"))) + "…";
        }
        int textX = this.x + (this.width - ctx.fontRenderer().width(text)) / 2;
        ctx.drawString(ctx.fontRenderer(), text, textX, this.y + this.height - 11,
                this.selected ? 0xFFFFFFFF : TEXT, false);
    }
}
