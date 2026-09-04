package com.eerussianguy.betterfoliage.model;

/** Implemented on chunk vertex consumers that can carry a synthetic Iris block context. */
public interface ReedShaderContext
{
    void betterfoliage$beginReed(int blockId, int localX, int localY, int localZ);

    void betterfoliage$endReed();
}
