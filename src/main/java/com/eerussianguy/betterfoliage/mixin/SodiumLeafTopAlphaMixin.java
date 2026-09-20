package com.eerussianguy.betterfoliage.mixin;

import com.eerussianguy.betterfoliage.compat.SodiumLeafCullingCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keep cutout alpha on exposed/snow-topped leaves instead of turning restored fluff into opaque squares.
 * Does not hook shouldCullSide: the leaf cube's directional culling remains the other mod's decision.
 */
@Pseudo
@Mixin(targets = "toni.sodiumleafculling.LeafCulling", remap = false)
public abstract class SodiumLeafTopAlphaMixin
{
    @Inject(method = "surroundedByLeaves", at = @At("HEAD"), cancellable = true, require = 0)
    private static void betterfoliage$keepTopCutout(BlockGetter level, BlockPos pos, CallbackInfoReturnable<Boolean> callback)
    {
        if (SodiumLeafCullingCompat.preserveTopAlpha()) callback.setReturnValue(false);
    }
}
