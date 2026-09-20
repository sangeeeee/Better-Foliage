package com.eerussianguy.betterfoliage.model;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;

/** Capture the real mod/biome tint during mesh construction, not from a guessed texture color. */
final class SnowTintData
{
    private static final ModelProperty<Integer> COLOR = new ModelProperty<>();
    private SnowTintData() {}

    static ModelData append(BlockAndTintGetter level, BlockPos pos, BlockState state, ModelData data)
    {
        if (!SnowyLeavesData.isSnowy(data) || !SnowCompositeSprites.hasPalette()) return data;
        int color = Minecraft.getInstance().getBlockColors().getColor(state, level, pos, 0) & 0xffffff;
        if (Integer.valueOf(color).equals(data.get(COLOR))) return data;
        return data.derive().with(COLOR, color).build();
    }

    static Integer color(ModelData data) { return data.get(COLOR); }
}
