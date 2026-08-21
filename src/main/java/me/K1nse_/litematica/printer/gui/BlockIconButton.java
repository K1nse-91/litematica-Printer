package me.K1nse_.litematica.printer.gui;

import fi.dy.masa.malilib.gui.button.ButtonBase;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.malilib.render.RenderUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import java.util.List;

/**
 * 方块图标按钮，用于可视化方块选择界面。
 * 渲染方块图标，选中时显示黄色高亮边框。
 */
public class BlockIconButton extends ButtonBase {
    private final Block block;
    private boolean selected;

    public BlockIconButton(Block block, int x, int y, boolean selected) {
        super(x, y, 18, 18);
        this.block = block;
        this.selected = selected;
        this.setHoverStrings(List.of(
                block.getName().getString(),
                BuiltInRegistries.BLOCK.getKey(block).toString()
        ));
    }

    public Block getBlock() {
        return this.block;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    @Override
    public void render(GuiContext ctx, int mouseX, int mouseY, boolean hovered) {
        super.render(ctx, mouseX, mouseY, hovered);

        ItemStack stack = this.block.asItem().getDefaultInstance();
        //#if MC > 12111
        ctx.fakeItem(stack, this.x + 1, this.y + 1);
        //#else
        //$$ ctx.renderFakeItem(stack, this.x + 1, this.y + 1);
        //#endif

        if (this.selected) {
            RenderUtils.drawOutline(ctx, this.x, this.y, 18, 18, 0xFFFFFF00);
        }
    }
}
