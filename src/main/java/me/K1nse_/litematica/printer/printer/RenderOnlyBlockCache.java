package me.K1nse_.litematica.printer.printer;

import fi.dy.masa.litematica.render.LitematicaRenderer;
import me.K1nse_.litematica.printer.config.Configs;
import me.K1nse_.litematica.printer.utils.minecraft.BlockUtils;
import me.K1nse_.litematica.printer.utils.minecraft.IdentifierUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 只渲染指定方块（移植自 EMT）：开启后 litematica 投影只绘制名单内的方块，
 * 超大投影的渲染开销按名单比例下降。
 */
public final class RenderOnlyBlockCache {
    private static volatile boolean enabled;
    private static volatile List<String> source = List.of();
    private static volatile Set<Block> blocks = Set.of();
    private static volatile int changeCounter;
    private static int lastSeenCounter;
    private static volatile boolean initialized;

    private RenderOnlyBlockCache() {
    }

    public static boolean shouldRender(BlockState state) {
        refresh();
        return !enabled || (state != null && blocks.contains(state.getBlock()));
    }

    public static boolean isEnabled() {
        refresh();
        return enabled;
    }

    /** 名单/开关变化后重载 litematica 渲染器 */
    public static void tickPendingReload() {
        if (changeCounter != lastSeenCounter) {
            lastSeenCounter = changeCounter;
            if (Minecraft.getInstance().level != null) {
                LitematicaRenderer.getInstance().loadRenderers(null);
            }
        }
    }

    private static void refresh() {
        boolean currentEnabled = Configs.Special.RENDER_ONLY_BLOCKS.getBooleanValue();
        List<String> currentSource = Configs.Special.RENDER_ONLY_BLOCK_LIST.getStrings();
        if (currentEnabled != enabled || !currentSource.equals(source)) {
            apply(currentEnabled, currentSource);
        }
    }

    private static void apply(boolean currentEnabled, List<String> currentSource) {
        boolean first = !initialized;
        Set<Block> snapshot = new HashSet<>();
        if (currentEnabled) {
            for (String line : currentSource) {
                if (line == null || line.isBlank()) {
                    continue;
                }
                String normalized = line.replace('，', ',').replace('；', ';').replace('：', ':');
                for (String token : normalized.split("[,;]")) {
                    Block block = resolveStrict(token.trim());
                    if (block != null && block != Blocks.AIR) {
                        snapshot.add(block);
                    }
                }
            }
        }
        source = List.copyOf(currentSource);
        blocks = Set.copyOf(snapshot);
        enabled = currentEnabled;
        initialized = true;
        if (!first) {
            changeCounter++;
        }
    }

    private static Block resolveStrict(String token) {
        if (token == null || token.isEmpty()) {
            return null;
        }
        if (token.contains(":")) {
            try {
                Identifier id = IdentifierUtils.of(token);
                return BlockUtils.getBlock(id);
            } catch (Exception e) {
                return null;
            }
        }
        Block byPath = null;
        int pathHits = 0;
        for (Block block : BuiltInRegistries.BLOCK) {
            if (BuiltInRegistries.BLOCK.getKey(block).getPath().equals(token)) {
                byPath = block;
                pathHits++;
            }
        }
        if (pathHits == 1) {
            return byPath;
        }
        if (pathHits > 1) {
            return null;
        }
        Block byName = null;
        int nameHits = 0;
        for (Block block : BuiltInRegistries.BLOCK) {
            if (block.getName().getString().equals(token)) {
                byName = block;
                nameHits++;
            }
        }
        return nameHits == 1 ? byName : null;
    }
}
