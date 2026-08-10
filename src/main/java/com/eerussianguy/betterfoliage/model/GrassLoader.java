package com.eerussianguy.betterfoliage.model;

import javax.annotation.ParametersAreNonnullByDefault;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

import com.eerussianguy.betterfoliage.Helpers;
import net.neoforged.neoforge.client.model.geometry.IGeometryLoader;


@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class GrassLoader implements IGeometryLoader<GrassModel>
{
    @Override
    public GrassModel read(JsonObject json, JsonDeserializationContext deserializationContext)
    {
        ResourceLocation dirt = Helpers.requireID(json, "dirt");
        ResourceLocation top = Helpers.requireID(json, "top");
        ResourceLocation overlay = Helpers.identifierOrEmpty(json, "overlay");
        boolean tint = GsonHelper.getAsBoolean(json, "tint", false);
        ResourceLocation grass = Helpers.identifierOrEmpty(json, "grass");
        boolean renderReed = GsonHelper.getAsBoolean(json, "renderReed", false);

        return new GrassModel(dirt, top, overlay, tint, grass, renderReed);
    }
}
