package com.eerussianguy.betterfoliage.mixin;

import com.eerussianguy.betterfoliage.compat.EclipticLeafMergeState;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.teamtea.eclipticseasons.client.core.ExtraRendererContext", remap = false)
public abstract class EclipticLeafContextMixin implements EclipticLeafMergeState.Owner
{
    @Unique private EclipticLeafMergeState betterfoliage$state;

    @Override public EclipticLeafMergeState betterfoliage$leafMergeState()
    {
        if (betterfoliage$state == null) betterfoliage$state = new EclipticLeafMergeState();
        return betterfoliage$state;
    }

    @Inject(method = "setOriginalModel", at = @At("HEAD"), require = 0)
    private void betterfoliage$begin(CallbackInfoReturnable<?> callback)
    {
        if (betterfoliage$state != null) betterfoliage$state.reset();
    }

    @Inject(method = "resetAll", at = @At("HEAD"), require = 0)
    private void betterfoliage$end(CallbackInfo callback)
    {
        if (betterfoliage$state != null) betterfoliage$state.reset();
    }
}
