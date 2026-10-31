package me.K1nse_.litematica.printer.printer;

import me.K1nse_.litematica.printer.config.Configs;
import me.K1nse_.litematica.printer.utils.minecraft.BlockUtils;
import me.K1nse_.litematica.printer.utils.minecraft.IdentifierUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 材料替代放置缓存（移植自 EMT）。
 * 解析「替代1,替代2:目标」格式的配置行：目标方块缺失时可用替代方块顶上。
 * 条目示例：{@code minecraft:deepslate_tiles:minecraft:stone_bricks}
 * （把深板岩砖用石砖替代）。支持物品 ID、纯路径名与中文名解析。
 */
public final class SubstitutePlacementCache {
    private static boolean enabled;
    private static List<String> source = List.of();
    private static final Map<Block, List<Block>> substitutesByTarget = new HashMap<>();

    private SubstitutePlacementCache() {
    }

    public static boolean active() {
        refresh();
        return enabled && !substitutesByTarget.isEmpty();
    }

    public static boolean hasSubstitutes(Block target) {
        refresh();
        return substitutesByTarget.containsKey(target);
    }

    public static List<Block> getSubstitutes(Block target) {
        refresh();
        return substitutesByTarget.getOrDefault(target, List.of());
    }

    public static boolean isSubstituteOf(Block target, Block candidate) {
        refresh();
        return substitutesByTarget.getOrDefault(target, List.of()).contains(candidate);
    }

    private static void refresh() {
        boolean en = Configs.Print.SUBSTITUTE_PLACEMENT.getBooleanValue();
        List<String> cur = Configs.Print.SUBSTITUTE_LIST.getStrings();
        if (en != enabled || !cur.equals(source)) {
            enabled = en;
            source = List.copyOf(cur);
            substitutesByTarget.clear();
            if (en) {
                for (String line : cur) {
                    parseLine(line);
                }
            }
        }
    }

    private static void parseLine(String line) {
        if (line == null || line.isBlank()) {
            return;
        }
        String normalized = line.replace('，', ',').replace('；', ';').replace('：', ':');
        for (String rule : normalized.split(";")) {
            rule = rule.trim();
            if (rule.isEmpty()) {
                continue;
            }
            int sep = rule.lastIndexOf(':');
            if (sep <= 0 || sep == rule.length() - 1) {
                continue;
            }
            String targetToken = rule.substring(sep + 1).trim();
            Block target = resolveStrict(targetToken);
            if (target == null || target == Blocks.AIR) {
                continue;
            }
            Set<Block> subs = new LinkedHashSet<>();
            boolean valid = true;
            for (String token : rule.substring(0, sep).split(",")) {
                Block sub = resolveStrict(token.trim());
                if (sub == null || sub == Blocks.AIR || sub == target) {
                    valid = false;
                    break;
                }
                subs.add(sub);
            }
            if (!valid || subs.isEmpty()) {
                continue;
            }
            List<Block> merged = new ArrayList<>(substitutesByTarget.getOrDefault(target, List.of()));
            for (Block sub : subs) {
                if (!merged.contains(sub)) {
                    merged.add(sub);
                }
            }
            substitutesByTarget.put(target, merged);
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
        } else {
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
}
