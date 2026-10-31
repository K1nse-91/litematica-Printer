package me.K1nse_.litematica.printer.config;

import com.google.common.collect.ImmutableList;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fi.dy.masa.malilib.config.*;
import fi.dy.masa.malilib.config.options.*;
import fi.dy.masa.malilib.event.InputEventHandler;
import fi.dy.masa.malilib.hotkeys.IHotkey;
import fi.dy.masa.malilib.hotkeys.KeyAction;
import fi.dy.masa.malilib.hotkeys.KeybindSettings;
import fi.dy.masa.malilib.util.data.json.JsonUtils;
import fi.dy.masa.malilib.util.restrictions.UsageRestriction;
import fi.dy.masa.malilib.config.ConfigManager;
import me.K1nse_.litematica.printer.I18n;
import me.K1nse_.litematica.printer.Reference;
import me.K1nse_.litematica.printer.enums.*;
import me.K1nse_.litematica.printer.gui.ConfigUi;
import me.K1nse_.litematica.printer.utils.mods.ModLoadUtils;
import net.minecraft.world.level.block.Blocks;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.BooleanSupplier;

public class Configs extends ConfigBuilders implements IConfigHandler {
    private static final Configs INSTANCE = new Configs();

    private static final String FILE_PATH = "./config/" + Reference.MOD_ID + ".json";
    private static final File CONFIG_DIR = new File("./config");

    private static final KeybindSettings GUI_NO_ORDER = KeybindSettings.create(KeybindSettings.Context.GUI, KeyAction.PRESS, false, false, false, true);

    // 配置页面是否可视(函数式, 动态获取, 全局统一使用)
    private static final BooleanSupplier isSingle = () -> Core.WORK_MODE.getOptionListValue().equals(WorkingModeType.SINGLE);
    private static final BooleanSupplier isMulti = () -> Core.WORK_MODE.getOptionListValue().equals(WorkingModeType.MULTI);

    private static final BooleanSupplier isBreakCustom = () -> Break.BREAK_LIMITER.getOptionListValue().equals(ExcavateListMode.CUSTOM);
    private static final BooleanSupplier isBreakWhitelist = () -> isBreakCustom.getAsBoolean() && Break.BREAK_LIMIT.getOptionListValue().equals(UsageRestriction.ListType.WHITELIST);
    private static final BooleanSupplier isBreakBlacklist = () -> isBreakCustom.getAsBoolean() && Break.BREAK_LIMIT.getOptionListValue().equals(UsageRestriction.ListType.BLACKLIST);


    private static final BooleanSupplier isExcavateCustom = () -> Mine.EXCAVATE_LIMITER.getOptionListValue().equals(ExcavateListMode.CUSTOM);
    private static final BooleanSupplier isExcavateWhitelist = () -> isExcavateCustom.getAsBoolean() && Mine.EXCAVATE_LIMIT.getOptionListValue().equals(UsageRestriction.ListType.WHITELIST);
    private static final BooleanSupplier isExcavateBlacklist = () -> isExcavateCustom.getAsBoolean() && Mine.EXCAVATE_LIMIT.getOptionListValue().equals(UsageRestriction.ListType.BLACKLIST);
    private static final BooleanSupplier isBlocklist = () -> Fill.FILL_BLOCK_MODE.getOptionListValue().equals(FillBlockModeType.BLOCKLIST);
    // 瞬时破坏名单可见性（移植自 EMT）
    private static final BooleanSupplier isBreakVeil = () -> Break.BREAK_INSTANT_MINE.getBooleanValue();
    // 避让流体子选项可见性（移植自 EMT）
    private static final BooleanSupplier isBreakAvoidFluid = () -> Break.BREAK_AVOID_FLUID.getBooleanValue();


    public static final ImmutableList<IConfigBase> OPTIONS;
    public static final ImmutableList<IHotkey> HOTKEYS;

    /** 切换布尔配置并弹提示（移植自 EMT 的热键回调） */
    public static void toggleWithNotify(ConfigBoolean config) {
        boolean newValue = !config.getBooleanValue();
        config.setBooleanValue(newValue);
        me.K1nse_.litematica.printer.utils.minecraft.MessageUtils.setOverlayMessage(I18n.MESSAGE_TOGGLED.getName(
                config.getConfigGuiDisplayName(),
                newValue ? I18n.MESSAGE_VALUE_ON.getName() : I18n.MESSAGE_VALUE_OFF.getName()));
    }


    static {
        LinkedHashSet<IConfigBase> optionSet = new LinkedHashSet<>();
        optionSet.addAll(Core.OPTIONS);           // 核心
        optionSet.addAll(Special.OPTIONS);        // 特殊
        optionSet.addAll(Placement.OPTIONS);      // 放置
        optionSet.addAll(Break.OPTIONS);          // 破坏
        optionSet.addAll(Hotkeys.OPTIONS);        // 热键
        optionSet.addAll(Print.OPTIONS);          // 打印
        optionSet.addAll(Mine.OPTIONS);           // 挖掘
        optionSet.addAll(Fill.OPTIONS);           // 填充
        optionSet.addAll(Fluid.OPTIONS);          // 排流体
        optionSet.addAll(Go.OPTIONS);             // 自动寻路
        optionSet.addAll(Bedrock.OPTIONS);        // 破基岩
        OPTIONS = ImmutableList.copyOf(optionSet);

        List<IHotkey> hotkeys = new ArrayList<>();
        for (IConfigBase option : optionSet) {
            if (option instanceof IHotkey hokey) {
                hotkeys.add(hokey);
            }
        }
        HOTKEYS = ImmutableList.copyOf(hotkeys);
    }

    public static class Core {
        // 打印状态
        public static final ConfigBooleanHotkeyed WORK_SWITCH = booleanHotkey("workingSwitch")
                .defaultValue(false)
                .defaultHotkey("CAPS_LOCK")
                .keybindSettings(KeybindSettings.PRESS_ALLOWEXTRA_EMPTY)
                .build();

        // 核心 - 模式切换
        public static final ConfigOptionList WORK_MODE = optionList("modeSwitch")
                .defaultValue(WorkingModeType.SINGLE)
                .build();

        // 多模 - 打印
        public static final ConfigBooleanHotkeyed PRINT = booleanHotkey("print")
                .defaultValue(false)
                .setVisible(isMulti) // 仅多模式时显示
                .build();

        // 多模 - 挖掘
        public static final ConfigBooleanHotkeyed MINE = booleanHotkey("mine")
                .defaultValue(false)
                .setVisible(isMulti) // 仅多模式时显示
                .build();

        // 多模 - 填充
        public static final ConfigBooleanHotkeyed FILL = booleanHotkey("fill")
                .defaultValue(false)
                .setVisible(isMulti) // 仅多模式时显示
                .build();

        // 多模 - 排流体
        public static final ConfigBooleanHotkeyed FLUID = booleanHotkey("fluid")
                .defaultValue(false)
                .setVisible(isMulti) // 仅多模式时显示
                .build();

        // 核心 - 单模模式
        public static final ConfigOptionList WORK_MODE_TYPE = optionList("printerMode")
                .defaultValue(PrintModeType.PRINTER)
                .setVisible(isSingle) // 仅单模式时显示
                .build();

        // 核心 - 工作半径
        public static final ConfigInteger WORK_RANGE = integer("workRange")
                .defaultValue(6)
                .range(1, 256)
                .build();

        public static final ConfigInteger SCAN_TIME_BUDGET_MS = integer("scanTimeBudgetMs")
                .defaultValue(2)
                .range(1, 10)
                .build();

        public static final ConfigInteger LAZY_ENTER_TICKS = integer("lazyEnterTicks")
                .defaultValue(10)
                .range(0, 40)
                .build();

        // 核心 - 检查玩家方块交互范围
        public static final ConfigBoolean CHECK_PLAYER_INTERACTION_RANGE = bool("checkPlayerInteractionRange")
                .defaultValue(true)
                .build();

        // 核心 - 延迟检测
        public static final ConfigBoolean LAG_CHECK = bool("printerLagCheck")
                .defaultValue(false)
                .build();

        public static final ConfigInteger LAG_CHECK_MAX = integer("printerLagCheckMax")
                .defaultValue(20)
                .setVisible(LAG_CHECK::getBooleanValue)
                .range(20, 1200)
                .build();

        // 核心 - 迭代区域形状
        public static final ConfigOptionList ITERATOR_SHAPE = optionList("printerIteratorShape")
                .defaultValue(RadiusShapeType.SPHERE)
                .build();

        // 核心 - 显示打印机HUD
        public static final ConfigBoolean RENDER_HUD = bool("renderHud")
                .defaultValue(true)
                .build();

        public static final ConfigInteger RENDER_HUD_X = integer("renderHudX")
                .defaultValue(10)
                .range(0, 4096)
                .setVisible(RENDER_HUD::getBooleanValue)
                .build();

        public static final ConfigInteger RENDER_HUD_Y = integer("renderHudY")
                .defaultValue(10)
                .range(0, 4096)
                .setVisible(RENDER_HUD::getBooleanValue)
                .build();

        public static final ConfigInteger RENDER_HUD_SCALE = integer("renderHudScale")
                .defaultValue(100)
                .range(50, 200)
                .setVisible(RENDER_HUD::getBooleanValue)
                .build();

        // 核心 - 自动禁用打印机
        // 核心 - 自动启用打印机（移植自 EMT）：加载投影后自动开启工作开关
        public static final ConfigBoolean AUTO_ENABLE_PRINTER = bool("printerAutoEnable")
                .defaultValue(false)
                .build();

        public static final ConfigBoolean AUTO_DISABLE_PRINTER = bool("printerAutoDisable")
                .defaultValue(true)
                .build();

        // ---- 迭代调度（移植自 EMT）----

        // 迭代顺序
        public static final ConfigOptionList ITERATION_ORDER = optionList("printerIteratorMode")
                .defaultValue(IterationOrderType.XZY)
                .build();

        // X 轴反向迭代
        public static final ConfigBoolean X_REVERSE = bool("printerXAxisReverse")
                .defaultValue(false)
                .build();

        // Y 轴反向迭代
        public static final ConfigBoolean Y_REVERSE = bool("printerYAxisReverse")
                .defaultValue(false)
                .build();

        // Z 轴反向迭代
        public static final ConfigBoolean Z_REVERSE = bool("printerZAxisReverse")
                .defaultValue(false)
                .build();

        // 移动自适应迭代：挖掘模式按玩家移动主方向把工作范围向前延伸
        public static final ConfigBoolean MOVE_ADAPTIVE_ITERATION = bool("moveAdaptiveIteration")
                .defaultValue(true)
                .build();

        // 移动自适应延伸格数
        public static final ConfigInteger MOTION_AHEAD = integer("motionAheadBlocks")
                .defaultValue(3)
                .range(0, 16)
                .setVisible(MOVE_ADAPTIVE_ITERATION::getBooleanValue)
                .build();

        // 校验器优化（移植自 EMT）：用异步分区校验器替换原版校验器，大投影校对更快
        public static final ConfigBoolean VERIFIER_OPTIMIZED = bool("verifierOptimized")
                .defaultValue(false)
                .build();

        // 核心 - 检查更新
        public static final ConfigBoolean UPDATE_CHECK = bool("updateCheck")
                .defaultValue(true)
                .build();

        // 通用配置项列表（按功能分类排序）
        public static final ImmutableList<IConfigBase> OPTIONS = ImmutableList.of(
                WORK_SWITCH,
                WORK_MODE,
                WORK_MODE_TYPE,
                PRINT,
                MINE,
                FILL,
                FLUID,
                WORK_RANGE,
                SCAN_TIME_BUDGET_MS,
                LAZY_ENTER_TICKS,
                RENDER_HUD,
                RENDER_HUD_X,
                RENDER_HUD_Y,
                RENDER_HUD_SCALE,
                LAG_CHECK,
                LAG_CHECK_MAX,
                CHECK_PLAYER_INTERACTION_RANGE,
                ITERATOR_SHAPE,
                ITERATION_ORDER,
                X_REVERSE,
                Y_REVERSE,
                Z_REVERSE,
                MOVE_ADAPTIVE_ITERATION,
                MOTION_AHEAD,
                VERIFIER_OPTIMIZED,
                AUTO_DISABLE_PRINTER,
                AUTO_ENABLE_PRINTER,
                UPDATE_CHECK
        );
    }

    public static class Special {
        // 信标效果限制绕过
        public static final ConfigBoolean UNLOCK_BEACON_EFFECTS = bool("unlockBeaconEffects")
                .defaultValue(true)
                .build();

        // Tweakeroo - 放宽凭空放置限制
        public static final ConfigBoolean TWEAKEROO_ANGEL_BLOCK_MAY_BUILD = bool("tweakerooAngelBlockMayBuild")
                .defaultValue(true)
                .setVisible(ModLoadUtils::isTweakerooLoaded)
                .build();

        // ---- 背包同步（移植自 EMT）----

        // 背包同步 - 材料检查：背包材料不足时不开始同步
        // 只渲染指定方块（移植自 EMT）：超大投影只画名单内方块
        public static final ConfigBoolean RENDER_ONLY_BLOCKS = bool("renderOnlyBlocks")
                .defaultValue(false)
                .build();

        public static final ConfigStringList RENDER_ONLY_BLOCK_LIST = stringList("renderOnlyBlockList")
                .defaultValue(List.of("minecraft:stone"))
                .setVisible(RENDER_ONLY_BLOCKS::getBooleanValue)
                .build();

        public static final ConfigBoolean SYNC_INVENTORY_CHECK = bool("syncInventoryCheck")
                .defaultValue(false)
                .build();

        // 背包同步 - 合成器槽位状态同步
        public static final ConfigBoolean SYNC_INVENTORY_CRAFTER = bool("syncInventoryCrafter")
                .defaultValue(true)
                .build();

        // 背包同步 - 单 tick 点击包上限
        public static final ConfigInteger SYNC_PACKET_LIMIT = integer("syncPacketLimit")
                .defaultValue(16)
                .range(1, 1024)
                .build();

        // 背包同步 - 高亮颜色
        public static final ConfigColor SYNC_INVENTORY_COLOR = color("syncInventoryColor")
                .defaultValue("#4CFF4CE6")
                .build();

        // 背包同步 - 高亮渲染距离
        public static final ConfigInteger SYNC_HIGHLIGHT_RENDER_DISTANCE = integer("syncHighlightRenderDistance")
                .defaultValue(64)
                .range(0, 512)
                .build();

        public static final ImmutableList<IConfigBase> OPTIONS = ImmutableList.of(
                UNLOCK_BEACON_EFFECTS,
                TWEAKEROO_ANGEL_BLOCK_MAY_BUILD,
                RENDER_ONLY_BLOCKS,
                RENDER_ONLY_BLOCK_LIST,
                SYNC_INVENTORY_CHECK,
                SYNC_INVENTORY_CRAFTER,
                SYNC_PACKET_LIMIT,
                SYNC_INVENTORY_COLOR,
                SYNC_HIGHLIGHT_RENDER_DISTANCE
        );
    }

    public static class Placement {

        // 核心 - 工作间隔
        public static final ConfigInteger PLACE_INTERVAL = integer("placeInterval")
                .defaultValue(0)
                .range(0, 20)
                .build();

        // 每刻放置方块数
        public static final ConfigInteger PLACE_BLOCKS_PER_TICK = integer("placeBlocksPerTick")
                .defaultValue(1)
                .range(0, 256)
                .build();

        // 放置冷却
        public static final ConfigInteger PLACE_COOLDOWN = integer("placeCooldown")
                .defaultValue(8)
                .range(0, 64)
                .build();

        // RTT 自适应重放间隔 - 开关
        // 根据玩家 ping 自动把放置间隔抬到不低于一次往返,减少服务器下「发包快于服务端确认」导致的放错。
        public static final ConfigBoolean RTT_ADAPTIVE_INTERVAL = bool("placeRttAdaptiveInterval")
                .defaultValue(false)
                .build();

        // 默认放置朝向（移植自 EMT）：可选面不唯一时优先使用的放置方向
        public static final ConfigOptionList PLACE_DEFAULT_DIRECTION = optionList("placeDefaultDirection")
                .defaultValue(DefaultPlaceDirectionType.NONE)
                .build();

        public static final ConfigInteger RTT_SAFETY_PERCENT = integer("placeRttSafetyPercent")
                .defaultValue(100)
                .range(25, 300)
                .setVisible(RTT_ADAPTIVE_INTERVAL::getBooleanValue)
                .build();

        // 下落方块检查
        public static final ConfigBoolean FALLING_CHECK = bool("printFallingBlockCheck")
                .defaultValue(true)
                .build();

        // 快捷潜影盒 - 开关
        public static final ConfigBoolean QUICK_SHULKER = bool("quickShulker")
                .defaultValue(false)
                .build();

        // 手部补货：手持物品用完后自动从潜影盒补回原槽位（移植自 EMT）
        public static final ConfigBoolean HAND_RESTOCK_SHULKER_COMPAT = bool("handRestockShulkerCompat")
                .defaultValue(false)
                .setVisible(QUICK_SHULKER::getBooleanValue)
                .build();

        // 快捷潜影盒 - 工作模式
        public static final ConfigOptionList QUICK_SHULKER_MODE = optionList("quickShulkerMode")
                .defaultValue(QuickShulkerModeType.INVOKE)
                .build();

        // 快捷潜影盒 - 冷却时间
        public static final ConfigInteger QUICK_SHULKER_COOLDOWN = integer("quickShulkerCooldown")
                .defaultValue(1)
                .range(0, 20)
                .build();

        // 储存管理 - 有序存放
        public static final ConfigBoolean STORE_ORDERLY = bool("storeOrderly")
                .defaultValue(false)
                .build();

        public static final ImmutableList<IConfigBase> OPTIONS = ImmutableList.of(
                PLACE_INTERVAL,
                PLACE_BLOCKS_PER_TICK,
                PLACE_COOLDOWN,
                PLACE_DEFAULT_DIRECTION,
                RTT_ADAPTIVE_INTERVAL,
                RTT_SAFETY_PERCENT,
                FALLING_CHECK,
                STORE_ORDERLY,
                QUICK_SHULKER,
                HAND_RESTOCK_SHULKER_COMPAT,
                QUICK_SHULKER_MODE,
                QUICK_SHULKER_COOLDOWN
        );
    }

    public static class Break {
        public static final ConfigBoolean BREAK_USE_DELAYED_DESTROY = bool("breakUseDelayedDestroy")
                .defaultValue(false)
                .build();

        public static final ConfigInteger BREAK_PROGRESS_THRESHOLD = integer("breakProgressThreshold")
                .defaultValue(100)
                .range(70, 100)
                .build();

        public static final ConfigInteger BREAK_INTERVAL = integer("breakInterval")
                .defaultValue(0)
                .range(0, 20)
                .build();

        public static final ConfigInteger BREAK_BLOCKS_PER_TICK = integer("breakBlocksPerTick")
                .defaultValue(20)
                .range(0, 20)
                .build();

        public static final ConfigInteger BREAK_COOLDOWN = integer("breakCooldown")
                .defaultValue(8)
                .range(0, 64)
                .build();

        public static final ConfigBoolean BREAK_CHECK_HARDNESS = bool("breakCheckHardness")
                .defaultValue(true)
                .build();

        public static final ConfigBoolean BREAK_AUTO_TOOL = bool("breakAutoTool")
                .defaultValue(false)
                .build();

        // ---- 破坏增强（移植自 EMT）----

        // 纯发包破坏：直接发送 START/STOP 破坏包，跳过本地预测（瞬时破坏名单的前置开关）
        public static final ConfigBoolean BREAK_USE_PACKET = bool("breakUsePacket")
                .defaultValue(false)
                .build();

        // 破坏声音
        public static final ConfigBoolean BREAK_SOUND = bool("breakSound")
                .defaultValue(true)
                .build();

        // 瞬时破坏名单 - 开关
        public static final ConfigBoolean BREAK_INSTANT_MINE = bool("breakVeilToggle")
                .defaultValue(false)
                .build();

        // 瞬时破坏名单
        public static final ConfigStringList BREAK_INSTANT_MINE_LIST = stringList("breakVeilList")
                .setVisible(isBreakVeil)
                .build();

        // 并行挖掘：收集候选后按同一把工具批量连挖；关闭时逐个方块直接挖掘
        public static final ConfigBoolean BREAK_PARALLEL = bool("breakParallel")
                .defaultValue(true)
                .build();

        // 避让流体
        public static final ConfigBoolean BREAK_AVOID_FLUID = bool("breakAvoidFluid")
                .defaultValue(false)
                .build();

        // 避让流体名单
        public static final ConfigStringList BREAK_FLUID_LIST = stringList("breakFluidList")
                .defaultValue(Blocks.WATER, Blocks.LAVA)
                .setVisible(isBreakAvoidFluid)
                .build();

        // 避让流体策略
        public static final ConfigOptionList BREAK_FLUID_STRATEGY = optionList("breakFluidStrategy")
                .defaultValue(FluidAvoidStrategyType.FIVE_FACES)
                .setVisible(isBreakAvoidFluid)
                .build();

        // 避让支撑方块
        public static final ConfigBoolean BREAK_AVOID_SUPPORT = bool("breakAvoidSupport")
                .defaultValue(false)
                .build();

        // 让位手动挖掘
        public static final ConfigBoolean BREAK_NON_BLOCKING = bool("breakNonBlocking")
                .defaultValue(false)
                .build();

        // 模式限制器
        public static final ConfigOptionList BREAK_LIMITER = optionList("breakLimiter")
                .defaultValue(ExcavateListMode.CUSTOM)
                .build();

        // 模式限制
        public static final ConfigOptionList BREAK_LIMIT = optionList("breakLimit")
                .defaultValue(UsageRestriction.ListType.NONE)
                .setVisible(isBreakCustom)
                .build();

        // 白名单
        public static final ConfigStringList BREAK_WHITELIST = stringList("breakWhitelist")
                .setVisible(isBreakWhitelist)
                .build();

        // 黑名单
        public static final ConfigStringList BREAK_BLACKLIST = stringList("breakBlacklist")
                .setVisible(isBreakBlacklist)
                .build();

        public static final ImmutableList<IConfigBase> OPTIONS = ImmutableList.of(
                BREAK_CHECK_HARDNESS,
                BREAK_AUTO_TOOL,
                BREAK_USE_DELAYED_DESTROY,
                BREAK_COOLDOWN,
                BREAK_PROGRESS_THRESHOLD,
                BREAK_USE_PACKET,
                BREAK_SOUND,
                BREAK_INSTANT_MINE,
                BREAK_INSTANT_MINE_LIST,
                BREAK_PARALLEL,
                BREAK_AVOID_FLUID,
                BREAK_FLUID_LIST,
                BREAK_FLUID_STRATEGY,
                BREAK_AVOID_SUPPORT,
                BREAK_NON_BLOCKING,
                // 限制器
                BREAK_LIMITER,
                BREAK_LIMIT,
                BREAK_WHITELIST,
                BREAK_BLACKLIST
        );
    }

    public static class Bedrock {
        public static final ConfigInteger BEDROCK_INTERVAL = integer("bedrockInterval")
                .defaultValue(2)
                .range(1, 20)
                .build();

        public static final ConfigInteger BEDROCK_BLOCKS_PER_TICK = integer("bedrockBlocksPerTick")
                .defaultValue(6)
                .range(1, 8)
                .build();

        public static final ConfigBoolean BEDROCK_ALLOW_SIDE = bool("bedrockAllowSide")
                .defaultValue(false)
                .build();

        // 多人破基岩自适应：按 RTT 与服务端方块更新延迟，延长确认宽限并退避重试
        public static final ConfigBoolean BEDROCK_MULTIPLAYER_ADAPTIVE = bool("bedrockMultiplayerAdaptive")
                .defaultValue(true)
                .build();

        // 外部破基岩（移植自 EMT）：把目标交给 bedrockminer/blockminer 模组执行
        public static final ConfigBoolean BEDROCK_EXTERNAL_MINER = bool("bedrockExternalMiner")
                .defaultValue(false)
                .build();

        public static final ConfigStringList BEDROCK_WHITELIST = stringList("bedrockWhitelist")
                .build();

        public static final ImmutableList<IConfigBase> OPTIONS = ImmutableList.of(
                BEDROCK_INTERVAL,
                BEDROCK_BLOCKS_PER_TICK,
                BEDROCK_ALLOW_SIDE,
                BEDROCK_MULTIPLAYER_ADAPTIVE,
                BEDROCK_EXTERNAL_MINER,
                BEDROCK_WHITELIST
        );
    }

    public static class Print {
        // 选区类型
        public static final ConfigOptionList PRINT_SELECTION_TYPE = optionList("printSelectionType")
                .defaultValue(SelectionType.LITEMATICA_RENDER_LAYER)
                .build();

        // 投影轻松放置协议
        public static final ConfigBoolean EASY_PLACE_PROTOCOL = bool("easyPlaceProtocol")
                .defaultValue(false)
                .build();

        // 凭空放置
        public static final ConfigBoolean PLACE_IN_AIR = bool("placeInAir")
                .defaultValue(true)
                .build();

        // 纯发包放置(数据包打印):仅打印模式，跳过原版放置流程与本地预测，直接发 UseItemOn 包，
        // 方块由服务端确认后才出现。可消除本地预测造成的幽灵方块，代价是方块延迟一拍显示。
        public static final ConfigBoolean PRINT_USE_PACKET = bool("printUsePacket")
                .defaultValue(false)
                .build();

        // 放置声音（移植自 EMT）：纯发包放置时经声音包确认后播放；普通放置控制本地音效开关
        public static final ConfigBoolean PRINT_SOUND = bool("printSound")
                .defaultValue(true)
                .build();

        // ---- 打印增强（移植自 EMT）----

        // 预留物品：放置时保留背包里的最后 N 个同类物品，避免材料被放光
        public static final ConfigBoolean RESERVE_ITEMS = bool("printReserveItems")
                .defaultValue(false)
                .build();

        // 预留数量
        public static final ConfigInteger RESERVE_ITEM_COUNT = integer("printReserveItemCount")
                .defaultValue(1)
                .range(0, 64)
                .setVisible(RESERVE_ITEMS::getBooleanValue)
                .build();

        // 切换物品间隔（tick）：换物品后至少等这么久再放
        public static final ConfigInteger ITEM_SWITCH_INTERVAL = integer("itemSwitchInterval")
                .defaultValue(0)
                .range(0, 10)
                .build();

        // 同物品优先：优先放当前手持物品对应的方块
        public static final ConfigBoolean PLACE_SAME_ITEM_FIRST = bool("placeSameItemFirst")
                .defaultValue(true)
                .build();

        // 跳过潜影盒方块
        public static final ConfigBoolean SKIP_SHULKER = bool("printSkipShulker")
                .defaultValue(false)
                .build();

        // 仅使用空潜影盒放置
        public static final ConfigBoolean ONLY_EMPTY_SHULKER = bool("printOnlyEmptyShulker")
                .defaultValue(false)
                .build();

        // 潜影盒后置：附近还有普通方块待放时先放普通方块
        public static final ConfigBoolean SHULKER_AFTER_ORDINARY = bool("printShulkerAfterOrdinary")
                .defaultValue(false)
                .build();

        // 破坏状态错误的方块（同方块但状态不同且可修复时）
        public static final ConfigBoolean BREAK_WRONG_STATE_BLOCK = bool("printBreakWrongStateBlock")
                .defaultValue(false)
                .build();

        // 炼药锅填充（水/岩浆/细雪）
        public static final ConfigBoolean FILL_CAULDRON = bool("printFillCauldron")
                .defaultValue(false)
                .build();

        // 快速定向放置：移动时把工作盒向移动方向延伸（配合 motionAheadBlocks）
        public static final ConfigBoolean FAST_DIRECTIONAL_PLACEMENT = bool("printFastDirectionalPlacement")
                .defaultValue(false)
                .build();

        // 打印目标排序
        public static final ConfigBoolean PRINT_SORT_TARGETS = bool("printSortTargets")
                .defaultValue(false)
                .build();

        // 放置面排序
        public static final ConfigBoolean PRINT_SORT_SIDES = bool("printSortSides")
                .defaultValue(false)
                .build();

        // 铁轨形态修复
        public static final ConfigBoolean REPAIR_RAIL_SHAPE = bool("printRepairRailShape")
                .defaultValue(false)
                .build();

        // 跳过放置
        public static final ConfigBoolean PRINT_SKIP = bool("printSkip")
                .defaultValue(false)
                .build();

        // 跳过放置名单
        public static final ConfigStringList PRINT_SKIP_LIST = stringList("printSkipList")
                .build();

        // 始终潜行
        public static final ConfigBoolean PRINT_FORCED_SNEAK = bool("printForcedSneak")
                .defaultValue(false)
                .build();

        // 覆盖打印

        // 打印扫描白名单（移植自 EMT）
        public static final ConfigBoolean PRINT_SCAN_WHITELIST = bool("printScanWhitelist")
                .defaultValue(false)
                .build();

        // 打印扫描白名单列表
        public static final ConfigStringList PRINT_SCAN_WHITELIST_LIST = stringList("printScanWhitelistList")
                .setVisible(PRINT_SCAN_WHITELIST::getBooleanValue)
                .build();
        public static final ConfigBoolean PRINT_REPLACE = bool("printReplace")
                .defaultValue(true)
                .build();

        // 覆盖方块列表
        public static final ConfigStringList REPLACEABLE_LIST = stringList("printReplaceableList")
                .defaultValue(Blocks.SNOW, Blocks.LAVA, Blocks.WATER, Blocks.BUBBLE_COLUMN, Blocks.SHORT_GRASS)
                .build();

        // 跳过含水方块
        public static final ConfigBoolean SKIP_WATERLOGGED_BLOCK = bool("printSkipWaterlogged")
                .defaultValue(false)
                .build();

        // 替换珊瑚
        public static final ConfigBoolean REPLACE_CORAL = bool("printReplaceCoral")
                .defaultValue(false)
                .build();

        // ---- 材料替代放置（移植自 EMT）----

        // 材料替代放置 - 开关
        public static final ConfigBoolean SUBSTITUTE_PLACEMENT = bool("printSubstitutePlacement")
                .defaultValue(false)
                .build();

        // 材料替代放置 - 名单（格式：替代1,替代2:目标）
        public static final ConfigStringList SUBSTITUTE_LIST = stringList("printSubstituteList")
                .setVisible(SUBSTITUTE_PLACEMENT::getBooleanValue)
                .build();

        // 破冰放水
        public static final ConfigBooleanHotkeyed PRINT_ICE_FOR_WATER = booleanHotkey("printIceForWater")
                .defaultValue(false)
                .build();

        // 自动去皮
        public static final ConfigBoolean STRIP_LOGS = bool("printAutoStripLogs")
                .defaultValue(false)
                .build();

        // 音符盒自动调音
        public static final ConfigBoolean NOTE_BLOCK_TUNING = bool("printAutoTuning")
                .defaultValue(true)
                .build();

        // 侦测器安全放置
        public static final ConfigBoolean SAFELY_OBSERVER = bool("printSafelyObserver")
                .defaultValue(true)
                .build();

        // 堆肥桶自动填充
        public static final ConfigBoolean FILL_COMPOSTER = bool("printAutoFillComposter")
                .defaultValue(false)
                .build();

        // 堆肥桶白名单
        public static final ConfigStringList FILL_COMPOSTER_WHITELIST = stringList("printAutoFillComposterWhitelist")
                .setVisible(FILL_COMPOSTER::getBooleanValue)
                .build();

        // 农作物催熟
        public static final ConfigBoolean BONEMEAL_CROPS = bool("printBonemealCrops")
                .defaultValue(false)
                .build();

        // 农作物催熟连点次数
        public static final ConfigInteger BONEMEAL_CROPS_CLICKS = integer("printBonemealCropsClicks")
                .defaultValue(10)
                .range(1, 32)
                .setVisible(BONEMEAL_CROPS::getBooleanValue)
                .build();

        // 破坏错误方块
        public static final ConfigBoolean BREAK_WRONG_BLOCK = bool("printBreakWrongBlock")
                .defaultValue(false)
                .build();

        // 破坏多余方块
        public static final ConfigBoolean BREAK_EXTRA_BLOCK = bool("printBreakExtraBlock")
                .defaultValue(false)
                .build();

        public static final ImmutableList<IConfigBase> OPTIONS = ImmutableList.of(
                PRINT_SELECTION_TYPE,
                EASY_PLACE_PROTOCOL,
                PLACE_IN_AIR,
                PRINT_USE_PACKET,
                PRINT_SOUND,
                RESERVE_ITEMS,
                RESERVE_ITEM_COUNT,
                ITEM_SWITCH_INTERVAL,
                PLACE_SAME_ITEM_FIRST,
                SKIP_SHULKER,
                ONLY_EMPTY_SHULKER,
                SHULKER_AFTER_ORDINARY,
                BREAK_WRONG_STATE_BLOCK,
                FILL_CAULDRON,
                FAST_DIRECTIONAL_PLACEMENT,
                PRINT_SORT_TARGETS,
                PRINT_SORT_SIDES,
                REPAIR_RAIL_SHAPE,
                PRINT_FORCED_SNEAK,
                BREAK_WRONG_BLOCK,
                BREAK_EXTRA_BLOCK,
                PRINT_SKIP,
                PRINT_SKIP_LIST,
                PRINT_SCAN_WHITELIST,
                PRINT_SCAN_WHITELIST_LIST,
                PRINT_REPLACE,
                REPLACEABLE_LIST,
                SKIP_WATERLOGGED_BLOCK,
                PRINT_ICE_FOR_WATER,
                SAFELY_OBSERVER,
                STRIP_LOGS,
                NOTE_BLOCK_TUNING,
                REPLACE_CORAL,
                SUBSTITUTE_PLACEMENT,
                SUBSTITUTE_LIST,
                FILL_COMPOSTER,
                FILL_COMPOSTER_WHITELIST,
                BONEMEAL_CROPS
                , BONEMEAL_CROPS_CLICKS
        );
    }

    public static class Mine {
        // 选区类型
        public static final ConfigOptionList MINE_SELECTION_TYPE = optionList("mineSelectionType")
                .defaultValue(SelectionType.LITEMATICA_SELECTION)
                .build();

        // 挖沟模式：挖掘前先用沙子填充直线选区及两侧紧邻方块中的流体
        public static final ConfigBoolean MINE_TRENCH_MODE = bool("mineTrenchMode")
                .defaultValue(false)
                .build();

        // 挖掘模式限制器
        public static final ConfigOptionList EXCAVATE_LIMITER = optionList("excavateLimiter")
                .defaultValue(ExcavateListMode.CUSTOM)
                .build();

        // 挖掘模式限制
        public static final ConfigOptionList EXCAVATE_LIMIT = optionList("excavateLimit")
                .defaultValue(UsageRestriction.ListType.NONE)
                .setVisible(isExcavateCustom)
                .build();

        // 挖掘白名单
        public static final ConfigStringList EXCAVATE_WHITELIST = stringList("excavateWhitelist")
                .setVisible(isExcavateWhitelist)
                .build();

        // 挖掘黑名单
        public static final ConfigStringList EXCAVATE_BLACKLIST = stringList("excavateBlacklist")
                .setVisible(isExcavateBlacklist)
                .build();

        public static final ImmutableList<IConfigBase> OPTIONS = ImmutableList.of(
                MINE_SELECTION_TYPE,          // 挖掘 - 选区类型
                MINE_TRENCH_MODE,             // 挖掘 - 挖沟模式
                EXCAVATE_LIMITER,             // 挖掘 - 挖掘模式限制器
                EXCAVATE_LIMIT,               // 挖掘 - 挖掘模式限制
                EXCAVATE_WHITELIST,           // 挖掘 - 挖掘白名单
                EXCAVATE_BLACKLIST            // 挖掘 - 挖掘黑名单
        );
    }

    public static class Fill {
        // 选区类型
        public static final ConfigOptionList FILL_SELECTION_TYPE = optionList("fillSelectionType")
                .defaultValue(SelectionType.LITEMATICA_SELECTION)
                .build();

        // 填充方块模式
        public static final ConfigOptionList FILL_BLOCK_MODE = optionList("fillBlockMode")
                .defaultValue(FillBlockModeType.BLOCKLIST)
                .build();

        // 填充方块名单
        public static final ConfigStringList FILL_BLOCK_LIST = stringList("fillBlockList")
                .defaultValue(Blocks.COBBLESTONE)
                .setVisible(isBlocklist)
                .build();

        // 模式朝向
        public static final ConfigOptionList FILL_BLOCK_FACING = optionList("fillModeFacing")
                .defaultValue(FillModeFacingType.NONE)
                .build();

        public static final ImmutableList<IConfigBase> OPTIONS = ImmutableList.of(
                FILL_SELECTION_TYPE,          // 填充 - 选区类型
                FILL_BLOCK_MODE,              // 填充 - 填充方块模式
                FILL_BLOCK_LIST,              // 填充 - 填充方块名单
                FILL_BLOCK_FACING             // 填充 - 模式朝向
        );
    }

    public static class Fluid {

        // 选区类型
        public static final ConfigOptionList FLUID_SELECTION_TYPE = optionList("fluidSelectionType")
                .defaultValue(SelectionType.LITEMATICA_SELECTION)
                .build();

        // 填充流动液体
        public static final ConfigBoolean FILL_FLOWING_FLUID = bool("fluidModeFillFlowing")
                .defaultValue(true)
                .build();

        // 方块名单
        public static final ConfigStringList FLUID_REPLACE_BLOCK_LIST = stringList("fluidReplaceBlockList")
                .defaultValue(Blocks.SAND)
                .build();

        // 液体名单
        public static final ConfigStringList FLUID_LIST = stringList("fluidList")
                .defaultValue(Blocks.WATER, Blocks.LAVA)
                .build();

        public static final ImmutableList<IConfigBase> OPTIONS = ImmutableList.of(
                FLUID_SELECTION_TYPE,         // 排流体 - 选区类型
                FILL_FLOWING_FLUID,           // 排流体 - 填充流动液体
                FLUID_REPLACE_BLOCK_LIST,             // 排流体 - 方块名单
                FLUID_LIST                    // 排流体 - 液体名单
        );
    }

    // ---- 自动寻路（移植自 EMT，MC 26.1+ 生效）----
    public static class Go {
        // 打印扫描自动寻路：扫描到待处理目标时自动走过去
        public static final ConfigBoolean PRINT_SCAN_AUTOWALK = bool("printScanAutoWalk")
                .defaultValue(false)
                .build();

        // 恶魂寻路：允许骑乘快乐恶魂进行空中寻路
        public static final ConfigBoolean GHAST_PATHFIND = bool("ghastPathfind")
                .defaultValue(false)
                .build();

        // 优先取最近目标
        public static final ConfigBoolean PATH_NEAREST_TARGET = bool("pathNearestTarget")
                .defaultValue(true)
                .build();

        // 扫描额外方块
        public static final ConfigBoolean GO_SCAN_EXTRA_BLOCKS = bool("goScanExtraBlocks")
                .defaultValue(false)
                .build();

        // 扫描错误方块
        public static final ConfigBoolean GO_SCAN_WRONG_BLOCKS = bool("goScanWrongBlocks")
                .defaultValue(false)
                .build();

        // 目标候选上限
        public static final ConfigInteger PATH_TARGET_CANDIDATE_LIMIT = integer("pathTargetCandidateLimit")
                .defaultValue(96)
                .range(1, 512)
                .build();

        // 寻路扫描白名单开关
        public static final ConfigBoolean WALK_SCAN_WHITELIST = bool("walkScanWhitelist")
                .defaultValue(false)
                .build();

        // 寻路扫描白名单
        public static final ConfigStringList WALK_SCAN_WHITELIST_LIST = stringList("walkScanWhitelistList")
                .setVisible(WALK_SCAN_WHITELIST::getBooleanValue)
                .build();

        // 扫描区块顺序
        public static final ConfigOptionList PRINT_SCAN_SECTION_ORDER = optionList("printScanSectionOrder")
                .defaultValue(SectionScanOrderType.ZYX)
                .build();

        // 扫描 X 轴反向
        public static final ConfigBoolean PRINT_SCAN_X_REVERSE = bool("printScanXReverse")
                .defaultValue(false)
                .build();

        // 扫描 Y 轴反向
        public static final ConfigBoolean PRINT_SCAN_Y_REVERSE = bool("printScanYReverse")
                .defaultValue(false)
                .build();

        // 扫描 Z 轴反向
        public static final ConfigBoolean PRINT_SCAN_Z_REVERSE = bool("printScanZReverse")
                .defaultValue(false)
                .build();

        // 寻路时间上限
        public static final ConfigInteger GO_TIME_LIMIT = integer("goTimeLimit")
                .defaultValue(500)
                .range(100, 2000)
                .build();

        // 最大跌落高度
        public static final ConfigInteger GO_MAX_FALL = integer("goMaxFall")
                .defaultValue(3)
                .range(1, 10)
                .build();

        // 代价上限系数
        public static final ConfigInteger GO_COST_LIMIT_FACTOR = integer("goCostLimitFactor")
                .defaultValue(8)
                .range(0, 32)
                .build();

        // 启发式权重
        public static final ConfigDouble GO_HEURISTIC_WEIGHT = doubleValue("goHeuristicWeight")
                .defaultValue(1.0D)
                .range(1.0D, 10.0D)
                .build();

        // 恶魂正交移动代价
        public static final ConfigDouble GO_GHAST_COST_ORTHO = doubleValue("goGhastCostOrtho")
                .defaultValue(1.0D)
                .range(0.1D, 10.0D)
                .build();

        // 恶魂二维对角移动代价
        public static final ConfigDouble GO_GHAST_COST_DIAG2 = doubleValue("goGhastCostDiag2")
                .defaultValue(1.41421356D)
                .range(0.1D, 10.0D)
                .build();

        // 恶魂三维对角移动代价
        public static final ConfigDouble GO_GHAST_COST_DIAG3 = doubleValue("goGhastCostDiag3")
                .defaultValue(1.7320508D)
                .range(0.1D, 10.0D)
                .build();

        // 恶魂上升代价倍率
        public static final ConfigInteger GO_GHAST_ASCEND_MULT = integer("goGhastAscendMult")
                .defaultValue(2)
                .range(1, 32)
                .build();

        // 恶魂下降代价倍率
        public static final ConfigInteger GO_GHAST_DESCEND_MULT = integer("goGhastDescendMult")
                .defaultValue(2)
                .range(1, 32)
                .build();

        // 恶魂贴墙惩罚
        public static final ConfigDouble GO_GHAST_WALL_PENALTY = doubleValue("goGhastWallPenalty")
                .defaultValue(6.0D)
                .range(0.0D, 64.0D)
                .build();

        // 恶魂垂直延迟权重
        public static final ConfigDouble GO_GHAST_VERT_LATE_WEIGHT = doubleValue("goGhastVertLateWeight")
                .defaultValue(1.0D)
                .range(0.0D, 16.0D)
                .build();

        // 恶魂路点超时
        public static final ConfigDouble GO_GHAST_WAYPOINT_TIMEOUT = doubleValue("goGhastWaypointTimeout")
                .defaultValue(15.0D)
                .range(0.0D, 120.0D)
                .build();

        // 恶魂转向惩罚
        public static final ConfigDouble GO_GHAST_TURN_PENALTY = doubleValue("goGhastTurnPenalty")
                .defaultValue(0.0D)
                .range(0.0D, 10.0D)
                .build();

        // 步行代价
        public static final ConfigDouble GO_WALK_COST = doubleValue("goWalkCost")
                .defaultValue(4.633D)
                .range(0.1D, 60.0D)
                .build();

        // 斜走代价
        public static final ConfigDouble GO_WALK_DIAGONAL_COST = doubleValue("goWalkDiagonalCost")
                .defaultValue(6.552D)
                .range(0.1D, 60.0D)
                .build();

        // 跳跃上移代价
        public static final ConfigDouble GO_JUMP_UP_COST = doubleValue("goJumpUpCost")
                .defaultValue(9.633D)
                .range(0.1D, 60.0D)
                .build();

        // 涉水代价
        public static final ConfigDouble GO_WATER_COST = doubleValue("goWaterCost")
                .defaultValue(9.091D)
                .range(0.1D, 60.0D)
                .build();

        // 爬梯代价
        public static final ConfigDouble GO_LADDER_COST = doubleValue("goLadderCost")
                .defaultValue(8.511D)
                .range(0.1D, 60.0D)
                .build();

        // 出梯代价
        public static final ConfigDouble GO_LADDER_EXIT_COST = doubleValue("goLadderExitCost")
                .defaultValue(12.511D)
                .range(0.1D, 60.0D)
                .build();

        // 跑酷代价
        public static final ConfigDouble GO_PARKOUR_COST = doubleValue("goParkourCost")
                .defaultValue(12.0D)
                .range(0.0D, 60.0D)
                .build();

        // 疾跑代价
        public static final ConfigDouble GO_SPRINT_COST = doubleValue("goSprintCost")
                .defaultValue(3.564D)
                .range(0.1D, 60.0D)
                .build();

        // 最大速度
        public static final ConfigDouble GO_MAX_SPEED = doubleValue("goMaxSpeed")
                .defaultValue(5.7D)
                .range(0.5D, 10.0D)
                .build();

        // 强制疾跑
        public static final ConfigBoolean GO_FORCE_SPRINT = bool("goForceSprint")
                .defaultValue(false)
                .build();

        // 接管视角
        public static final ConfigBoolean GO_TAKEOVER_VIEW = bool("goTakeoverView")
                .defaultValue(false)
                .build();

        // 视角偏移
        public static final ConfigInteger GO_VIEW_OFFSET = integer("goViewOffset")
                .defaultValue(0)
                .range(-180, 180)
                .build();

        // 偏离停机
        public static final ConfigBoolean GO_DEVIATION_STOP = bool("goDeviationStop")
                .defaultValue(true)
                .build();

        // 偏离距离
        public static final ConfigInteger GO_DEVIATION_DISTANCE = integer("goDeviationDistance")
                .defaultValue(1)
                .range(1, 16)
                .build();

        // 路点超时
        public static final ConfigDouble GO_WAYPOINT_TIMEOUT = doubleValue("goWaypointTimeout")
                .defaultValue(30.0D)
                .range(0.0D, 600.0D)
                .build();

        public static final ImmutableList<IConfigBase> OPTIONS = ImmutableList.of(
                PRINT_SCAN_AUTOWALK,
                GHAST_PATHFIND,
                PATH_NEAREST_TARGET,
                GO_SCAN_EXTRA_BLOCKS,
                GO_SCAN_WRONG_BLOCKS,
                PATH_TARGET_CANDIDATE_LIMIT,
                WALK_SCAN_WHITELIST,
                WALK_SCAN_WHITELIST_LIST,
                PRINT_SCAN_SECTION_ORDER,
                PRINT_SCAN_X_REVERSE,
                PRINT_SCAN_Y_REVERSE,
                PRINT_SCAN_Z_REVERSE,
                GO_TIME_LIMIT,
                GO_MAX_FALL,
                GO_COST_LIMIT_FACTOR,
                GO_HEURISTIC_WEIGHT,
                GO_GHAST_COST_ORTHO,
                GO_GHAST_COST_DIAG2,
                GO_GHAST_COST_DIAG3,
                GO_GHAST_ASCEND_MULT,
                GO_GHAST_DESCEND_MULT,
                GO_GHAST_WALL_PENALTY,
                GO_GHAST_VERT_LATE_WEIGHT,
                GO_GHAST_WAYPOINT_TIMEOUT,
                GO_GHAST_TURN_PENALTY,
                GO_WALK_COST,
                GO_WALK_DIAGONAL_COST,
                GO_JUMP_UP_COST,
                GO_WATER_COST,
                GO_LADDER_COST,
                GO_LADDER_EXIT_COST,
                GO_PARKOUR_COST,
                GO_SPRINT_COST,
                GO_MAX_SPEED,
                GO_FORCE_SPRINT,
                GO_TAKEOVER_VIEW,
                GO_VIEW_OFFSET,
                GO_DEVIATION_STOP,
                GO_DEVIATION_DISTANCE,
                GO_WAYPOINT_TIMEOUT
        );
    }

    public static class Hotkeys {
        // 打开设置菜单
        public static final ConfigHotkey OPEN_SCREEN = hotkey("openScreen")
                .defaultStorageString("Z,Y")
                .build();

        // 关闭全部模式
        public static final ConfigHotkey CLOSE_ALL_MODE = hotkey("closeAllMode")
                .defaultStorageString("LEFT_CONTROL,G")
                .build();

        // 切换模式
        public static final ConfigHotkey SWITCH_PRINTER_MODE = hotkey("switchPrinterMode")
                .bindConfig(Core.WORK_MODE_TYPE)
                .setVisible(isSingle) // 仅单模式时显示
                .build();

        // 破基岩
        public static final ConfigBooleanHotkeyed BEDROCK = booleanHotkey("bedrock")
                .defaultValue(false)
                .setVisible(isMulti) // 仅多模式时显示
                .build();

        // 拖动 HUD 位置（按住热键移动鼠标，松开保存）
        public static final ConfigHotkey HUD_DRAG = hotkey("hudDrag")
                .defaultStorageString("F9")
                .keybindSettings(fi.dy.masa.malilib.hotkeys.KeybindSettings.INGAME_BOTH)
                .build();

        // 背包同步（移植自 EMT）：对准容器按热键，把选区内同名容器同步成一致内容
        public static final ConfigHotkey SYNC_INVENTORY = hotkey("syncInventory")
                .build();

        // 扫描自动寻路（移植自 EMT）：切换自动走向下一个待处理目标
        public static final ConfigHotkey SCAN_AUTOWALK_HOTKEY = hotkey("scanAutoWalkHotkey")
                .keybindCallback((action, key) -> { Configs.toggleWithNotify(Configs.Go.PRINT_SCAN_AUTOWALK); return true; })
                .build();

        public static final ImmutableList<IConfigBase> OPTIONS = ImmutableList.of(
                OPEN_SCREEN,                  // 打开设置菜单
                Core.WORK_SWITCH,
                CLOSE_ALL_MODE,               // 关闭全部模式
                SWITCH_PRINTER_MODE,          // 切换模式
                HUD_DRAG,                     // 拖动HUD位置
                SYNC_INVENTORY,               // 背包同步
                SCAN_AUTOWALK_HOTKEY,         // 扫描自动寻路

                // 多模
                Core.PRINT,
                Core.MINE,                // 挖掘
                Core.FILL,                    // 填充
                Core.FLUID,                  // 排流体
                BEDROCK                       // 破基岩
        );
    }

    @Override
    public void load() {
        File settingFile = new File(FILE_PATH);
        if (settingFile.isFile() && settingFile.exists()) {
            //#if MC >= 260100
            JsonElement jsonElement = JsonUtils.parseJsonFile(settingFile.toPath());
            //#else
            //$$ JsonElement jsonElement = JsonUtils.parseJsonFile(settingFile);
            //#endif
            if (jsonElement != null && jsonElement.isJsonObject()) {
                JsonObject obj = jsonElement.getAsJsonObject();
                ConfigUtils.readConfigBase(obj, Reference.MOD_ID, OPTIONS);
            }
        }
    }

    @Override
    public void save() {
        File settingFile = new File(FILE_PATH);
        if ((CONFIG_DIR.exists() && CONFIG_DIR.isDirectory()) || CONFIG_DIR.mkdirs()) {
            JsonObject configRoot = new JsonObject();
            ConfigUtils.writeConfigBase(configRoot, Reference.MOD_ID, OPTIONS);
            //#if MC >= 260100
            JsonUtils.writeJsonToFile(configRoot, settingFile.toPath());
            //#else
            //$$ JsonUtils.writeJsonToFile(configRoot, settingFile);
            //#endif
        }
    }

    public static void init() {
        Configs.INSTANCE.load();
        ConfigManager.getInstance().registerConfigHandler(Reference.MOD_ID, Configs.INSTANCE);
        InputEventHandler.getKeybindManager().registerKeybindProvider(InputHandler.getInstance());
        InputEventHandler.getInputManager().registerKeyboardInputHandler(InputHandler.getInstance());
        //#if MC > 12006
        fi.dy.masa.malilib.registry.Registry.CONFIG_SCREEN.registerConfigScreenFactory(
                new fi.dy.masa.malilib.util.data.ModInfo(Reference.MOD_ID, Reference.MOD_NAME, ConfigUi::new)
        );
        //#endif
    }

    public static void saveToFile() {
        Configs.INSTANCE.save();
    }
}
