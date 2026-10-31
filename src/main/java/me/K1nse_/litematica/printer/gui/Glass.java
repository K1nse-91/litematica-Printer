package me.K1nse_.litematica.printer.gui;

import fi.dy.masa.malilib.render.GuiContext;

/**
 * iOS 26 液态玻璃绘制工具（纯 fill 实现，无着色器）：
 * 真圆角（逐行内缩）、悬浮投影、边缘高光/暗边、顶部反光渐变、胶囊。
 */
public final class Glass {
    private Glass() {
    }

    /** 圆角矩形半径 r 的逐行内缩表 */
    private static int inset(int r, int row) {
        double dy = r - row - 0.5;
        double dx = Math.sqrt(Math.max(0.0, (double) r * r - dy * dy));
        return (int) Math.round(r - dx);
    }

    /** 填充圆角矩形 */
    public static void rect(GuiContext ctx, int x, int y, int w, int h, int r, int color) {
        int radius = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        if (radius == 0) {
            ctx.fill(x, y, x + w, y + h, color);
            return;
        }
        ctx.fill(x, y + radius, x + w, y + h - radius, color);
        for (int i = 0; i < radius; i++) {
            int in = inset(radius, i);
            ctx.fill(x + in, y + i, x + w - in, y + i + 1, color);
            ctx.fill(x + in, y + h - i - 1, x + w - in, y + h - i, color);
        }
    }

    /** 1px 圆角描边 */
    public static void ring(GuiContext ctx, int x, int y, int w, int h, int r, int color) {
        int radius = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        if (radius == 0) {
            ctx.fill(x, y, x + w, y + 1, color);
            ctx.fill(x, y + h - 1, x + w, y + h, color);
            ctx.fill(x, y, x + 1, y + h, color);
            ctx.fill(x + w - 1, y, x + w, y + h, color);
            return;
        }
        ctx.fill(x + radius, y, x + w - radius, y + 1, color);
        ctx.fill(x + radius, y + h - 1, x + w - radius, y + h, color);
        ctx.fill(x, y + radius, x + 1, y + h - radius, color);
        ctx.fill(x + w - 1, y + radius, x + w, y + h - radius, color);
        for (int i = 0; i < radius; i++) {
            int inA = inset(radius, i);
            int inB = inset(radius, i + 1);
            // 左上/右上 圆弧
            ctx.fill(x + inA, y + i, x + inB + 1, y + i + 1, color);
            ctx.fill(x + w - inB - 1, y + i, x + w - inA, y + i + 1, color);
            // 左下/右下 圆弧
            ctx.fill(x + inA, y + h - i - 1, x + inB + 1, y + h - i, color);
            ctx.fill(x + w - inB - 1, y + h - i - 1, x + w - inA, y + h - i, color);
        }
    }

    /** 悬浮投影：由外到内多层低透明度圆角矩形 */
    public static void shadow(GuiContext ctx, int x, int y, int w, int h, int r, int spread, int baseAlpha) {
        for (int i = spread; i >= 1; i--) {
            int alpha = Math.max(3, baseAlpha / (i + 1));
            rect(ctx, x - i, y - i + 2, w + i * 2, h + i * 2, r + i, (alpha << 24));
        }
    }

    /** 顶部反光：自上而下的多段渐隐（液态玻璃的光泽层） */
    public static void gloss(GuiContext ctx, int x, int y, int w, int h, int r, int topAlpha, int bands) {
        int bandH = Math.max(1, h / (bands * 2));
        for (int i = 0; i < bands; i++) {
            int alpha = topAlpha * (bands - i) / bands;
            if (alpha <= 0) {
                continue;
            }
            int yy = y + i * bandH;
            rect(ctx, x, yy, w, bandH, Math.max(0, r - i * 2), (alpha << 24));
        }
    }

    /** 胶囊（r = h/2） */
    public static void capsule(GuiContext ctx, int x, int y, int w, int h, int color) {
        rect(ctx, x, y, w, h, h / 2, color);
    }

    public static void capsuleRing(GuiContext ctx, int x, int y, int w, int h, int color) {
        ring(ctx, x, y, w, h, h / 2, color);
    }

    /** 玻璃面：底 + 顶部光泽 + 外投影 + 双描边 + 边缘高光/暗边 */
    public static void surface(GuiContext ctx, int x, int y, int w, int h, int r,
                               int base, int edge, int glossAlpha, int shadowAlpha) {
        shadow(ctx, x, y, w, h, r, 5, shadowAlpha);
        rect(ctx, x, y, w, h, r, base);
        gloss(ctx, x + 1, y + 1, w - 2, h / 3, Math.max(0, r - 1), glossAlpha, 6);
        ring(ctx, x, y, w, h, r, edge);
        // 上缘高光 + 下缘暗边（玻璃厚度感）
        ctx.fill(x + r, y, x + w - r, y + 1, 0x66FFFFFF);
        ctx.fill(x + r, y + h - 1, x + w - r, y + h, 0x40000000);
    }
}
