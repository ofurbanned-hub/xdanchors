# Anchor RTP Bot — Minecraft 1.21.11

Client-side Fabric mod.

Default keybind: `O`

Behavior:
- Sends `/rtp` when starting.
- Finds Respawn Anchors within a 100-block spherical radius.
- Uses bounded A* voxel pathfinding with jumping, step-up, drop and obstacle avoidance.
- Replans when the route becomes blocked.
- Walks to the nearest reachable anchor.
- Mines it with a pickaxe.
- Rescans after mining.
- Uses `/rtp` when no anchors remain.
- Uses `/rtp` when another player enters the configurable danger radius.
- Eats when hunger is low.
- When the held/selected pickaxe gets low on durability, it seeks nearby XP orbs so Mending can repair it.

The uploaded StylexTV/Maple project targets Minecraft 1.18.1, so it cannot be used as a 1.21.11 drop-in dependency. This project implements the needed pathfinding behavior directly for 1.21.11.
