package com.eerussianguy.betterfoliage.mixin;

import com.eerussianguy.betterfoliage.model.WaterPetalRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin
{
    @Inject(method = "blockChanged", at = @At("TAIL"))
    private void betterfoliage$refreshWaterPetals(BlockGetter level, BlockPos pos, BlockState oldState, BlockState newState, int flags, CallbackInfo callback)
    {
        WaterPetalRenderer.onBlockChanged((LevelRenderer) (Object) this, level, pos, oldState, newState);
    }
}
