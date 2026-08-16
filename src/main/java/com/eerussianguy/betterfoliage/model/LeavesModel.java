package com.eerussianguy.betterfoliage.model;

import java.util.function.Function;

import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.*;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.model.geometry.IGeometryBakingContext;
import net.neoforged.neoforge.client.model.geometry.IUnbakedGeometry;

public record LeavesModel(ResourceLocation leaves, ResourceLocation fluff, ResourceLocation overlay, boolean tintLeaves, boolean tintOverlay) implements IUnbakedGeometry<LeavesModel>
{
    @Override
    public BakedModel bake(IGeometryBakingContext owner, ModelBaker bakery, Function<Material, TextureAtlasSprite> spriteGetter, ModelState modelTransform, ItemOverrides overrides)
    {
        Function<ResourceLocation, TextureAtlasSprite> textureGetter = texture -> spriteGetter.apply(new Material(TextureAtlas.LOCATION_BLOCKS, texture));
        return new LeavesBakedModel(leaves, fluff, overlay, tintLeaves, tintOverlay, textureGetter);
    }

}
