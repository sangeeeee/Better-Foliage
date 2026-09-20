package com.eerussianguy.betterfoliage.mixin;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import com.eerussianguy.betterfoliage.model.SnowCompositeSprites;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.SpriteLoader;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SpriteLoader.class)
public abstract class SnowCompositeSpriteLoaderMixin
{
    @Shadow @Final private ResourceLocation location;
    @Shadow @Final private int maxSupportedTextureSize;
    @Unique private ResourceManager betterfoliage$resources;

    @Inject(method = "loadAndStitch(Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/resources/ResourceLocation;ILjava/util/concurrent/Executor;Ljava/util/Collection;)Ljava/util/concurrent/CompletableFuture;", at = @At("HEAD"))
    private void betterfoliage$captureResources(ResourceManager resources, ResourceLocation atlas, int mip,
        Executor executor, Collection<MetadataSectionSerializer<?>> metadata,
        CallbackInfoReturnable<CompletableFuture<SpriteLoader.Preparations>> callback)
    {
        if (TextureAtlas.LOCATION_BLOCKS.equals(location)) betterfoliage$resources = resources;
    }

    @ModifyVariable(method = "stitch", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private List<SpriteContents> betterfoliage$composeSnow(List<SpriteContents> sprites)
    {
        return TextureAtlas.LOCATION_BLOCKS.equals(location)
            ? SnowCompositeSprites.generate(sprites, betterfoliage$resources, maxSupportedTextureSize) : sprites;
    }
}
