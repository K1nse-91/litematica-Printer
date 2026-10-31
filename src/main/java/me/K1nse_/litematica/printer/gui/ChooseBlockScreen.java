package me.K1nse_.litematica.printer.gui;

import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.GuiTextFieldGeneric;
import fi.dy.masa.malilib.render.GuiContext;
import me.K1nse_.litematica.printer.utils.PinYinSearchUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * 方块选择界面（iOS 液态玻璃）：大圆角玻璃面板、两行分段式分类、胶囊搜索、
 * 主操作强调色按钮、圆角单元格、「已选择」分组与一键清空。
 */
public class ChooseBlockScreen extends GuiBase {
    private static final int COLS = 9;
    private static final int ROWS = 5;
    private static final int CELL_W = 40;
    private static final int CELL_H = 32;
    private static final int PAD = 14;
    private static final int RADIUS = 22;
    private static final int PANEL_W = COLS * CELL_W + PAD * 2;
    private static final int PANEL_H = 318;
    private static final int SEG_ROWS = 2;
    private static final int SEG_COLS = 6;
    private static final int SEG_Y = 54;
    private static final int SEG_H = 16;
    private static final int SEG_GAP = 18;

    // 液态玻璃配色（暗色玻璃 + iOS 蓝强调）
    private static final int GLASS_TINT = 0x6610171F;
    private static final int GLASS_VEIL = 0x22FFFFFF;
    private static final int GLASS_EDGE = 0x66FFFFFF;
    private static final int ACCENT = 0xFF0A84FF;
    private static final int ACCENT_LIGHT = 0xFF64D2FF;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int TEXT_DIM = 0xA6FFFFFF;
    private static final int KNOB = 0x8AFFFFFF;
    private static final int KNOB_EDGE = 0x80FFFFFF;
    private static final int BUTTON = 0x2EFFFFFF;
    private static final int BUTTON_HOVER = 0x4DFFFFFF;

    /** 分类：按用途分组，关键字匹配与标签无关，跨版本稳定 */
    public enum Category {
        ALL("全部", id -> true),
        SELECTED("已选择", id -> true),
        STONE("石材", id -> has(id, "stone", "cobble", "brick", "deepslate", "blackstone", "basalt", "tuff", "calcite",
                "dripstone", "prismarine", "sandstone", "purpur", "andesite", "diorite", "granite", "concrete", "terracotta",
                "quartz", "mud", "moss", "smooth_", "chiseled_", "polished_", "cracked_")),
        WOOD("木质", id -> has(id, "log", "wood", "planks", "fence", "door", "trapdoor", "sign", "boat", "raft",
                "ladder", "bookshelf")),
        GLASS("玻璃", id -> has(id, "glass", "pane", "ice")),
        WOOL("羊毛", id -> has(id, "wool", "carpet", "banner", "bed")),
        REDSTONE("红石", id -> has(id, "redstone", "piston", "observer", "repeater", "comparator", "dispenser", "dropper",
                "hopper", "rail", "button", "lever", "pressure_plate", "lamp", "target", "tripwire", "crafter", "sensor",
                "daylight", "note_block", "jukebox", "sculk", "tnt")),
        PLANT("植物", id -> has(id, "sapling", "leaves", "flower", "tulip", "grass", "fern", "vine", "mushroom", "cactus",
                "bamboo", "wheat", "carrot", "potato", "beetroot", "melon", "pumpkin", "kelp", "seagrass", "azalea",
                "lily", "cocoa", "sugar_cane", "wart", "chorus", "fungus", "roots", "sprouts", "bush", "petal", "coral")),
        ORE("矿石", id -> has(id, "ore", "raw_", "ancient_debris", "iron_block", "gold_block", "copper_block",
                "diamond_block", "emerald_block", "lapis_block", "coal_block", "netherite_block", "amethyst", "copper_")),
        LIGHT("照明", id -> has(id, "torch", "lantern", "candle", "glowstone", "shroomlight", "sea_lantern", "end_rod",
                "jack_o_lantern", "campfire", "beacon", "froglight", "fire", "copper_bulb", "lamp")),
        FUNCTION("功能", id -> has(id, "chest", "barrel", "furnace", "smoker", "blast", "crafting", "anvil", "cauldron",
                "cartography", "fletching", "grindstone", "loom", "smithing", "stonecutter", "lectern", "shulker_box",
                "composter", "enchanting", "spawner", "conduit", "bell", "respawn_anchor")),
        MISC("其他", id -> true);

        private final String label;
        private final Predicate<String> matcher;

        Category(String label, Predicate<String> matcher) {
            this.label = label;
            this.matcher = matcher;
        }

        public String label() {
            return this.label;
        }

        static boolean has(String path, String... keywords) {
            for (String keyword : keywords) {
                if (path.contains(keyword)) {
                    return true;
                }
            }
            return false;
        }
    }

    private record Hit(int id, int x, int y, int w, int h) {
        boolean test(double mouseX, double mouseY) {
            return mouseX >= this.x && mouseX < this.x + this.w && mouseY >= this.y && mouseY < this.y + this.h;
        }
    }

    private record Segment(int index, int x, int y, int w) {
    }

    private final List<Block> allBlocks = new ArrayList<>();
    private final List<Block> filtered = new ArrayList<>();
    private final Set<Block> selectedBlocks;
    private final List<String> rawEntries;
    private final Consumer<Set<Block>> onDone;
    private final Map<Item, Integer> availableCounts = new HashMap<>();
    private final List<Hit> hits = new ArrayList<>();
    private final List<Segment> segments = new ArrayList<>();

    private Category category = Category.ALL;
    private int shift;
    private GuiTextFieldGeneric searchBar;

    public ChooseBlockScreen(Set<Block> selected, Consumer<Set<Block>> onDone) {
        this(selected, new ArrayList<>(), onDone);
    }

    public ChooseBlockScreen(Set<Block> selected, List<String> rawEntries, Consumer<Set<Block>> onDone) {
        //#if MC > 260100
        //$$ this.setParent(Minecraft.getInstance().gui.screen());
        //#else
        this.setParent(Minecraft.getInstance().screen);
        //#endif
        this.selectedBlocks = new LinkedHashSet<>(selected);
        this.rawEntries = rawEntries;
        this.onDone = onDone;
        BuiltInRegistries.BLOCK.forEach(this.allBlocks::add);
        this.recountInventory();
        this.refreshFilter("");
    }

    private void recountInventory() {
        this.availableCounts.clear();
        if (Minecraft.getInstance().player == null) {
            return;
        }
        Inventory inventory = Minecraft.getInstance().player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty()) {
                this.availableCounts.merge(stack.getItem(), stack.getCount(), Integer::sum);
            }
        }
        ItemStack offhand = Minecraft.getInstance().player.getOffhandItem();
        if (!offhand.isEmpty()) {
            this.availableCounts.merge(offhand.getItem(), offhand.getCount(), Integer::sum);
        }
    }

    private void refreshFilter(String text) {
        this.filtered.clear();
        String lower = text.toLowerCase();
        if (this.category == Category.SELECTED) {
            for (Block block : this.selectedBlocks) {
                String id = BuiltInRegistries.BLOCK.getKey(block).toString();
                String rawName = block.getName().getString();
                if (text.isEmpty() || rawName.toLowerCase().contains(lower) || id.contains(lower)
                        || PinYinSearchUtils.hasPinYin(rawName, text)) {
                    this.filtered.add(block);
                }
            }
            return;
        }
        for (Block block : this.allBlocks) {
            String id = BuiltInRegistries.BLOCK.getKey(block).toString();
            String path = BuiltInRegistries.BLOCK.getKey(block).getPath();
            if (block.asItem() == Items.AIR || !this.category.matcher.test(path)) {
                continue;
            }
            String rawName = block.getName().getString();
            if (text.isEmpty() || rawName.toLowerCase().contains(lower) || id.contains(lower)
                    || PinYinSearchUtils.hasPinYin(rawName, text)) {
                this.filtered.add(block);
            }
        }
    }

    private int panelX() {
        return (this.getScreenWidth() - PANEL_W) / 2;
    }

    private int panelY() {
        return (this.getScreenHeight() - PANEL_H) / 2;
    }

    private int gridX() {
        return this.panelX() + PAD;
    }

    private int gridY() {
        return this.panelY() + 96;
    }

    private int footerY() {
        return this.panelY() + PANEL_H - 30;
    }

    private int pageCount() {
        return Math.max(1, (this.filtered.size() + COLS * ROWS - 1) / (COLS * ROWS));
    }

    private String pageText() {
        int from = this.filtered.isEmpty() ? 0 : this.shift * COLS * ROWS + 1;
        int to = Math.min(this.filtered.size(), (this.shift + 1) * COLS * ROWS);
        return from + "-" + to + " / " + this.filtered.size();
    }

    private int pageCenterX() {
        return this.panelX() + PANEL_W / 2 - 6;
    }

    private int prevButtonX() {
        int w = Minecraft.getInstance().font.width(this.pageText());
        return this.pageCenterX() - w / 2 - 24;
    }

    private int nextButtonX() {
        int w = Minecraft.getInstance().font.width(this.pageText());
        return this.pageCenterX() + w / 2 + 6;
    }

    @Override
    public void initGui() {
        super.initGui();
        int px = this.panelX();
        int py = this.panelY();
        this.hits.clear();
        this.segments.clear();

        // 搜索框：无边框，画在自绘玻璃槽里
        if (this.searchBar == null) {
            this.searchBar = new GuiTextFieldGeneric(px + PAD + 12, py + 31, PANEL_W - PAD * 2 - 20, 14, Minecraft.getInstance().font);
            this.searchBar.setBordered(false);
            this.searchBar.setTextColor(0xFFFFFFFF);
            this.searchBar.setTextColorUneditable(0xFFFFFFFF);
        }
        this.searchBar.setXWrapper(px + PAD + 12);
        this.searchBar.setYWrapper(py + 31);
        this.addTextField(this.searchBar, textField -> {
            this.refreshFilter(textField.getTextWrapper());
            this.shift = 0;
            this.initGui();
            return true;
        });

        Font font = Minecraft.getInstance().font;

        // 两行分段控件
        int innerW = PANEL_W - PAD * 2;
        int cellW = innerW / SEG_COLS;
        int remainder = innerW - cellW * SEG_COLS;
        for (int i = 0; i < Category.values().length; i++) {
            int row = i / SEG_COLS;
            int col = i % SEG_COLS;
            int segX = px + PAD;
            for (int k = 0; k < col; k++) {
                segX += cellW + (k < remainder ? 1 : 0);
            }
            int w = cellW + (col < remainder ? 1 : 0);
            int segY = py + SEG_Y + row * SEG_GAP;
            this.segments.add(new Segment(i, segX, segY, w));
        }

        // 网格
        int gx = this.gridX();
        int gy = this.gridY();
        int index = this.shift * COLS * ROWS;
        outer:
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                if (index >= this.filtered.size()) {
                    break outer;
                }
                Block block = this.filtered.get(index++);
                int count = this.availableCounts.getOrDefault(block.asItem(), 0);
                BlockIconButton button = new BlockIconButton(block, count,
                        gx + col * CELL_W, gy + row * CELL_H, CELL_W, CELL_H,
                        this.selectedBlocks.contains(block));
                this.addButton(button, (btn, mouseButton) -> {
                    Block clicked = ((BlockIconButton) btn).getBlock();
                    if (this.selectedBlocks.contains(clicked)) {
                        this.selectedBlocks.remove(clicked);
                    } else {
                        this.selectedBlocks.add(clicked);
                    }
                    ((BlockIconButton) btn).setSelected(this.selectedBlocks.contains(clicked));
                });
            }
        }

        // 底部按钮与清空
        int fy = this.footerY();
        this.hits.add(new Hit(200, px + PAD, fy, 74, 20));
        this.hits.add(new Hit(206, px + PAD + 80, fy, 62, 20));
        this.hits.add(new Hit(203, this.prevButtonX(), fy + 1, 18, 18));
        this.hits.add(new Hit(204, this.nextButtonX(), fy + 1, 18, 18));
        this.hits.add(new Hit(202, px + PANEL_W - PAD - 128, fy, 60, 20));
        this.hits.add(new Hit(201, px + PANEL_W - PAD - 64, fy, 64, 20));
        int clearW = Math.max(96, font.width("清除全部已选择方块") + 16);
        this.hits.add(new Hit(205, px + PAD, py + PANEL_H - 52, clearW, 18));
    }

    @Override
    public boolean onMouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x();
        double my = event.y();
        if (event.button() == 0) {
            for (Segment segment : this.segments) {
                if (mx >= segment.x() && mx < segment.x() + segment.w()
                        && my >= segment.y() && my < segment.y() + SEG_H + 2) {
                    this.category = Category.values()[segment.index()];
                    this.shift = 0;
                    this.refreshFilter(this.searchBar == null ? "" : this.searchBar.getTextWrapper());
                    this.initGui();
                    return true;
                }
            }
            for (Hit hit : this.hits) {
                if (!hit.test(mx, my)) {
                    continue;
                }
                switch (hit.id()) {
                    case 200 -> {
                        int from = this.shift * COLS * ROWS;
                        int to = Math.min(this.filtered.size(), from + COLS * ROWS);
                        for (int i = from; i < to; i++) {
                            this.selectedBlocks.add(this.filtered.get(i));
                        }
                        this.initGui();
                    }
                    case 201 -> {
                        this.onDone.accept(this.selectedBlocks);
                        this.closeGui(true);
                    }
                    case 202 -> this.closeGui(true);
                    case 203 -> {
                        if (this.shift > 0) {
                            this.shift--;
                            this.initGui();
                        }
                    }
                    case 204 -> {
                        if (this.shift + 1 < this.pageCount()) {
                            this.shift++;
                            this.initGui();
                        }
                    }
                    case 206 -> {
                        String matcher = this.selectedBlocks.isEmpty() ? "" : net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(this.selectedBlocks.iterator().next()).toString();
                        GuiBase.openGui(new BlockNbtRuleScreen(matcher, "", rule -> this.rawEntries.add(rule)));
                    }
                    case 205 -> {
                        this.selectedBlocks.clear();
                        this.refreshFilter(this.searchBar == null ? "" : this.searchBar.getTextWrapper());
                        this.initGui();
                    }
                    default -> {
                    }
                }
                return true;
            }
        }
        return super.onMouseClicked(event, doubleClick);
    }

    @Override
    public boolean onMouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int maxShift = Math.max(0, this.pageCount() - 1);
        int newShift = Math.max(0, Math.min(this.shift - (int) Math.signum(verticalAmount), maxShift));
        if (newShift != this.shift) {
            this.shift = newShift;
            this.initGui();
        }
        return true;
    }

    // ---- 绘制 ----

    @Override
    protected void drawScreenBackground(GuiContext ctx, int mouseX, int mouseY) {
        int px = this.panelX();
        int py = this.panelY();
        Font font = ctx.fontRenderer();

        ctx.fill(0, 0, this.getScreenWidth(), this.getScreenHeight(), 0x6E000000);

        // 玻璃面板
        Glass.surface(ctx, px, py, PANEL_W, PANEL_H, RADIUS, GLASS_TINT, GLASS_EDGE, 0x1C, 0x44);
        Glass.rect(ctx, px + 1, py + 1, PANEL_W - 2, PANEL_H - 2, RADIUS - 1, GLASS_VEIL);
        for (int i = 0; i < 18; i++) {
            int alpha = 0x0C * (18 - i) / 18;
            if (alpha > 0) {
                ctx.fill(px + RADIUS, py + 1 + i, px + PANEL_W - RADIUS, py + 2 + i, alpha << 24);
            }
        }
        ctx.fill(px + 2, py + PANEL_H - 22, px + PANEL_W - 2, py + PANEL_H - 1, 0x14000000);
        Glass.ring(ctx, px + 1, py + 1, PANEL_W - 2, PANEL_H - 2, RADIUS - 1, 0x14000000);

        // 搜索胶囊
        Glass.capsule(ctx, px + PAD, py + 28, PANEL_W - PAD * 2, 20, 0x33000000);
        Glass.capsuleRing(ctx, px + PAD, py + 28, PANEL_W - PAD * 2, 20, 0x30FFFFFF);

        // 分段控件：两行容器 + 选中滑块
        int innerW = PANEL_W - PAD * 2;
        for (int row = 0; row < SEG_ROWS; row++) {
            int segY = py + SEG_Y + row * SEG_GAP;
            Glass.capsule(ctx, px + PAD, segY, innerW, SEG_H + 2, 0x2A000000);
            Glass.capsuleRing(ctx, px + PAD, segY, innerW, SEG_H + 2, 0x33FFFFFF);
        }
        for (Segment segment : this.segments) {
            boolean active = Category.values()[segment.index()] == this.category;
            int segY = segment.y();
            if (active) {
                Glass.capsule(ctx, segment.x() + 1, segY + 1, segment.w() - 2, SEG_H, KNOB);
                Glass.capsuleRing(ctx, segment.x() + 1, segY + 1, segment.w() - 2, SEG_H, KNOB_EDGE);
                ctx.fill(segment.x() + 6, segY + 2, segment.x() + segment.w() - 6, segY + 3, 0x40FFFFFF);
                ctx.fill(segment.x() + 4, segY + SEG_H, segment.x() + segment.w() - 4, segY + SEG_H + 1, 0x22000000);
            }
            String label = Category.values()[segment.index()].label();
            int color = active ? 0xFF0B0F14 : TEXT_DIM;
            ctx.drawString(font, label, segment.x() + (segment.w() - font.width(label)) / 2, segY + 5, color, false);
        }

        // 网格玻璃底
        int gx = this.gridX();
        int gy = this.gridY();
        int gw = COLS * CELL_W;
        int gh = ROWS * CELL_H;
        Glass.rect(ctx, gx - 5, gy - 5, gw + 10, gh + 10, 12, 0x2A000000);
        Glass.ring(ctx, gx - 5, gy - 5, gw + 10, gh + 10, 12, 0x1FFFFFFF);

        // 清除全部已选择方块
        String clearLabel = "清除全部已选择方块";
        int clearW = Math.max(96, font.width(clearLabel) + 16);
        int clearX = px + PAD;
        int clearY = py + PANEL_H - 52;
        boolean clearHover = mouseX >= clearX && mouseX < clearX + clearW && mouseY >= clearY && mouseY < clearY + 18;
        Glass.capsule(ctx, clearX, clearY, clearW, 18, clearHover ? 0x33FF6B6B : 0x1AFF6B6B);
        Glass.capsuleRing(ctx, clearX, clearY, clearW, 18, clearHover ? 0x99FF6B6B : 0x55FF6B6B);
        ctx.drawString(font, clearLabel, clearX + (clearW - font.width(clearLabel)) / 2, clearY + 5,
                clearHover ? 0xFFFF8A80 : 0xFFFF6B6B, false);

        // 底部
        int fy = this.footerY();
        boolean selectHover = mouseX >= gx && mouseX < gx + 74 && mouseY >= fy && mouseY < fy + 20;
        if (selectHover) {
            Glass.capsule(ctx, gx, fy, 74, 20, 0x1FFFFFFF);
        }
        boolean nbtHover = mouseX >= gx + 80 && mouseX < gx + 142 && mouseY >= fy && mouseY < fy + 20;
        if (nbtHover) {
            Glass.capsule(ctx, gx + 80, fy, 62, 20, 0x1FFFFFFF);
        }
        ctx.drawString(font, "NBT 规则", gx + 80 + (62 - font.width("NBT 规则")) / 2, fy + 6,
                nbtHover ? TEXT : TEXT_DIM, false);
        ctx.drawString(font, "全选本页", gx + (74 - font.width("全选本页")) / 2, fy + 6,
                selectHover ? TEXT : TEXT_DIM, false);

        // 翻页
        int navY = fy + 1;
        int prevX = this.prevButtonX();
        int nextX = this.nextButtonX();
        boolean prevHover = mouseX >= prevX && mouseX < prevX + 18 && mouseY >= navY && mouseY < navY + 18;
        boolean nextHover = mouseX >= nextX && mouseX < nextX + 18 && mouseY >= navY && mouseY < navY + 18;
        Glass.capsule(ctx, prevX, navY, 18, 18, prevHover ? BUTTON_HOVER : BUTTON);
        Glass.capsuleRing(ctx, prevX, navY, 18, 18, 0x33FFFFFF);
        Glass.capsule(ctx, nextX, navY, 18, 18, nextHover ? BUTTON_HOVER : BUTTON);
        Glass.capsuleRing(ctx, nextX, navY, 18, 18, 0x33FFFFFF);
        ctx.drawString(font, "<", prevX + 6, navY + 5, this.shift > 0 ? TEXT : TEXT_DIM, false);
        ctx.drawString(font, ">", nextX + 6, navY + 5, this.shift + 1 < this.pageCount() ? TEXT : TEXT_DIM, false);

        // 取消 / 完成
        int cancelX = px + PANEL_W - PAD - 128;
        boolean cancelHover = mouseX >= cancelX && mouseX < cancelX + 60 && mouseY >= fy && mouseY < fy + 20;
        Glass.capsule(ctx, cancelX, fy, 60, 20, cancelHover ? BUTTON_HOVER : BUTTON);
        Glass.capsuleRing(ctx, cancelX, fy, 60, 20, 0x3DFFFFFF);
        ctx.drawString(font, "取消", cancelX + (60 - font.width("取消")) / 2, fy + 6, TEXT, false);

        int doneX = px + PANEL_W - PAD - 64;
        boolean doneHover = mouseX >= doneX && mouseX < doneX + 64 && mouseY >= fy && mouseY < fy + 20;
        Glass.capsule(ctx, doneX, fy, 64, 20, doneHover ? 0xFF2E9BFF : ACCENT);
        Glass.capsuleRing(ctx, doneX, fy, 64, 20, 0x66FFFFFF);
        ctx.fill(doneX + 6, fy + 1, doneX + 58, fy + 2, 0x40FFFFFF);
        ctx.drawString(font, "完成", doneX + (64 - font.width("完成")) / 2, fy + 6, TEXT, false);
    }

    @Override
    protected void drawContents(GuiContext ctx, int mouseX, int mouseY, float partialTicks) {
        int px = this.panelX();
        int py = this.panelY();
        Font font = ctx.fontRenderer();

        ctx.drawString(font, "添加方块到名单", px + PAD, py + 12, TEXT, false);
        String selectedText = "已选 " + this.selectedBlocks.size();
        ctx.drawString(font, selectedText, px + PANEL_W - PAD - font.width(selectedText), py + 12, ACCENT_LIGHT, false);

        if (this.searchBar != null && this.searchBar.getTextWrapper().isEmpty()) {
            ctx.drawString(font, "搜索名称 / ID / 拼音", px + PAD + 12, py + 34, 0x80FFFFFF, false);
        }

        String pageText = this.pageText();
        int pageX = this.pageCenterX() - font.width(pageText) / 2;
        ctx.drawString(font, pageText, pageX, py + PANEL_H - 23, TEXT_DIM, false);

        if (this.filtered.isEmpty()) {
            int gx = this.gridX();
            int gy = this.gridY();
            String hint = this.category == Category.SELECTED ? "还没有选择任何方块" : "没有匹配的方块";
            ctx.drawCenteredString(font, hint, gx + COLS * CELL_W / 2, gy + ROWS * CELL_H / 2, TEXT_DIM);
        }
    }
}
