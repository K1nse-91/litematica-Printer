package me.K1nse_.litematica.printer.gui;

import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.GuiTextFieldGeneric;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.render.GuiContext;
import me.K1nse_.litematica.printer.utils.PinYinSearchUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * 可视化方块选择界面（多选）。
 * 显示所有方块的图标网格，支持搜索、滚轮翻页、点击勾选/取消。
 */
public class ChooseBlockScreen extends GuiBase {
    private static final int COLS = 9;
    private static final int ROWS = 6;
    private static final int CELL = 22;

    private final List<Block> allBlocks = new ArrayList<>();
    private final Set<Block> selectedBlocks;
    private final Consumer<Set<Block>> onDone;
    private final List<Block> filtered = new ArrayList<>();

    private int shift = 0;
    private boolean needInitGui = true;
    private GuiTextFieldGeneric searchBar;

    public ChooseBlockScreen(Set<Block> selected, Consumer<Set<Block>> onDone) {
        //#if MC > 260100
        //$$ this.setParent(Minecraft.getInstance().gui.screen());
        //#else
        this.setParent(Minecraft.getInstance().screen);
        //#endif
        this.selectedBlocks = new HashSet<>(selected);
        this.onDone = onDone;
        BuiltInRegistries.BLOCK.forEach(this.allBlocks::add);
        this.refreshFilter("");
    }

    private void refreshFilter(String text) {
        this.filtered.clear();
        String lower = text.toLowerCase();
        for (Block block : this.allBlocks) {
            String rawName = block.getName().getString();
            String name = rawName.toLowerCase();
            String id = BuiltInRegistries.BLOCK.getKey(block).toString();
            if (text.isEmpty() || name.contains(lower) || id.contains(lower)
                    || PinYinSearchUtils.hasPinYin(rawName, text)) {
                this.filtered.add(block);
            }
        }
    }

    @Override
    public void initGui() {
        super.initGui();
        this.needInitGui = false;

        int gridW = CELL * COLS;
        int gridH = CELL * ROWS;
        int x0 = (this.getScreenWidth() - gridW) / 2;
        int y0 = (this.getScreenHeight() - gridH) / 2;

        // 搜索框（复用字段，避免每次重建丢失输入）
        if (this.searchBar == null) {
            this.searchBar = new GuiTextFieldGeneric(x0, y0 - 28, gridW, 16, Minecraft.getInstance().font);
        }
        this.searchBar.setXWrapper(x0);
        this.searchBar.setYWrapper(y0 - 28);
        this.addTextField(this.searchBar, textField -> {
            this.refreshFilter(textField.getTextWrapper());
            this.shift = 0;
            this.needInitGui = true;
            return true;
        });

        // 方块图标网格
        int index = 0;
        outer:
        for (int a = 0; a < ROWS; a++) {
            for (int b = 0; b < COLS; b++) {
                index = this.shift * COLS + a * COLS + b;
                if (index >= this.filtered.size()) {
                    break outer;
                }
                Block block = this.filtered.get(index);
                int x = x0 + b * CELL + 2;
                int y = y0 + a * CELL + 2;
                BlockIconButton button = new BlockIconButton(block, x, y, this.selectedBlocks.contains(block));
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

        // 完成 / 取消按钮
        int doneY = y0 + gridH + 12;
        ButtonGeneric doneButton = new ButtonGeneric(x0 + gridW / 2 - 90, doneY, 80, 20, "完成");
        this.addButton(doneButton, (btn, mb) -> {
            this.onDone.accept(this.selectedBlocks);
            this.closeGui(true);
        });
        ButtonGeneric cancelButton = new ButtonGeneric(x0 + gridW / 2 + 10, doneY, 80, 20, "取消");
        this.addButton(cancelButton, (btn, mb) -> this.closeGui(true));
    }

    @Override
    public boolean onMouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int maxShift = Math.max(0, (this.filtered.size() + COLS - 1) / COLS - ROWS);
        int newShift = this.shift - (int) Math.signum(verticalAmount);
        if (newShift > maxShift) {
            newShift = maxShift;
        }
        if (newShift < 0) {
            newShift = 0;
        }
        if (newShift != this.shift) {
            this.shift = newShift;
            this.initGui();
        }
        return true;
    }

    @Override
    protected void drawContents(GuiContext ctx, int mouseX, int mouseY, float partialTicks) {
        if (this.needInitGui) {
            this.initGui();
        }
    }
}
