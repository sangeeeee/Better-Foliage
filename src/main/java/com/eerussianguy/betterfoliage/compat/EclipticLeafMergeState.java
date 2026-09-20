package com.eerussianguy.betterfoliage.compat;

import java.util.Arrays;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.util.RandomSource;

/** One small handshake per ES renderer context, not a world/position cache. */
public final class EclipticLeafMergeState
{
    public final BakedQuad[] merged = new BakedQuad[6];
    public final RandomSource random = RandomSource.create(0);
    public boolean probing;
    public boolean hasTint;
    public int tint;
    public void reset() { Arrays.fill(merged, null); probing = false; hasTint = false; }

    public interface Owner
    {
        EclipticLeafMergeState betterfoliage$leafMergeState();
    }
}
