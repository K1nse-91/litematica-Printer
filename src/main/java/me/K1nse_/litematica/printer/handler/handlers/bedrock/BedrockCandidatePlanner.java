package me.K1nse_.litematica.printer.handler.handlers.bedrock;

import me.K1nse_.litematica.printer.config.Configs;
import me.K1nse_.litematica.printer.handler.scan.ScanCache;
import me.K1nse_.litematica.printer.handler.scan.ScanIntent;
import me.K1nse_.litematica.printer.printer.PrinterBox;
import me.K1nse_.litematica.printer.utils.mods.LitematicaUtils;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

public final class BedrockCandidatePlanner {
    private static final Direction[] NEIGHBOR_DIRECTIONS = Direction.values();
    private static final int CANDIDATE_SOFT_CAP = 256;
    private static final int CANDIDATE_COLLECT_CAP = CANDIDATE_SOFT_CAP * 4;
    private static final int UNLIMITED_SCAN_SLICE = 4096;
    private static final int MAX_SCAN_SLICE = 32768;
    /**
     * 单 tick 内允许执行的"重型建模"(buildCandidate:layout 查找 + 双火把探测 + 13 格调度惩罚 + 6 邻居检测)次数上限。
     * 廉价过滤(空气/非基岩/冷却)不计入此预算。大交互距离下命中的基岩极多,若不设上限会在单 tick 内对成百上千个基岩
     * 做重型建模,卡满一帧后再爆发,表现为"一阵一阵"。入口扫描器会在下一 tick 续扫剩余位置,因此把建模成本摊到多 tick
     * 即可消除卡顿,同时仍能凑足候选池供优先级筛选。
     */
    private static final int MODELING_BUDGET_PER_TICK = 128;

    public Iterable<BlockPos> iterable(PrinterBox sourceBox, ClientLevel level, LocalPlayer player, int maxEffectiveExecutions, int scanGuardLimit) {
        CandidateShard shard = this.collectCandidateShard(sourceBox, level, player, scanGuardLimit);
        List<CandidateInfo> candidates = shard.candidates();

        List<CandidateInfo> selectedCandidates;
        if (candidates.size() <= 1) {
            selectedCandidates = candidates;
        } else {
            candidates.sort(Comparator
                    .comparingInt(CandidateInfo::priority)
                    .thenComparingInt(CandidateInfo::neighborTargetCount));

            int limit = Math.min(candidates.size(), this.getCandidateSelectionLimit(maxEffectiveExecutions));
            selectedCandidates = selectNonConflictingCandidates(candidates, limit);
        }

        List<BlockPos> filtered = new ArrayList<>(selectedCandidates.size() + (shard.hasMoreSource() ? 1 : 0));
        for (CandidateInfo candidate : selectedCandidates) {
            BedrockController.primeSubmissionPlan(candidate.pos(), candidate.layout(), candidate.placement(), candidate.slimePos());
            filtered.add(candidate.pos());
        }
        if (shard.hasMoreSource()) {
            filtered.add(null);
        }
        return filtered;
    }

    private CandidateShard collectCandidateShard(PrinterBox sourceBox, ClientLevel level, LocalPlayer player, int scanGuardLimit) {
        int scanLimit = this.getCandidateScanLimit(scanGuardLimit);
        Iterator<BlockPos> iterator = ScanCache.INSTANCE.iterable(
                "bedrock",
                sourceBox,
                level,
                null,
                player,
                scanLimit,
                ScanIntent.MINE,
                pos -> this.passesCheapFilters(level, pos)
        ).iterator();
        List<CandidateInfo> verticalCandidates = new ArrayList<>();
        List<CandidateInfo> sideCandidates = new ArrayList<>();
        boolean allowSide = Configs.Bedrock.BEDROCK_ALLOW_SIDE.getBooleanValue();
        int scanned = 0;
        int modeled = 0;
        boolean hasMoreSource = false;

        while (iterator.hasNext()
                && scanned < scanLimit
                && verticalCandidates.size() + sideCandidates.size() < CANDIDATE_COLLECT_CAP) {
            // 单 tick 重型建模预算用尽时停止本 tick 扫描,剩余位置由入口扫描会话在下一 tick 续扫,
            // 避免大 box 下一次性建模成百上千个基岩造成的卡顿"一阵一阵"。
            if (modeled >= MODELING_BUDGET_PER_TICK) {
                hasMoreSource = true;
                break;
            }
            BlockPos pos = iterator.next();
            if (pos == null) {
                hasMoreSource = true;
                break;
            }
            scanned++;
            modeled++;
            CandidateInfo candidate = this.buildModeledCandidate(level, pos, allowSide);
            if (candidate != null) {
                if (candidate.layout() != null && candidate.layout().isHorizontal()) {
                    sideCandidates.add(candidate);
                } else {
                    verticalCandidates.add(candidate);
                }
            }
        }

        List<CandidateInfo> candidates = verticalCandidates.isEmpty() ? sideCandidates : verticalCandidates;
        return new CandidateShard(candidates, hasMoreSource || iterator.hasNext());
    }

    /**
     * 廉价过滤:仅做范围/目标方块/冷却判断,不触碰 layout 与火把探测等重型逻辑。不计入建模预算。
     */
    private boolean passesCheapFilters(ClientLevel level, BlockPos pos) {
        if (pos == null || !BedrockEnvironment.canInteract(pos)) {
            return false;
        }
        if (!LitematicaUtils.isWithinSelection1ModeRange(pos)) {
            return false;
        }
        if (!BedrockTargetBlocks.isTargetBlock(level.getBlockState(pos))) {
            return false;
        }
        return !BedrockController.isPositionOnRetryCooldown(pos);
    }

    /**
     * 重型建模阶段:调用方需保证已通过 {@link #passesCheapFilters}。计入建模预算。
     */
    private CandidateInfo buildModeledCandidate(ClientLevel level, BlockPos pos, boolean allowSide) {
        CandidateInfo candidate = buildCandidate(level, pos.immutable());
        if (candidate.layout() == null) {
            return null;
        }
        if (candidate.layout().isHorizontal() && !allowSide) {
            return null;
        }
        return candidate;
    }

    private int getCandidateSelectionLimit(int maxEffectiveExecutions) {
        return Math.max(1, Math.min(CANDIDATE_SOFT_CAP, maxEffectiveExecutions));
    }

    private int getCandidateScanLimit(int scanGuardLimit) {
        int baseScanLimit = scanGuardLimit > 0 ? scanGuardLimit : UNLIMITED_SCAN_SLICE;
        BedrockController.HudSnapshot snapshot = BedrockController.getHudSnapshot();
        int activeDeficit = Math.max(1, snapshot.activeCap() - snapshot.activeTargets());
        long expandedScanLimit = (long) baseScanLimit * activeDeficit;
        return (int) Math.max(1L, Math.min(MAX_SCAN_SLICE, expandedScanLimit));
    }

    private CandidateInfo buildCandidate(ClientLevel level, BlockPos pos) {
        BedrockMachineLayout layout = BedrockMachineLayout.find(level, pos);
        PlacementSelection placementSelection = layout == null ? null : findPlacementSelection(level, layout, pos);
        BedrockTorchPlacement placement = placementSelection == null ? null : placementSelection.placement();
        BlockPos slimePos = placementSelection == null ? null : placementSelection.slimePos();
        int priority = candidatePriority(level, pos, layout, placement);
        int neighborTargetCount = neighborTargetCount(level, pos);
        return new CandidateInfo(
                pos,
                layout,
                placement,
                slimePos,
                buildStructuralPositions(pos, layout),
                buildPowerReservationPositions(placement),
                priority,
                neighborTargetCount
        );
    }

    private int candidatePriority(ClientLevel level, BlockPos pos, BedrockMachineLayout layout, BedrockTorchPlacement placement) {
        int controllerPenalty = BedrockController.getSchedulingPenalty(pos);
        if (layout != null) {
            int penalty = controllerPenalty;
            penalty += BedrockController.getSchedulingPenalty(layout.getPistonPos());
            penalty += BedrockController.getSchedulingPenalty(layout.getHeadPos());
            if (placement != null) {
                penalty += BedrockController.getSchedulingPenalty(placement.getSupportPos());
                penalty += BedrockController.getSchedulingPenalty(placement.getTorchPos());
                if (level.getBlockState(placement.getSupportPos()).is(Blocks.SLIME_BLOCK)) {
                    penalty += 200;
                }
            }
            penalty += BedrockController.getPredictedMachineOverlapPenalty(pos, layout, placement);
            return penalty;
        }
        if (BedrockMachineLayout.shouldDeferUntilExposed(level, pos)) {
            return controllerPenalty + 1_000;
        }
        return controllerPenalty + 10_000;
    }

    private static List<CandidateInfo> selectNonConflictingCandidates(List<CandidateInfo> candidates, int limit) {
        if (limit <= 0 || candidates.isEmpty()) {
            return List.of();
        }
        List<CandidateInfo> selected = new ArrayList<>(limit);
        for (CandidateInfo candidate : candidates) {
            if (selected.size() >= limit) {
                break;
            }
            if (conflictsWithSelected(candidate, selected)) {
                continue;
            }
            selected.add(candidate);
        }
        return selected;
    }

    private static boolean conflictsWithSelected(CandidateInfo candidate, List<CandidateInfo> selected) {
        for (CandidateInfo existing : selected) {
            if (candidatesConflict(candidate, existing)) {
                return true;
            }
        }
        return false;
    }

    private static boolean candidatesConflict(CandidateInfo left, CandidateInfo right) {
        if (left.layout() == null || right.layout() == null) {
            return false;
        }
        if (intersects(left.structuralPositions(), right.structuralPositions())
                || intersects(left.structuralPositions(), right.powerReservationPositions())
                || intersects(left.powerReservationPositions(), right.structuralPositions())) {
            return true;
        }
        if (left.placement() != null && right.placement() != null
                && sameTorchPlacement(left.placement(), right.placement())) {
            return false;
        }
        return isTorchPoweredBy(left.layout().getPistonPos(), right.placement())
                || isTorchPoweredBy(right.layout().getPistonPos(), left.placement());
    }

    private static boolean intersects(Iterable<BlockPos> left, Iterable<BlockPos> right) {
        for (BlockPos leftPos : left) {
            for (BlockPos rightPos : right) {
                if (leftPos.equals(rightPos)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static List<BlockPos> buildStructuralPositions(BlockPos bedrockPos, BedrockMachineLayout layout) {
        List<BlockPos> positions = new ArrayList<>(3);
        positions.add(bedrockPos);
        if (layout != null) {
            positions.add(layout.getPistonPos());
            positions.add(layout.getHeadPos());
        }
        return positions;
    }

    private static List<BlockPos> buildPowerReservationPositions(BedrockTorchPlacement placement) {
        if (placement == null) {
            return List.of();
        }
        List<BlockPos> positions = new ArrayList<>(2);
        if (placement.getSupportPos() != null) {
            positions.add(placement.getSupportPos());
        }
        if (placement.getTorchPos() != null) {
            positions.add(placement.getTorchPos());
        }
        return positions;
    }

    private static PlacementSelection findPlacementSelection(ClientLevel level, BedrockMachineLayout layout, BlockPos bedrockPos) {
        BedrockTorchPlacement placement = BedrockEnvironment.findTorchPlacement(
                level,
                layout.getPistonPos(),
                layout.getPistonOffset().getOpposite(),
                bedrockPos,
                layout.getPistonPos(),
                layout.getHeadPos()
        );
        if (placement != null) {
            return new PlacementSelection(placement, level.getBlockState(placement.getSupportPos()).is(Blocks.SLIME_BLOCK)
                    ? placement.getSupportPos()
                    : null);
        }
        placement = BedrockEnvironment.findPossibleSlimeTorchPlacement(
                level,
                layout.getPistonPos(),
                layout.getPistonOffset().getOpposite(),
                bedrockPos,
                layout.getPistonPos(),
                layout.getHeadPos()
        );
        return placement == null ? null : new PlacementSelection(placement, placement.getSupportPos());
    }

    private static int neighborTargetCount(ClientLevel level, BlockPos pos) {
        int count = 0;
        for (Direction direction : NEIGHBOR_DIRECTIONS) {
            BlockPos neighborPos = pos.relative(direction);
            if (BedrockTargetBlocks.isTargetBlock(level.getBlockState(neighborPos))) {
                count++;
            }
        }
        return count;
    }

    private static boolean sameTorchPlacement(BedrockTorchPlacement left, BedrockTorchPlacement right) {
        return left.getClickedFace() == right.getClickedFace()
                && left.getSupportPos() != null
                && left.getSupportPos().equals(right.getSupportPos())
                && left.getTorchPos() != null
                && left.getTorchPos().equals(right.getTorchPos());
    }

    private static boolean isTorchPoweredBy(BlockPos pistonPos, BedrockTorchPlacement placement) {
        return pistonPos != null
                && placement != null
                && placement.getTorchPos() != null
                && BedrockEnvironment.getTorchInfluencePositions(pistonPos).contains(placement.getTorchPos());
    }

    private record CandidateShard(List<CandidateInfo> candidates, boolean hasMoreSource) {
    }

    private record CandidateInfo(
            BlockPos pos,
            BedrockMachineLayout layout,
            BedrockTorchPlacement placement,
            BlockPos slimePos,
            List<BlockPos> structuralPositions,
            List<BlockPos> powerReservationPositions,
            int priority,
            int neighborTargetCount
    ) {
    }

    private record PlacementSelection(BedrockTorchPlacement placement, BlockPos slimePos) {
    }
}
