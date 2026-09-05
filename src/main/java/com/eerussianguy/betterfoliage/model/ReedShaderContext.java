package com.eerussianguy.betterfoliage.model;

/** Implemented on chunk vertex consumers that can carry synthetic Iris vegetation metadata. */
public interface ReedShaderContext
{
    void betterfoliage$beginReed(int blockId, int localX, int localY, int localZ);

    void betterfoliage$endReed();
}
