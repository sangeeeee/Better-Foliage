package com.eerussianguy.betterfoliage.mixin;

import com.eerussianguy.betterfoliage.model.IrisShaderCompat;
import com.eerussianguy.betterfoliage.model.ReedShaderContext;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Supplies Iris metadata for NeoForge additional-section geometry compiled through Sodium's fallback consumer. */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.compile.buffers.ChunkVertexConsumer", remap = false)
public abstract class SodiumChunkVertexConsumerMixin implements ReedShaderContext
{
    @Unique private boolean betterfoliage$reedActive;
    @Unique private int betterfoliage$blockId = -1;
    @Unique private int betterfoliage$localX;
    @Unique private int betterfoliage$localY;
    @Unique private int betterfoliage$localZ;

    @Override
    public void betterfoliage$beginReed(int blockId, int localX, int localY, int localZ)
    {
        betterfoliage$reedActive = true;
        betterfoliage$blockId = blockId;
        betterfoliage$localX = localX;
        betterfoliage$localY = localY;
        betterfoliage$localZ = localZ;
    }

    @Override
    public void betterfoliage$endReed()
    {
        betterfoliage$reedActive = false;
        betterfoliage$blockId = -1;
    }

    @Inject(
        method = "potentiallyEndVertex",
        at = @At(
            value = "INVOKE",
            target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/vertex/builder/ChunkMeshBufferBuilder;push([Lnet/caffeinemc/mods/sodium/client/render/chunk/vertex/format/ChunkVertexEncoder$Vertex;Lnet/caffeinemc/mods/sodium/client/render/chunk/terrain/material/Material;)V"
        ),
        require = 0
    )
    private void betterfoliage$attachIrisBlockData(CallbackInfoReturnable<VertexConsumer> callback)
    {
        IrisShaderCompat.writeSodiumVertexData(
            this,
            betterfoliage$reedActive ? betterfoliage$blockId : -1,
            betterfoliage$localX,
            betterfoliage$localY,
            betterfoliage$localZ
        );
    }
}
