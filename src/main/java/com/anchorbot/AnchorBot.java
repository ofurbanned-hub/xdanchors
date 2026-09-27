package com.anchorbot;

import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.ExperienceOrbEntity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class AnchorBot {
    private enum State { OFF, RTP, SCANNING, PATHING, MINING, EATING, XP }

    private final MinecraftClient client;
    private State state = State.OFF;
    private boolean enabled;
    private List<BlockPos> anchors = List.of();
    private List<PathNode> path = List.of();
    private int pathIndex;
    private BlockPos targetAnchor;
    private BlockPos navigationTarget;
    private boolean navigatingToXp;
    private int rtpCooldown;
    private int scanCooldown;
    private int miningTicks;
    private int stuckTicks;
    private double lastX;
    private double lastZ;
    private int eatTicks;

    public AnchorBot(MinecraftClient client) {
        this.client = client;
    }

    public void toggle() {
        enabled = !enabled;
        if (!enabled) {
            state = State.OFF;
            stopInput();
            AnchorRtpBotClient.message(client, "OFF");
            return;
        }
        state = State.RTP;
        rtpCooldown = 40;
        sendRtp();
        AnchorRtpBotClient.message(client, "ON");
    }

    public void tick() {
        if (!enabled) return;
        if (client.player == null || client.world == null) return;

        if (rtpCooldown > 0) rtpCooldown--;
        if (scanCooldown > 0) scanCooldown--;

        if (nearbyPlayer()) {
            state = State.RTP;
            stopInput();
            sendRtp();
            rtpCooldown = 60;
            return;
        }

        if (handleEating()) return;

        if (state == State.RTP) {
            stopInput();
            if (rtpCooldown == 0) {
                state = State.SCANNING;
            }
            return;
        }

        if (state == State.SCANNING) {
            if (needsMending()) {
                ExperienceOrbEntity orb = nearestXpOrb();
                if (orb != null) {
                    navigationTarget = orb.getBlockPos();
                    navigatingToXp = true;
                    path = VoxelPathfinder.findPath(client.world, client.player.getBlockPos(), navigationTarget);
                    pathIndex = 0;
                    if (!path.isEmpty()) {
                        state = State.XP;
                        return;
                    }
                }
            }
            if (scanCooldown > 0) return;
            anchors = scanAnchors();
            scanCooldown = 200;

            if (anchors.isEmpty()) {
                state = State.RTP;
                rtpCooldown = 80;
                sendRtp();
                return;
            }

            targetAnchor = anchors.stream()
                    .min(Comparator.comparingDouble(p -> p.getSquaredDistance(client.player.getBlockPos())))
                    .orElse(null);

            if (targetAnchor == null) return;

            BlockPos goal = targetAnchor;
            path = VoxelPathfinder.findPath(client.world, client.player.getBlockPos(), goal);
            pathIndex = 0;
            stuckTicks = 0;
            lastX = client.player.getX();
            lastZ = client.player.getZ();

            if (path.isEmpty()) {
                anchors = anchors.stream().skip(1).toList();
                scanCooldown = 0;
                return;
            }

            state = State.PATHING;
            return;
        }

        if (state == State.PATHING) {
            if (targetAnchor == null || !client.world.getBlockState(targetAnchor).isOf(Blocks.RESPAWN_ANCHOR)) {
                state = State.SCANNING;
                scanCooldown = 0;
                stopInput();
                return;
            }

            if (client.player.squaredDistanceTo(Vec3d.ofCenter(targetAnchor)) < 20) {
                state = State.MINING;
                miningTicks = 0;
                stopInput();
                return;
            }

            followPath();
            return;
        }

        if (state == State.MINING) {
            mineAnchor();
        }

        if (state == State.XP) {
            if (!navigatingToXp || navigationTarget == null) {
                state = State.SCANNING;
                return;
            }
            if (client.player.squaredDistanceTo(Vec3d.ofCenter(navigationTarget)) < 4.0
                    || nearestXpOrb() == null) {
                state = State.SCANNING;
                scanCooldown = 0;
                navigatingToXp = false;
                navigationTarget = null;
                stopInput();
                return;
            }
            followGenericPath();
        }
    }

    private void followPath() {
        ClientPlayerEntity p = client.player;
        if (pathIndex >= path.size()) {
            state = State.MINING;
            stopInput();
            return;
        }

        PathNode node = path.get(pathIndex);
        double dist = p.squaredDistanceTo(Vec3d.ofCenter(node.pos));
        if (dist < 1.8) {
            pathIndex++;
            if (pathIndex >= path.size()) {
                state = State.MINING;
                stopInput();
                return;
            }
            node = path.get(pathIndex);
        }

        float yaw = yawTo(p.getPos(), Vec3d.ofCenter(node.pos));
        p.setYaw(yaw);
        p.setHeadYaw(yaw);

        boolean jump = node.jump && p.isOnGround();
        p.input.playerInput = new PlayerInput(true, false, false, false, jump, false, true);

        if (p.getX() - lastX < 0.01 && p.getZ() - lastZ < 0.01) {
            stuckTicks++;
        } else {
            stuckTicks = 0;
            lastX = p.getX();
            lastZ = p.getZ();
        }

        if (stuckTicks > 25) {
            stopInput();
            path = VoxelPathfinder.findPath(client.world, p.getBlockPos(), targetAnchor);
            pathIndex = 0;
            stuckTicks = 0;
        }
    }

    private void followGenericPath() {
        ClientPlayerEntity p = client.player;
        if (pathIndex >= path.size()) {
            state = State.SCANNING;
            navigatingToXp = false;
            navigationTarget = null;
            stopInput();
            return;
        }
        PathNode node = path.get(pathIndex);
        if (p.squaredDistanceTo(Vec3d.ofCenter(node.pos)) < 1.8) {
            pathIndex++;
            if (pathIndex >= path.size()) {
                state = State.SCANNING;
                navigatingToXp = false;
                navigationTarget = null;
                stopInput();
                return;
            }
            node = path.get(pathIndex);
        }
        float yaw = yawTo(p.getPos(), Vec3d.ofCenter(node.pos));
        p.setYaw(yaw);
        p.setHeadYaw(yaw);
        p.input.playerInput = new PlayerInput(true, false, false, false, node.jump && p.isOnGround(), false, true);
    }

    private void mineAnchor() {
        ClientPlayerEntity p = client.player;
        if (targetAnchor == null) {
            state = State.SCANNING;
            return;
        }

        if (!client.world.getBlockState(targetAnchor).isOf(Blocks.RESPAWN_ANCHOR)) {
            state = State.SCANNING;
            scanCooldown = 0;
            return;
        }

        int pick = findPickaxe();
        if (pick >= 0) p.getInventory().selectedSlot = pick;

        Vec3d center = Vec3d.ofCenter(targetAnchor);
        p.setYaw(yawTo(p.getPos(), center));
        p.setHeadYaw(p.getYaw());

        Direction face = bestFace(targetAnchor);
        if (client.interactionManager != null) {
            client.interactionManager.updateBlockBreakingProgress(targetAnchor, face);
        }

        miningTicks++;
        if (miningTicks > 160) {
            if (client.interactionManager != null) client.interactionManager.cancelBlockBreaking();
            state = State.SCANNING;
            scanCooldown = 0;
        }
    }

    private Direction bestFace(BlockPos pos) {
        Vec3d delta = Vec3d.ofCenter(pos).subtract(client.player.getEyePos());
        double ax = Math.abs(delta.x);
        double ay = Math.abs(delta.y);
        double az = Math.abs(delta.z);
        if (ay > ax && ay > az) return delta.y > 0 ? Direction.DOWN : Direction.UP;
        if (ax > az) return delta.x > 0 ? Direction.WEST : Direction.EAST;
        return delta.z > 0 ? Direction.NORTH : Direction.SOUTH;
    }

    private boolean handleEating() {
        ClientPlayerEntity p = client.player;
        if (!p.isAlive() || p.getHungerManager().getFoodLevel() > 10) return false;

        int food = findFood();
        if (food < 0) return false;

        if (!p.isUsingItem()) {
            p.getInventory().selectedSlot = food;
            if (client.interactionManager != null) {
                client.interactionManager.interactItem(p, Hand.MAIN_HAND);
            }
            eatTicks = 0;
        } else {
            p.input.playerInput = PlayerInput.DEFAULT;
            eatTicks++;
            if (eatTicks > 40) {
                client.interactionManager.stopUsingItem(p);
                eatTicks = 0;
            }
        }
        return true;
    }

    private boolean needsMending() {
        int slot = findPickaxe();
        if (slot < 0 || client.player == null) return false;
        ItemStack stack = client.player.getInventory().getStack(slot);
        if (stack.isEmpty() || !stack.isDamageable()) return false;
        int max = stack.getMaxDamage();
        int remaining = max - stack.getDamage();
        if (remaining > Math.max(40, max / 5)) return false;
        client.player.getInventory().selectedSlot = slot;
        return true;
    }

    private ExperienceOrbEntity nearestXpOrb() {
        ClientPlayerEntity p = client.player;
        return client.world.getEntitiesByClass(
                ExperienceOrbEntity.class,
                p.getBoundingBox().expand(48),
                e -> e.isAlive()
        ).stream().min(Comparator.comparingDouble(p::squaredDistanceTo)).orElse(null);
    }

    private int findFood() {
        ClientPlayerEntity p = client.player;
        for (int i = 0; i < p.getInventory().size(); i++) {
            ItemStack s = p.getInventory().getStack(i);
            if (!s.isEmpty() && s.isFood()) return i;
        }
        return -1;
    }

    private int findPickaxe() {
        ClientPlayerEntity p = client.player;
        for (int i = 0; i < 9; i++) {
            ItemStack s = p.getInventory().getStack(i);
            if (!s.isEmpty() && (s.isOf(Items.DIAMOND_PICKAXE) || s.isOf(Items.NETHERITE_PICKAXE) || s.isOf(Items.IRON_PICKAXE) || s.isOf(Items.GOLDEN_PICKAXE) || s.isOf(Items.STONE_PICKAXE) || s.isOf(Items.WOODEN_PICKAXE))) return i;
        }
        return p.getInventory().selectedSlot;
    }

    private List<BlockPos> scanAnchors() {
        ClientWorld w = client.world;
        BlockPos c = client.player.getBlockPos();
        ArrayList<BlockPos> found = new ArrayList<>();

        int r = 100;
        int minY = Math.max(w.getBottomY(), c.getY() - r);
        int maxY = Math.min(w.getTopY() - 1, c.getY() + r);

        for (int x = c.getX() - r; x <= c.getX() + r; x++) {
            int dx = x - c.getX();
            for (int z = c.getZ() - r; z <= c.getZ() + r; z++) {
                int dz = z - c.getZ();
                if (dx * dx + dz * dz > r * r) continue;

                for (int y = minY; y <= maxY; y++) {
                    int dy = y - c.getY();
                    if (dx * dx + dy * dy + dz * dz > r * r) continue;
                    BlockPos p = new BlockPos(x, y, z);
                    if (w.getBlockState(p).isOf(Blocks.RESPAWN_ANCHOR)) found.add(p);
                }
            }
        }
        return found;
    }

    private boolean nearbyPlayer() {
        ClientPlayerEntity me = client.player;
        for (PlayerEntity other : client.world.getPlayers()) {
            if (other != me && other.isAlive() && me.squaredDistanceTo(other) <= 32 * 32) return true;
        }
        return false;
    }

    private void sendRtp() {
        if (client.getNetworkHandler() != null) {
            client.getNetworkHandler().sendChatCommand("rtp");
        }
    }

    private void stopInput() {
        if (client.player != null) client.player.input.playerInput = PlayerInput.DEFAULT;
        if (client.interactionManager != null && client.interactionManager.isBreakingBlock()) {
            client.interactionManager.cancelBlockBreaking();
        }
    }

    private static float yawTo(Vec3d from, Vec3d to) {
        double dx = to.x - from.x;
        double dz = to.z - from.z;
        return (float)(Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
    }
}
