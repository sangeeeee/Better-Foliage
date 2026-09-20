package com.eerussianguy.betterfoliage.mixin;

import java.util.List;
import com.eerussianguy.betterfoliage.compat.EclipticLeafMerge;
import com.teamtea.eclipticseasons.client.core.ExtraRendererContext;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.teamtea.eclipticseasons.client.core.ExtraModelManager", remap = false)
public abstract class EclipticLeafModelMixin
{
    @Unique private static volatile boolean betterfoliage$incompatible;

    @Inject(method = "cancelTop(Lcom/teamtea/eclipticseasons/client/core/ExtraRendererContext;Lnet/minecraft/client/resources/model/BakedModel;Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;Lnet/minecraft/util/RandomSource;JLjava/util/List;Ljava/util/List;)Ljava/util/List;",
        at = @At("RETURN"), cancellable = true, require = 0)
    private static void betterfoliage$mergeLeafSnow(ExtraRendererContext context, BakedModel model, BlockAndTintGetter view,
        BlockState state, BlockPos pos, Direction side, RandomSource random, long seed,
        List<BakedQuad> original, List<BakedQuad> cache, CallbackInfoReturnable<List<BakedQuad>> callback)
    {
        if (betterfoliage$incompatible) return;
        List<BakedQuad> previous = callback.getReturnValue();
        try
        {
            List<BakedQuad> result = EclipticLeafMerge.apply(context, model, view, state, pos, side, seed, previous);
            if (result != previous) callback.setReturnValue(result);
        }
        catch (LinkageError incompatible)
        {
            betterfoliage$incompatible = true;
            com.mojang.logging.LogUtils.getLogger().warn("ES leaf-cube snow merging disabled: incompatible rendering API", incompatible);
        }
    }
}
