package com.eerussianguy.betterfoliage.model;

import java.util.function.Function;


import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.*;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.model.geometry.IGeometryBakingContext;
import net.neoforged.neoforge.client.model.geometry.IUnbakedGeometry;

public record GrassModel(ResourceLocation dirt, ResourceLocation top, ResourceLocation overlay, boolean tint, ResourceLocation grassLocation, boolean renderReed) implements IUnbakedGeometry<GrassModel>
{
    @Override
    public BakedModel bake(IGeometryBakingContext owner, ModelBaker bakery, Function<Material, TextureAtlasSprite> spriteGetter, ModelState modelTransform, ItemOverrides overrides)
    {
        Function<ResourceLocation, TextureAtlasSprite> textureGetter = texture -> spriteGetter.apply(new Material(TextureAtlas.LOCATION_BLOCKS, texture));
        return new GrassBakedModel(dirt, top, overlay, tint, grassLocation, renderReed, textureGetter);
    }
}
