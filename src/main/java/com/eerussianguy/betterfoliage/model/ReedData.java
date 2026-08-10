package com.eerussianguy.betterfoliage.model;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelProperty;

public record ReedData(boolean eligible, float populationRoll, int model, int light)
{
    public static final ModelProperty<ReedData> PROPERTY = new ModelProperty<>();
    private static final long RANDOM_SALT = 0x6A09E667F3BCC909L;

    public static ReedData create(BlockAndTintGetter level, BlockPos pos)
    {
        BlockPos waterPos = pos.above();
        BlockPos airPos = pos.above(2);
        BlockState waterState = level.getBlockState(waterPos);
        BlockState airState = level.getBlockState(airPos);
        boolean eligible = waterState.is(Blocks.WATER) && airState.isAir();
        if (eligible)
        {
            var clientLevel = Minecraft.getInstance().level;
            if (clientLevel == null)
            {
                eligible = false;
            }
            else
            {
                var biome = clientLevel.getBiome(pos);
                eligible = !biome.is(BiomeTags.IS_BEACH) && !biome.is(BiomeTags.IS_OCEAN);
            }
        }

        RandomSource random = RandomSource.create(pos.asLong() ^ RANDOM_SALT);
        float populationRoll = random.nextFloat();
        int texture = random.nextInt(ReedBakedModelSet.TEXTURE_COUNT);
        int xOffset = random.nextInt(ReedBakedModelSet.OFFSET_STEPS);
        int zOffset = random.nextInt(ReedBakedModelSet.OFFSET_STEPS);
        int model = texture * ReedBakedModelSet.MODELS_PER_TEXTURE + xOffset * ReedBakedModelSet.OFFSET_STEPS + zOffset;

        int light = 0;
        if (eligible)
        {
            int waterLight = LevelRenderer.getLightColor(level, waterState, waterPos);
            int airLight = LevelRenderer.getLightColor(level, airState, airPos);
            light = LightTexture.pack(
                Math.max(LightTexture.block(waterLight), LightTexture.block(airLight)),
                Math.max(LightTexture.sky(waterLight), LightTexture.sky(airLight))
            );
        }
        return new ReedData(eligible, populationRoll, model, light);
    }

    public boolean shouldRender(double population)
    {
        return eligible && populationRoll < population;
    }
}
