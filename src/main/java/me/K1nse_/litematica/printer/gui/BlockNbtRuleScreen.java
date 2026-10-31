package me.K1nse_.litematica.printer.gui;

import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.GuiTextFieldGeneric;
import fi.dy.masa.malilib.render.GuiContext;
import me.K1nse_.litematica.printer.utils.BlockNbtRule;
import me.K1nse_.litematica.printer.utils.FilterUtils;
import me.K1nse_.litematica.printer.utils.minecraft.PlayerUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * NBT 规则编辑界面（移植自 EMT）：按准星/附近方块自动列出方块状态属性，
 * 逐项填值后编码成 ``@nbt:`` 规则并追加到名单配置。
 */
public class BlockNbtRuleScreen extends GuiBase {
    private static final int RADIUS = 18;
    private static final int PANEL_W = 372;
    private static final int PANEL_H = 252;
    private static final int PAD = 12;
    private static final int ROW_H = 20;
    private static final int VISIBLE_ROWS = 5;

    private static final int GLASS_TINT = 0x6610171F;
    private static final int GLASS_VEIL = 0x22FFFFFF;
    private static final int GLASS_EDGE = 0x66FFFFFF;
    private static final int ACCENT = 0xFF0A84FF;
    private static final int ACCENT_LIGHT = 0xFF64D2FF;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int TEXT_DIM = 0xA6FFFFFF;

    private final String initialMatcher;
    private final String initialEncoded;
    private final Consumer<String> onSave;

    private final List<String> paths = new ArrayList<>();
    private final List<GuiTextFieldGeneric> valueFields = new ArrayList<>();
    private final Map<String, String> initialValues = new LinkedHashMap<>();
    private GuiTextFieldGeneric matcherField;
    private int scrollOffset;
    private int saveHover;
    private int cancelHover;

    public BlockNbtRuleScreen(String matcher, String encoded, Consumer<String> onSave) {
        //#if MC > 260100
        //$$ this.setParent(Minecraft.getInstance().gui.screen());
        //#else
        this.setParent(Minecraft.getInstance().screen);
        //#endif
        this.initialMatcher = matcher == null ? "" : matcher;
        this.initialEncoded = encoded == null ? "" : encoded;
        this.onSave = onSave;

        BlockNbtRule rule = BlockNbtRule.parse(this.initialEncoded);
        String matcherFromConfig = rule.blockMatcher().isBlank() ? this.initialMatcher : rule.blockMatcher();
        for (BlockNbtRule.Condition condition : rule.conditions()) {
            this.initialValues.put(condition.path(), condition.value());
        }
        BlockState target = this.findTargetState(matcherFromConfig);
        Set<String> discovered = new LinkedHashSet<>();
        if (target != null) {
            for (Property<?> property : target.getProperties()) {
                discovered.add("state." + property.getName());
            }
        }
        discovered.addAll(this.initialValues.keySet());
        this.paths.addAll(discovered);
        if (this.paths.isEmpty()) {
            this.paths.add("state.waterlogged");
        }
        this.title = "NBT 规则";
    }

    private BlockState findTargetState(String matcher) {
        ClientLevel level = Minecraft.getInstance().level;
        LocalPlayer player = Minecraft.getInstance().player;
        if (level == null || player == null) {
            return null;
        }
        if (Minecraft.getInstance().hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK
                && PlayerUtils.isWithinBlockInteractionRange(player, hit.getBlockPos(), 0.0D)) {
            BlockState state = level.getBlockState(hit.getBlockPos());
            if (matcher.isBlank() || FilterUtils.matchBlockName(matcher, state)) {
                return state;
            }
        }
        int range = (int) Math.ceil(PlayerUtils.getPlayerBlockInteractionRange());
        BlockPos center = player.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-range, -range, -range), center.offset(range, range, range))) {
            if (!PlayerUtils.isWithinBlockInteractionRange(player, pos, 0.0D)) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (matcher.isBlank() || FilterUtils.matchBlockName(matcher, state)) {
                return state;
            }
        }
        return null;
    }

    private int panelX() {
        return (this.getScreenWidth() - PANEL_W) / 2;
    }

    private int panelY() {
        return (this.getScreenHeight() - PANEL_H) / 2;
    }

    private int rowsY() {
        return this.panelY() + 84;
    }

    @Override
    public void initGui() {
        super.initGui();
        int px = this.panelX();
        int py = this.panelY();
        if (this.matcherField == null) {
            this.matcherField = new GuiTextFieldGeneric(px + PAD + 42, py + 38, PANEL_W - PAD * 2 - 48, 14,
                    Minecraft.getInstance().font);
            this.matcherField.setBordered(false);
            this.matcherField.setTextColor(0xFFFFFFFF);
            this.matcherField.setValueWrapper(this.initialMatcher);
        }
        this.matcherField.setXWrapper(px + PAD + 42);
        this.matcherField.setYWrapper(py + 38);
        this.addTextField(this.matcherField, field -> true);

        this.valueFields.clear();
        for (int i = 0; i < this.paths.size(); i++) {
            GuiTextFieldGeneric field = new GuiTextFieldGeneric(px + PAD + 120, this.rowsY() + i * ROW_H,
                    PANEL_W - PAD * 2 - 124, 14, Minecraft.getInstance().font);
            field.setBordered(false);
            field.setTextColor(0xFFFFFFFF);
            field.setValueWrapper(this.initialValues.getOrDefault(this.paths.get(i), ""));
            this.valueFields.add(field);
            this.addTextField(field, f -> true);
        }
        this.updateFieldPositions();
    }

    private void updateFieldPositions() {
        int px = this.panelX();
        for (int i = 0; i < this.valueFields.size(); i++) {
            GuiTextFieldGeneric field = this.valueFields.get(i);
            int row = i - this.scrollOffset;
            if (row >= 0 && row < VISIBLE_ROWS) {
                field.setXWrapper(px + PAD + 120);
                field.setYWrapper(this.rowsY() + row * ROW_H + 3);
            } else {
                field.setXWrapper(-2000);
                field.setYWrapper(-2000);
            }
        }
    }

    @Override
    public boolean onMouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int max = Math.max(0, this.paths.size() - VISIBLE_ROWS);
        int next = Math.max(0, Math.min(this.scrollOffset + (verticalAmount > 0 ? -1 : 1), max));
        if (next != this.scrollOffset) {
            this.scrollOffset = next;
            this.updateFieldPositions();
        }
        return true;
    }

    @Override
    public boolean onMouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0) {
            int px = this.panelX();
            int py = this.panelY();
            int fy = py + PANEL_H - 30;
            int cancelX = px + PANEL_W - PAD - 132;
            int saveX = px + PANEL_W - PAD - 66;
            if (event.x() >= saveX && event.x() < saveX + 66 && event.y() >= fy && event.y() < fy + 20) {
                this.save();
                return true;
            }
            if (event.x() >= cancelX && event.x() < cancelX + 62 && event.y() >= fy && event.y() < fy + 20) {
                this.closeGui(true);
                return true;
            }
        }
        return super.onMouseClicked(event, doubleClick);
    }

    private void save() {
        List<BlockNbtRule.Condition> conditions = new ArrayList<>();
        for (int i = 0; i < this.paths.size(); i++) {
            String value = this.valueFields.get(i).getTextWrapper();
            if (value != null && !value.isBlank()) {
                conditions.add(new BlockNbtRule.Condition(this.paths.get(i), value.trim()));
            }
        }
        String matcher = this.matcherField.getTextWrapper();
        if (matcher == null || matcher.isBlank()) {
            matcher = BuiltInRegistries.BLOCK.getKey(net.minecraft.world.level.block.Blocks.STONE).toString();
        }
        this.onSave.accept(new BlockNbtRule(matcher.trim(), conditions).encode());
        this.closeGui(true);
    }

    @Override
    protected void drawScreenBackground(GuiContext ctx, int mouseX, int mouseY) {
        int px = this.panelX();
        int py = this.panelY();
        ctx.fill(0, 0, this.getScreenWidth(), this.getScreenHeight(), 0x6E000000);
        Glass.surface(ctx, px, py, PANEL_W, PANEL_H, RADIUS, GLASS_TINT, GLASS_EDGE, 0x1C, 0x44);
        Glass.rect(ctx, px + 1, py + 1, PANEL_W - 2, PANEL_H - 2, RADIUS - 1, GLASS_VEIL);
        // 标题栏发丝线
        ctx.fill(px + RADIUS, py + 26, px + PANEL_W - RADIUS, py + 27, 0xCC64D2FF);

        // 方块匹配输入槽
        Glass.capsule(ctx, px + PAD, py + 34, PANEL_W - PAD * 2, 20, 0x33000000);
        Glass.capsuleRing(ctx, px + PAD, py + 34, PANEL_W - PAD * 2, 20, 0x30FFFFFF);

        // 属性行背景
        int rowsY = this.rowsY();
        for (int i = 0; i < VISIBLE_ROWS; i++) {
            int rowY = rowsY + i * ROW_H;
            Glass.rect(ctx, px + PAD, rowY, PANEL_W - PAD * 2, ROW_H - 2, 6, i % 2 == 0 ? 0x18FFFFFF : 0x0EFFFFFF);
        }
        // 值输入槽
        for (int i = 0; i < VISIBLE_ROWS; i++) {
            int rowY = rowsY + i * ROW_H + 3;
            Glass.capsule(ctx, px + PAD + 118, rowY - 2, PANEL_W - PAD * 2 - 118, 14, 0x33000000);
        }

        // 底部按钮
        int fy = py + PANEL_H - 30;
        int cancelX = px + PANEL_W - PAD - 132;
        int saveX = px + PANEL_W - PAD - 66;
        boolean cancelHovered = mouseX >= cancelX && mouseX < cancelX + 62 && mouseY >= fy && mouseY < fy + 20;
        boolean saveHovered = mouseX >= saveX && mouseX < saveX + 66 && mouseY >= fy && mouseY < fy + 20;
        Glass.capsule(ctx, cancelX, fy, 62, 20, cancelHovered ? 0x4DFFFFFF : 0x2EFFFFFF);
        Glass.capsuleRing(ctx, cancelX, fy, 62, 20, 0x3DFFFFFF);
        Glass.capsule(ctx, saveX, fy, 66, 20, saveHovered ? 0xFF2E9BFF : ACCENT);
        Glass.capsuleRing(ctx, saveX, fy, 66, 20, 0x66FFFFFF);
        ctx.fill(saveX + 6, fy + 1, saveX + 60, fy + 2, 0x40FFFFFF);
    }

    @Override
    protected void drawContents(GuiContext ctx, int mouseX, int mouseY, float partialTicks) {
        int px = this.panelX();
        int py = this.panelY();
        Font font = ctx.fontRenderer();
        ctx.drawString(font, "NBT 规则", px + PAD, py + 10, TEXT, false);
        ctx.drawString(font, "属性值留空表示不限制", px + PANEL_W - PAD - font.width("属性值留空表示不限制"), py + 10,
                TEXT_DIM, false);

        ctx.drawString(font, "方块", px + PAD, py + 40, TEXT_DIM, false);
        if (this.matcherField != null && this.matcherField.getTextWrapper().isEmpty()) {
            ctx.drawString(font, "例如 minecraft:oak_log", px + PAD + 44, py + 39, 0x80FFFFFF, false);
        }

        int rowsY = this.rowsY();
        for (int i = 0; i < VISIBLE_ROWS; i++) {
            int index = i + this.scrollOffset;
            if (index >= this.paths.size()) {
                break;
            }
            String path = this.paths.get(index);
            ctx.drawString(font, path, px + PAD + 4, rowsY + i * ROW_H + 4, TEXT_DIM, false);
        }
        if (this.paths.size() > VISIBLE_ROWS) {
            String hint = (this.scrollOffset + 1) + "-" + Math.min(this.paths.size(), this.scrollOffset + VISIBLE_ROWS)
                    + " / " + this.paths.size() + "（滚轮翻页）";
            ctx.drawString(font, hint, px + PAD, py + PANEL_H - 22, TEXT_DIM, false);
        }
        ctx.drawString(font, "取消", px + PANEL_W - PAD - 132 + 31, py + PANEL_H - 24, TEXT, false);
        ctx.drawString(font, "保存", px + PANEL_W - PAD - 66 + 33, py + PANEL_H - 24, TEXT, false);
    }
}
