package com.anchorbot;

import net.minecraft.util.math.BlockPos;

public final class PathNode {
    public final BlockPos pos;
    public final double g;
    public final double f;
    public final PathNode parent;
    public final boolean jump;

    public PathNode(BlockPos pos, double g, double f, PathNode parent, boolean jump) {
        this.pos = pos;
        this.g = g;
        this.f = f;
        this.parent = parent;
        this.jump = jump;
    }
}
