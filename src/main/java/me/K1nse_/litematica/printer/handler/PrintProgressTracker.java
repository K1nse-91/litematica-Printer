package me.K1nse_.litematica.printer.handler;

import fi.dy.masa.litematica.world.SchematicWorldHandler;
import fi.dy.masa.litematica.world.WorldSchematic;
import me.K1nse_.litematica.printer.config.Configs;
import me.K1nse_.litematica.printer.enums.BlockMatchResult;
import me.K1nse_.litematica.printer.printer.PrinterBox;
import me.K1nse_.litematica.printer.printer.SchematicBlockContext;
import me.K1nse_.litematica.printer.utils.ConfigUtils;
import me.K1nse_.litematica.printer.utils.mods.LitematicaUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * 投影完成度扫描器 — 增量遍历投影放置区域，统计各模式的完成比例。
 * 快照机制：扫描过程中显示上一轮完整扫描的结果，扫完一轮才更新显示值，避免进度条"清零重涨"抽搐。
 * 节流：每 tick 1ms 预算、每轮间隔 20 tick、HUD 关闭或不扫描时零开销，避免拖慢打印/挖掘等功能。
 */
public final class PrintProgressTracker {
    public static final PrintProgressTracker INSTANCE = new PrintProgressTracker();

    private static final int SCAN_BUDGET_MS = 1;
    private static final int RESCAN_INTERVAL_TICKS = 20;

    private final Minecraft client = Minecraft.getInstance();
    private final Map<HudStatsManager.Mode, Progress> progressMap = new EnumMap<>(HudStatsManager.Mode.class);

    private List<PrinterBox> boxes = List.of();
    private int boxIndex;
    private Iterator<BlockPos> iterator;
    private boolean scanning;
    private long nextScanTick = Long.MIN_VALUE;

    private boolean trackPrint;
    private boolean trackMine;
    private boolean trackFill;
    private boolean trackFluid;
    private Item[] fillItems = new Item[0];

    private PrintProgressTracker() {
        for (HudStatsManager.Mode mode : HudStatsManager.Mode.values()) {
            this.progressMap.put(mode, new Progress());
        }
    }

    public double getProgress(HudStatsManager.Mode mode) {
        return this.progressMap.get(mode).get();
    }

    public long getFinished(HudStatsManager.Mode mode) {
        return this.progressMap.get(mode).getFinished();
    }

    public long getTotal(HudStatsManager.Mode mode) {
        return this.progressMap.get(mode).getTotal();
    }

    public void tick() {
        if (this.client.level == null || this.client.player == null) {
            return;
        }
        // HUD 显示关闭时不扫描，避免占用主线程拖慢打印/挖掘等功能
        if (!Configs.Core.RENDER_HUD.getBooleanValue()) {
            this.scanning = false;
            return;
        }
        WorldSchematic schematic = SchematicWorldHandler.getSchematicWorld();
        if (schematic == null) {
            this.scanning = false;
            return;
        }
        if (!this.scanning) {
            if (this.client.level.getGameTime() < this.nextScanTick) {
                return;
            }
            this.startScan();
            if (!this.scanning) {
                return;
            }
        }
        long deadline = System.currentTimeMillis() + SCAN_BUDGET_MS;
        while (System.currentTimeMillis() < deadline) {
            BlockPos pos = this.next();
            if (pos == null) {
                this.finishScan();
                return;
            }
            this.countPosition(schematic, pos);
        }
    }

    public void reset() {
        this.scanning = false;
        this.iterator = null;
        this.boxes = List.of();
        this.boxIndex = 0;
        this.nextScanTick = Long.MIN_VALUE;
        for (Progress progress : this.progressMap.values()) {
            progress.reset();
        }
    }

    private void startScan() {
        this.trackPrint = ConfigUtils.isPrintMode();
        this.trackMine = ConfigUtils.isMineMode();
        this.trackFill = ConfigUtils.isFillMode();
        this.trackFluid = ConfigUtils.isFluidMode();
        if (!this.trackPrint && !this.trackMine && !this.trackFill && !this.trackFluid) {
            this.scanning = false; // 没有激活的工作模式，无需统计进度
            return;
        }
        // 每轮按当前激活模式重新获取 boxes（打印=投影区域，挖掘/填充/排流体=选择区域），
        // 避免模式切换后 boxes 与模式不匹配导致进度统计不到。
        java.util.ArrayList<PrinterBox> merged = new java.util.ArrayList<>();
        if (this.trackPrint) {
            merged.addAll(LitematicaUtils.createSchematicPlacementBoxes()); // 打印：投影区域
        }
        if (this.trackMine || this.trackFill || this.trackFluid) {
            merged.addAll(LitematicaUtils.createSelection1Boxes()); // 挖掘/填充/排流体：选择区域
        }
        this.boxes = merged;
        if (this.boxes.isEmpty()) {
            this.scanning = false;
            return;
        }
        for (Progress progress : this.progressMap.values()) {
            progress.reset();
        }
        this.fillItems = Modules.FILL.getFillModeItemList();
        this.boxIndex = 0;
        this.iterator = this.boxes.get(0).iterator();
        this.scanning = true;
    }

    private BlockPos next() {
        while (this.boxIndex < this.boxes.size()) {
            if (this.iterator == null) {
                this.iterator = this.boxes.get(this.boxIndex).iterator();
            }
            if (this.iterator.hasNext()) {
                return this.iterator.next();
            }
            this.boxIndex++;
            this.iterator = null;
        }
        return null;
    }

    private void countPosition(WorldSchematic schematic, BlockPos pos) {
        BlockState currentState = this.client.level.getBlockState(pos);
        // 打印统计投影区域（schematic 里非空气 = 在投影范围内）；
        // 挖掘/填充/排流体统计选择区域（用户框选的选区）。
        boolean inSchematic = !schematic.getBlockState(pos).isAir();
        boolean inSelection = false;
        try {
            inSelection = LitematicaUtils.isWithinSelection1ModeRange(pos);
        } catch (Exception ignored) {
            // 选区 API 异常（如 Simple 选区 box 为空）时按不在选区内处理
        }

        if (this.trackPrint && inSchematic) {
            SchematicBlockContext context = new SchematicBlockContext(this.client, this.client.level, schematic, pos);
            this.progressMap.get(HudStatsManager.Mode.PRINT)
                    .record(BlockMatchResult.compare(context) == BlockMatchResult.CORRECT);
        }
        if (this.trackMine && inSelection) {
            this.progressMap.get(HudStatsManager.Mode.MINE).record(currentState.isAir());
        }
        if (this.trackFill && inSelection) {
            boolean ok = false;
            if (this.fillItems != null && this.fillItems.length > 0) {
                Item currentItem = currentState.getBlock().asItem();
                for (Item item : this.fillItems) {
                    if (item == currentItem) {
                        ok = true;
                        break;
                    }
                }
            }
            this.progressMap.get(HudStatsManager.Mode.FILL).record(ok);
        }
        if (this.trackFluid && inSelection) {
            this.progressMap.get(HudStatsManager.Mode.FLUID).record(!(currentState.getBlock() instanceof LiquidBlock));
        }
    }

    private void finishScan() {
        this.scanning = false;
        this.iterator = null;
        for (Progress progress : this.progressMap.values()) {
            progress.snapshot();
        }
        this.nextScanTick = this.client.level.getGameTime() + RESCAN_INTERVAL_TICKS;
    }

    /** 进度计数器：扫描中的临时值（scan*）与显示快照（display*）分离。 */
    private static final class Progress {
        private long scanTotal;
        private long scanFinished;
        private long displayTotal;
        private long displayFinished;
        private double displayProgress;

        private void reset() {
            this.scanTotal = 0;
            this.scanFinished = 0;
            // 不清空 display，保留上一轮完整结果
        }

        private void record(boolean ok) {
            this.scanTotal++;
            if (ok) {
                this.scanFinished++;
            }
        }

        private void snapshot() {
            this.displayTotal = this.scanTotal;
            this.displayFinished = this.scanFinished;
            if (this.scanTotal > 0) {
                this.displayProgress = (double) this.scanFinished / (double) this.scanTotal;
            }
        }

        private double get() {
            return this.displayProgress;
        }

        private long getFinished() {
            return this.displayFinished;
        }

        private long getTotal() {
            return this.displayTotal;
        }
    }
}