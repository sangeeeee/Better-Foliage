package com.eerussianguy.betterfoliage.mixin;

import com.eerussianguy.betterfoliage.compat.SodiumLeafCullingCompat;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Makes the current Sodium leaf position available while its Better Foliage model emits quads. */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderer", remap = false)
public abstract class SodiumBlockRendererMixin
{
    @Unique private boolean betterfoliage$leafCullingContext;

    @Inject(method = "renderModel", at = @At("HEAD"), require = 0)
    private void betterfoliage$beginLeafCullingContext(
        BakedModel model,
        BlockState state,
        BlockPos pos,
        BlockPos sectionLocalPos,
        CallbackInfo callback
    )
    {
        betterfoliage$leafCullingContext = state.getBlock() instanceof LeavesBlock
            && SodiumLeafCullingCompat.isAvailable();
        if (betterfoliage$leafCullingContext)
        {
            SodiumLeafCullingCompat.beginLeaf(this, pos);
        }
    }

    @Inject(method = "renderModel", at = @At("RETURN"), require = 0)
    private void betterfoliage$endLeafCullingContext(
        BakedModel model,
        BlockState state,
        BlockPos pos,
        BlockPos sectionLocalPos,
        CallbackInfo callback
    )
    {
        if (betterfoliage$leafCullingContext)
        {
            SodiumLeafCullingCompat.endLeaf();
            betterfoliage$leafCullingContext = false;
        }
    }
}
