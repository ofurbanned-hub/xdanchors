package com.anchorbot;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;

import java.util.*;

public final class VoxelPathfinder {
    private static final int SEARCH_RADIUS = 48;
    private static final int MAX_EXPANDED = 14000;

    private VoxelPathfinder() {}

    public static List<PathNode> findPath(ClientWorld world, BlockPos start, BlockPos goal) {
        BlockPos s = start.toImmutable();
        if (!standable(world, goal) && !standable(world, goal.down()) && !standable(world, goal.up())) {
            goal = nearestStandable(world, goal);
            if (goal == null) return List.of();
        }

        PriorityQueue<PathNode> open = new PriorityQueue<>(Comparator.comparingDouble(n -> n.f));
        Map<BlockPos, Double> best = new HashMap<>();
        Set<BlockPos> closed = new HashSet<>();

        PathNode first = new PathNode(s, 0, heuristic(s, goal), null, false);
        open.add(first);
        best.put(s, 0.0);

        int expanded = 0;
        while (!open.isEmpty() && expanded++ < MAX_EXPANDED) {
            PathNode cur = open.poll();
            if (!closed.add(cur.pos)) continue;

            if (nearGoal(cur.pos, goal)) return reconstruct(cur);

            for (Step step : neighbors(world, cur.pos)) {
                if (closed.contains(step.pos)) continue;
                double ng = cur.g + step.cost;
                Double old = best.get(step.pos);
                if (old != null && ng >= old) continue;

                double h = heuristic(step.pos, goal);
                PathNode next = new PathNode(step.pos, ng, ng + h, cur, step.jump);
                best.put(step.pos, ng);
                open.add(next);
            }
        }
        return List.of();
    }

    private static boolean nearGoal(BlockPos a, BlockPos b) {
        return a.getSquaredDistance(b) <= 2.25;
    }

    private static double heuristic(BlockPos a, BlockPos b) {
        return Math.sqrt(a.getSquaredDistance(b));
    }

    private static List<PathNode> reconstruct(PathNode end) {
        ArrayList<PathNode> result = new ArrayList<>();
        for (PathNode n = end; n != null; n = n.parent) result.add(n);
        Collections.reverse(result);
        return result;
    }

    private record Step(BlockPos pos, double cost, boolean jump) {}

    private static List<Step> neighbors(ClientWorld world, BlockPos p) {
        ArrayList<Step> out = new ArrayList<>(12);

        int[][] dirs = {
                {1,0},{-1,0},{0,1},{0,-1},
                {1,1},{1,-1},{-1,1},{-1,-1}
        };

        for (int[] d : dirs) {
            int nx = p.getX() + d[0];
            int nz = p.getZ() + d[1];

            if (Math.abs(nx - p.getX()) + Math.abs(nz - p.getZ()) == 2) {
                if (!standable(world, p.add(d[0], 0, 0)) || !standable(world, p.add(0, 0, d[1]))) continue;
            }

            for (int dy : new int[]{1, 0, -1, -2, -3}) {
                BlockPos np = new BlockPos(nx, p.getY() + dy, nz);
                if (!insideSearch(p, np)) continue;
                if (!standable(world, np)) continue;
                if (isDanger(world, np) || isDanger(world, np.down())) continue;

                boolean jump = dy == 1;
                double cost = Math.abs(d[0]) + Math.abs(d[1]) == 2 ? 1.42 : 1.0;
                if (dy == 1) cost += 0.35;
                if (dy < 0) cost += 0.45 * -dy;
                if (isDanger(world, np)) cost += 1000;

                out.add(new Step(np, cost, jump));
                break;
            }
        }

        return out;
    }

    private static boolean insideSearch(BlockPos s, BlockPos p) {
        return Math.abs(p.getX() - s.getX()) <= SEARCH_RADIUS
                && Math.abs(p.getZ() - s.getZ()) <= SEARCH_RADIUS
                && Math.abs(p.getY() - s.getY()) <= 16;
    }

    private static BlockPos nearestStandable(ClientWorld world, BlockPos goal) {
        for (int r = 1; r <= 4; r++) {
            for (int x = -r; x <= r; x++) {
                for (int z = -r; z <= r; z++) {
                    for (int y = -2; y <= 2; y++) {
                        BlockPos p = goal.add(x, y, z);
                        if (standable(world, p) && !isDanger(world, p.down())) return p;
                    }
                }
            }
        }
        return null;
    }

    private static boolean standable(ClientWorld world, BlockPos feet) {
        BlockState feetState = world.getBlockState(feet);
        BlockState headState = world.getBlockState(feet.up());
        BlockState floorState = world.getBlockState(feet.down());

        if (!feetState.getCollisionShape(world, feet).isEmpty()) return false;
        if (!headState.getCollisionShape(world, feet.up()).isEmpty()) return false;

        VoxelShape floor = floorState.getCollisionShape(world, feet.down());
        if (floor.isEmpty()) return false;

        if (isDanger(world, feet) || isDanger(world, feet.up()) || isDanger(world, feet.down())) return false;
        return true;
    }

    private static boolean isDanger(ClientWorld world, BlockPos pos) {
        Block b = world.getBlockState(pos).getBlock();
        return b == Blocks.LAVA
                || b == Blocks.FIRE
                || b == Blocks.SOUL_FIRE
                || b == Blocks.CAMPFIRE
                || b == Blocks.SOUL_CAMPFIRE
                || b == Blocks.CACTUS
                || b == Blocks.SWEET_BERRY_BUSH
                || b == Blocks.POWDER_SNOW
                || b == Blocks.MAGMA_BLOCK;
    }
}
