package com.eerussianguy.betterfoliage.compat;

import java.util.ArrayList;
import java.util.List;
import com.eerussianguy.betterfoliage.model.EclipticLeafQuads;
import com.eerussianguy.betterfoliage.model.EclipticLeafSprites;
import com.teamtea.eclipticseasons.client.core.ExtraModelManager;
import com.teamtea.eclipticseasons.client.core.ExtraRendererContext;
import com.teamtea.eclipticseasons.client.model.SnowyBakedModelWrapper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Loaded only by the optional ES mixin. Operates after ES has made its own visibility decisions. */
public final class EclipticLeafMerge
{
    private EclipticLeafMerge() {}

    public static List<BakedQuad> apply(ExtraRendererContext context, BakedModel model, BlockAndTintGetter view,
        BlockState state, BlockPos pos, Direction side, long seed, List<BakedQuad> quads)
    {
        if (side == null || quads.isEmpty() || !EclipticLeafSprites.available()
            || !(state.getBlock() instanceof LeavesBlock) || context.isReplace()
            || !((Object) context instanceof EclipticLeafMergeState.Owner owner)) return quads;
        EclipticLeafMergeState session = owner.betterfoliage$leafMergeState();
        if (session.probing) return quads;
        BakedModel extra = context.getExtraModel();
        if (!(extra instanceof SnowyBakedModelWrapper<?> wrapper) || wrapper.getBindBlockType() != 4) return quads;

        if (model == extra)
        {
            // Remove only a snow face whose underlying leaf face was actually replaced in this render.
            return EclipticLeafQuads.removeMerged(quads, side, session.merged[side.ordinal()]);
        }
        if (model != context.getOriginalModel()) return quads;
        int index = EclipticLeafQuads.singleFace(quads, side);
        if (index < 0) return quads; // Multi-layer fruit/custom cube faces must not be merged independently.
        BakedQuad leaf = quads.get(index);
        List<BakedQuad> covers;
        session.probing = true;
        try
        {
            session.random.setSeed(seed);
            covers = extra.getQuads(state, side, session.random, context.getModelData(), ExtraModelManager.getRenderType(state));
            // In particular, retain ES' same-species/random neighbor snow-face culling.
            covers = ExtraModelManager.cancelTop(context, extra, view, state, pos, side, session.random, seed, covers, quads);
        }
        finally { session.probing = false; }
        int snowIndex = EclipticLeafQuads.singleFace(covers, side);
        if (snowIndex < 0) return quads;
        if (leaf.isTinted() && !session.hasTint)
        {
            session.tint = Minecraft.getInstance().getBlockColors().getColor(state, view, pos, 0) & 0xffffff;
            session.hasTint = true;
        }
        BakedQuad snow = covers.get(snowIndex);
        BakedQuad replacement = EclipticLeafQuads.merge(leaf, snow, session.tint);
        if (replacement == null) return quads;
        List<BakedQuad> result = new ArrayList<>(quads);
        result.set(index, replacement);
        session.merged[side.ordinal()] = snow;
        return result;
    }
}
