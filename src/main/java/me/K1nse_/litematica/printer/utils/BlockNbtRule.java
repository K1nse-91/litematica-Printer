package me.K1nse_.litematica.printer.utils;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * 方块 NBT 规则（移植自 EMT）：
 * 过滤条目支持 {@code @nbt:<base64url(JSON)>} 前缀，把目标方块状态的属性条件编码进配置行，
 * 用于精确匹配带状态的方块（如 {@code state.facing=north}）。
 * 非 @nbt 条目直接作为普通名称匹配规则处理。
 */
public record BlockNbtRule(String blockMatcher, List<BlockNbtRule.Condition> conditions) {
    private static final String PREFIX = "@nbt:";
    private static final Gson GSON = new Gson();

    public BlockNbtRule {
        blockMatcher = blockMatcher == null ? "" : blockMatcher;
        List<Condition> filtered = new ArrayList<>();
        if (conditions != null) {
            for (Condition condition : conditions) {
                if (condition != null && condition.path() != null && condition.path().startsWith("state.") && !condition.path().isBlank()) {
                    filtered.add(condition);
                }
            }
        }
        conditions = List.copyOf(filtered);
    }

    public static BlockNbtRule parse(String value) {
        if (value != null && value.startsWith(PREFIX)) {
            try {
                String json = new String(Base64.getUrlDecoder().decode(value.substring(PREFIX.length())), StandardCharsets.UTF_8);
                Stored stored = GSON.fromJson(json, Stored.class);
                List<Condition> conditions = new ArrayList<>();
                if (stored != null && stored.conditions != null) {
                    for (Condition condition : stored.conditions) {
                        if (condition != null && condition.path != null && !condition.path.isBlank()) {
                            conditions.add(condition);
                        }
                    }
                }
                return new BlockNbtRule(stored == null ? "" : stored.block, conditions);
            } catch (IllegalArgumentException | JsonSyntaxException ignored) {
                return new BlockNbtRule(value, List.of());
            }
        } else {
            return new BlockNbtRule(value, List.of());
        }
    }

    public boolean hasConditions() {
        return !this.conditions.isEmpty();
    }

    public String encode() {
        return this.conditions.isEmpty()
                ? this.blockMatcher
                : PREFIX
                + Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(GSON.toJson(new Stored(this.blockMatcher, this.conditions)).getBytes(StandardCharsets.UTF_8));
    }

    public boolean matchesState(BlockState state) {
        for (Condition condition : this.conditions) {
            if (condition.path().startsWith("state.")) {
                String propertyName = condition.path().substring("state.".length());
                Property<?> property = state.getBlock().getStateDefinition().getProperty(propertyName);
                if (property == null || !matchesPropertyValue(state, property, condition.value())) {
                    return false;
                }
            }
        }
        return true;
    }

    private static <T extends Comparable<T>> boolean matchesPropertyValue(BlockState state, Property<T> property, String expected) {
        String actual = property.getName(state.getValue(property));
        return actual.equals(expected == null ? "" : expected.trim());
    }

    public record Condition(String path, String value) {
    }

    private record Stored(String block, List<Condition> conditions) {
    }
}
