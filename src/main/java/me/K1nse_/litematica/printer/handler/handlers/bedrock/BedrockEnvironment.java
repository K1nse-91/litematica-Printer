package me.K1nse_.litematica.printer.handler.handlers.bedrock;

import me.K1nse_.litematica.printer.config.Configs;
import me.K1nse_.litematica.printer.enums.RadiusShapeType;
import me.K1nse_.litematica.printer.utils.ConfigUtils;
import me.K1nse_.litematica.printer.utils.minecraft.BlockUtils;
import me.K1nse_.litematica.printer.utils.minecraft.PlayerUtils;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedstoneWallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class BedrockEnvironment {
    private static final double BEDROCK_INTERACTION_GRACE = 1.0D;
    private static final Direction[] PLACEMENT_DIRECTIONS = {
            Direction.DOWN,
            Direction.NORTH,
            Direction.SOUTH,
            Direction.EAST,
            Direction.WEST,
            Direction.UP
    };

    private BedrockEnvironment() {
    }

    public static boolean isTorchPlacementUsable(ClientLevel level, BedrockTorchPlacement placement) {
        if (placement == null || placement.getSupportPos() == null || placement.getClickedFace() == null) {
            return false;
        }
        BlockPos supportPos = placement.getSupportPos();
        BlockPos torchPos = placement.getTorchPos();
        Direction clickedFace = placement.getClickedFace();
        if (torchPos == null) {
            return false;
        }
        if (!isWithinBuildHeight(level, supportPos) || !isWithinBuildHeight(level, torchPos)) {
            return false;
        }
        boolean supportOk = clickedFace == Direction.UP
                ? BlockUtils.canSupportCenter(level, supportPos, Direction.UP)
                : level.getBlockState(supportPos).isFaceSturdy(level, supportPos, clickedFace);
        if (!supportOk) {
            return false;
        }
        BlockState torchState = level.getBlockState(torchPos);
        if (BlockUtils.isReplaceable(torchState)) {
            return true;
        }
        if (!isRedstoneTorch(torchState)) {
            return false;
        }
        if (clickedFace == Direction.UP) {
            return torchState.is(Blocks.REDSTONE_TORCH);
        }
        return torchState.is(Blocks.REDSTONE_WALL_TORCH)
                && torchState.getValue(RedstoneWallTorchBlock.FACING) == clickedFace;
    }

    public static boolean isTorchSupportUsable(ClientLevel level, BlockPos supportPos) {
        return isTorchPlacementUsable(level, new BedrockTorchPlacement(supportPos, Direction.UP));
    }

    public static boolean isSlimeSupportUsable(ClientLevel level, BlockPos slimePos) {
        if (slimePos == null) {
            return false;
        }
        BlockPos torchPos = slimePos.above();
        if (!isWithinBuildHeight(level, slimePos) || !isWithinBuildHeight(level, torchPos)) {
            return false;
        }
        return (level.getBlockState(slimePos).is(Blocks.SLIME_BLOCK) || BlockUtils.isReplaceable(level.getBlockState(slimePos)))
                && (BlockUtils.isReplaceable(level.getBlockState(torchPos)) || isRedstoneTorch(level.getBlockState(torchPos)));
    }

    public static boolean isSlimePlacementUsable(ClientLevel level, BedrockTorchPlacement placement) {
        if (placement == null || placement.getSupportPos() == null || placement.getTorchPos() == null) {
            return false;
        }
        if (!isWithinBuildHeight(level, placement.getSupportPos()) || !isWithinBuildHeight(level, placement.getTorchPos())) {
            return false;
        }
        BlockState supportState = level.getBlockState(placement.getSupportPos());
        if (!supportState.is(Blocks.SLIME_BLOCK) && !BlockUtils.isReplaceable(supportState)) {
            return false;
        }
        BlockState torchState = level.getBlockState(placement.getTorchPos());
        return BlockUtils.isReplaceable(torchState) || isRedstoneTorch(torchState);
    }

    public static BlockPos findTorchSupport(ClientLevel level, BlockPos bedrockPos) {
        return findTorchSupport(level, bedrockPos, null);
    }

    public static BlockPos findTorchSupport(ClientLevel level, BlockPos centerPos, Direction excludedAxis) {
        for (Direction direction : new Direction[]{Direction.EAST, Direction.WEST, Direction.NORTH, Direction.SOUTH}) {
            if (excludedAxis != null && direction == excludedAxis) {
                continue;
            }
            BlockPos support = centerPos.relative(direction);
            if (isTorchSupportUsable(level, support)) {
                return support;
            }
        }
        return null;
    }

    public static BedrockTorchPlacement findTorchPlacement(ClientLevel level, BlockPos centerPos, Direction excludedAxis, BlockPos... blockedPositions) {
        return findPreferredTorchPlacement(level, centerPos, excludedAxis, false, blockedPositions);
    }

    public static BedrockTorchPlacement findTorchPlacement(ClientLevel level, BlockPos centerPos, Direction excludedAxis) {
        return findTorchPlacement(level, centerPos, excludedAxis, new BlockPos[0]);
    }

    public static BedrockTorchPlacement findPossibleSlimeTorchPlacement(ClientLevel level, BlockPos centerPos, Direction excludedAxis, BlockPos... blockedPositions) {
        return findPreferredTorchPlacement(level, centerPos, excludedAxis, true, blockedPositions);
    }

    private static BedrockTorchPlacement findPreferredTorchPlacement(ClientLevel level, BlockPos centerPos, Direction excludedAxis, boolean allowSlimeSupport, BlockPos... blockedPositions) {
        BedrockTorchPlacement topFallback = null;
        BedrockTorchPlacement topDirtyFallback = null;
        for (Direction direction : new Direction[]{Direction.EAST, Direction.WEST, Direction.NORTH, Direction.SOUTH}) {
            if (excludedAxis != null && direction == excludedAxis) {
                continue;
            }
            BedrockTorchPlacement placement = new BedrockTorchPlacement(centerPos.relative(direction), Direction.UP);
            if (!isPlacementUsable(level, placement, allowSlimeSupport, blockedPositions)) {
                continue;
            }
            if (isPlacementInteractable(placement, blockedPositions)) {
                if (isCleanupFriendlyPlacement(level, placement)) {
                    return placement;
                }
                if (topDirtyFallback == null) {
                    topDirtyFallback = placement;
                }
                continue;
            }
            if (topFallback == null) {
                topFallback = placement;
            }
        }

        BedrockTorchPlacement wallFallback = null;
        BedrockTorchPlacement wallDirtyFallback = null;
        for (Direction direction : new Direction[]{Direction.EAST, Direction.WEST, Direction.NORTH, Direction.SOUTH}) {
            if (excludedAxis != null && direction == excludedAxis) {
                continue;
            }
            BlockPos torchPos = centerPos.relative(direction);
            for (Direction attachedFace : new Direction[]{Direction.EAST, Direction.WEST, Direction.NORTH, Direction.SOUTH}) {
                if (excludedAxis != null && attachedFace == excludedAxis) {
                    continue;
                }
                BedrockTorchPlacement placement = new BedrockTorchPlacement(torchPos.relative(attachedFace.getOpposite()), attachedFace);
                if (!isPlacementUsable(level, placement, allowSlimeSupport, blockedPositions)) {
                    continue;
                }
                if (isPlacementInteractable(placement, blockedPositions)) {
                    if (isCleanupFriendlyPlacement(level, placement)) {
                        return placement;
                    }
                    if (wallDirtyFallback == null) {
                        wallDirtyFallback = placement;
                    }
                    continue;
                }
                if (wallFallback == null) {
                    wallFallback = placement;
                }
            }
        }

        BedrockTorchPlacement lowerTopFallback = null;
        BedrockTorchPlacement lowerTopDirtyFallback = null;
        for (Direction direction : new Direction[]{Direction.EAST, Direction.WEST, Direction.NORTH, Direction.SOUTH}) {
            if (excludedAxis != null && direction == excludedAxis) {
                continue;
            }
            BedrockTorchPlacement placement = new BedrockTorchPlacement(centerPos.relative(direction).below(), Direction.UP);
            if (!isPlacementUsable(level, placement, allowSlimeSupport, blockedPositions)) {
                continue;
            }
            if (isPlacementInteractable(placement, blockedPositions)) {
                if (isCleanupFriendlyPlacement(level, placement)) {
                    return placement;
                }
                if (lowerTopDirtyFallback == null) {
                    lowerTopDirtyFallback = placement;
                }
                continue;
            }
            if (lowerTopFallback == null) {
                lowerTopFallback = placement;
            }
        }

        if (topDirtyFallback != null) {
            return topDirtyFallback;
        }
        if (wallDirtyFallback != null) {
            return wallDirtyFallback;
        }
        if (lowerTopDirtyFallback != null) {
            return lowerTopDirtyFallback;
        }
        if (topFallback != null) {
            return topFallback;
        }
        if (wallFallback != null) {
            return wallFallback;
        }
        return lowerTopFallback;
    }

    private static boolean isPlacementUsable(ClientLevel level, BedrockTorchPlacement placement, boolean allowSlimeSupport, BlockPos... blockedPositions) {
        if (conflictsWithBlockedPositions(placement, blockedPositions)) {
            return false;
        }
        return allowSlimeSupport
                ? isSlimePlacementUsable(level, placement)
                : isTorchPlacementUsable(level, placement);
    }

    private static boolean conflictsWithBlockedPositions(BedrockTorchPlacement placement, BlockPos... blockedPositions) {
        if (placement == null || blockedPositions == null) {
            return false;
        }
        BlockPos supportPos = placement.getSupportPos();
        BlockPos torchPos = placement.getTorchPos();
        for (BlockPos blockedPos : blockedPositions) {
            if (blockedPos == null) {
                continue;
            }
            if (blockedPos.equals(supportPos) || blockedPos.equals(torchPos)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isCleanupFriendlyPlacement(ClientLevel level, BedrockTorchPlacement placement) {
        if (level == null || placement == null || placement.getSupportPos() == null || placement.getTorchPos() == null) {
            return false;
        }

        BlockState supportState = level.getBlockState(placement.getSupportPos());
        if (BedrockTargetBlocks.isCleanupResidue(supportState) && !supportState.is(Blocks.SLIME_BLOCK)) {
            return false;
        }

        BlockState torchState = level.getBlockState(placement.getTorchPos());
        return !BedrockTargetBlocks.isCleanupResidue(torchState) || isRedstoneTorch(torchState);
    }

    public static BlockPos findPossibleSlimeSupport(ClientLevel level, BlockPos bedrockPos) {
        return findPossibleSlimeSupport(level, bedrockPos, null);
    }

    public static BlockPos findPossibleSlimeSupport(ClientLevel level, BlockPos centerPos, Direction excludedAxis) {
        for (Direction direction : new Direction[]{Direction.EAST, Direction.WEST, Direction.NORTH, Direction.SOUTH}) {
            if (excludedAxis != null && direction == excludedAxis) {
                continue;
            }
            BlockPos slimePos = centerPos.relative(direction);
            BlockPos torchPos = slimePos.above();
            if (!isWithinBuildHeight(level, slimePos) || !isWithinBuildHeight(level, torchPos)) {
                continue;
            }
            if (BlockUtils.isReplaceable(level.getBlockState(slimePos)) && BlockUtils.isReplaceable(level.getBlockState(torchPos))) {
                return slimePos;
            }
        }
        return null;
    }

    public static boolean hasRoomForPiston(ClientLevel level, BlockPos bedrockPos) {
        return hasRoomForPiston(level, bedrockPos.above(), Direction.UP);
    }

    public static boolean hasRoomForPiston(ClientLevel level, BlockPos pistonPos, Direction facing) {
        BlockPos headPos = pistonPos.relative(facing);
        if (!isWithinBuildHeight(level, pistonPos) || !isWithinBuildHeight(level, headPos)) {
            return false;
        }
        return BlockUtils.isReplaceable(level.getBlockState(pistonPos)) && BlockUtils.isReplaceable(level.getBlockState(headPos));
    }

    private static boolean isWithinBuildHeight(ClientLevel level, BlockPos pos) {
        return !level.isOutsideBuildHeight(pos);
    }

    public static List<BlockPos> findNearbyRedstoneTorches(ClientLevel level, BlockPos pistonPos) {
        List<BlockPos> result = new ArrayList<>();
        for (BlockPos candidate : getTorchInfluencePositions(pistonPos)) {
            if (isRedstoneTorch(level.getBlockState(candidate))) {
                result.add(candidate);
            }
        }
        return result;
    }

    public static boolean isRedstoneTorch(BlockState state) {
        return state.is(Blocks.REDSTONE_TORCH) || state.is(Blocks.REDSTONE_WALL_TORCH);
    }

    public static boolean isRedstoneTorchAt(ClientLevel level, BlockPos pos) {
        return pos != null && isRedstoneTorch(level.getBlockState(pos));
    }

    public static boolean arePositionsInteractable(BlockPos... positions) {
        return findFirstOutOfRangePosition(positions) == null;
    }

    public static boolean arePositionsInteractable(Iterable<BlockPos> positions) {
        return findFirstOutOfRangePosition(positions) == null;
    }

    public static PlacementInteraction findPlacementInteraction(ClientLevel level, BlockPos placePos, BlockPos... preferredAnchors) {
        List<PlacementInteraction> interactions = getPlacementInteractions(level, placePos, preferredAnchors);
        return interactions.isEmpty() ? null : interactions.get(0);
    }

    public static List<PlacementInteraction> getPlacementInteractions(ClientLevel level, BlockPos placePos, BlockPos... preferredAnchors) {
        List<PlacementInteraction> interactions = new ArrayList<>();
        if (level == null || placePos == null) {
            return interactions;
        }

        Set<BlockPos> seenAnchors = new LinkedHashSet<>();
        if (preferredAnchors != null) {
            for (BlockPos preferredAnchor : preferredAnchors) {
                addPlacementInteraction(level, placePos, preferredAnchor, seenAnchors, interactions);
            }
        }

        for (Direction direction : PLACEMENT_DIRECTIONS) {
            addPlacementInteraction(level, placePos, placePos.relative(direction), seenAnchors, interactions);
        }

        return interactions;
    }

    public static boolean canInteract(BlockPos pos) {
        if (pos == null || !isWithinBedrockWorkRange(pos)) {
            return false;
        }
        if (!Configs.Core.CHECK_PLAYER_INTERACTION_RANGE.getBooleanValue()) {
            return true;
        }

        LocalPlayer player = ConfigUtils.client.player;
        if (player == null) {
            return false;
        }

        return PlayerUtils.isWithinBlockInteractionRange(player, pos, BEDROCK_INTERACTION_GRACE);
    }

    public static BlockPos findFirstOutOfRangePosition(BlockPos... positions) {
        if (positions == null) {
            return null;
        }
        for (BlockPos pos : positions) {
            if (pos != null && !canInteract(pos)) {
                return pos;
            }
        }
        return null;
    }

    public static BlockPos findFirstOutOfRangePosition(Iterable<BlockPos> positions) {
        if (positions == null) {
            return null;
        }
        for (BlockPos pos : positions) {
            if (pos != null && !canInteract(pos)) {
                return pos;
            }
        }
        return null;
    }

    public static List<BlockPos> getTorchInfluencePositions(BlockPos pistonPos) {
        List<BlockPos> result = new ArrayList<>();
        for (int yOffset : new int[]{0, 1, -1}) {
            BlockPos center = pistonPos.offset(0, yOffset, 0);
            for (Direction direction : new Direction[]{Direction.EAST, Direction.WEST, Direction.NORTH, Direction.SOUTH}) {
                result.add(center.relative(direction));
            }
        }
        return result;
    }

    private static boolean isPlacementInteractable(BedrockTorchPlacement placement, BlockPos... blockedPositions) {
        return placement != null
                && arePositionsInteractable(blockedPositions)
                && arePositionsInteractable(placement.getSupportPos(), placement.getTorchPos());
    }

    private static void addPlacementInteraction(ClientLevel level, BlockPos placePos, BlockPos anchorPos, Set<BlockPos> seenAnchors, List<PlacementInteraction> interactions) {
        if (anchorPos == null || !seenAnchors.add(anchorPos)) {
            return;
        }
        Direction clickedFace = getClickedFace(placePos, anchorPos);
        if (clickedFace == null) {
            return;
        }
        if (!isPlacementAnchorUsable(level, anchorPos, clickedFace)) {
            return;
        }
        interactions.add(new PlacementInteraction(anchorPos, clickedFace));
    }

    private static Direction getClickedFace(BlockPos placePos, BlockPos anchorPos) {
        int dx = placePos.getX() - anchorPos.getX();
        int dy = placePos.getY() - anchorPos.getY();
        int dz = placePos.getZ() - anchorPos.getZ();
        if (Math.abs(dx) + Math.abs(dy) + Math.abs(dz) != 1) {
            return null;
        }
        if (dx == 1) {
            return Direction.EAST;
        }
        if (dx == -1) {
            return Direction.WEST;
        }
        if (dy == 1) {
            return Direction.UP;
        }
        if (dy == -1) {
            return Direction.DOWN;
        }
        if (dz == 1) {
            return Direction.SOUTH;
        }
        if (dz == -1) {
            return Direction.NORTH;
        }
        return null;
    }

    private static boolean isPlacementAnchorUsable(ClientLevel level, BlockPos anchorPos, Direction clickedFace) {
        if (!isWithinBuildHeight(level, anchorPos) || !canInteract(anchorPos)) {
            return false;
        }
        BlockState anchorState = level.getBlockState(anchorPos);
        if (anchorState.isAir() || BlockUtils.isReplaceable(anchorState)) {
            return false;
        }
        return anchorState.isFaceSturdy(level, anchorPos, clickedFace) || BlockUtils.canSupportCenter(level, anchorPos, clickedFace);
    }

    private static boolean isWithinBedrockWorkRange(BlockPos pos) {
        double workRange = ConfigUtils.getWorkRange();
        if (Configs.Core.ITERATOR_SHAPE.getOptionListValue() instanceof RadiusShapeType radiusShapeType) {
            return switch (radiusShapeType) {
                case SPHERE -> PlayerUtils.isWithinWorkInteractedEuclideanRange(pos, workRange);
                case OCTAHEDRON -> PlayerUtils.isWithinWorkInteractedManhattanRange(pos, workRange);
                case CUBE -> PlayerUtils.isWithinWorkInteractedCubeRange(pos, workRange);
            };
        }
        return true;
    }

    public record PlacementInteraction(BlockPos anchorPos, Direction clickedFace) {
    }
}
