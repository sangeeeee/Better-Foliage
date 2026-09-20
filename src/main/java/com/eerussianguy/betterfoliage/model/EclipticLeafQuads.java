package com.eerussianguy.betterfoliage.model;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.client.model.IQuadTransformer;

/** Conservative cube-face merge. Never changes BF fluff or accepts rotated/cropped/colored custom geometry. */
public final class EclipticLeafQuads
{
    private EclipticLeafQuads() {}

    public static boolean fullFace(BakedQuad quad, Direction side)
    {
        if (side == null || quad.getDirection() != side) return false;
        int axis = switch (side.getAxis()) { case X -> 0; case Y -> 1; case Z -> 2; };
        float boundary = side.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1 : 0;
        int corners = 0;
        int[] data = quad.getVertices();
        for (int v = 0; v < 4; v++)
        {
            int offset = v * IQuadTransformer.STRIDE;
            int corner = 0, bit = 0;
            for (int a = 0; a < 3; a++)
            {
                float coordinate = Float.intBitsToFloat(data[offset + IQuadTransformer.POSITION + a]);
                if (!Float.isFinite(coordinate)) return false;
                if (a == axis) { if (Math.abs(coordinate - boundary) > 0.00001f) return false; }
                else
                {
                    if (Math.abs(coordinate - 1) <= 0.00001f) corner |= 1 << bit;
                    else if (Math.abs(coordinate) > 0.00001f) return false;
                    bit++;
                }
            }
            corners |= 1 << corner;
        }
        return corners == 15;
    }

    public static BakedQuad merge(BakedQuad leaf, BakedQuad snow, int color)
    {
        if (leaf.getTintIndex() != -1 && leaf.getTintIndex() != 0 || snow.isTinted()) return null;
        if (!compatible(leaf, snow)) return null;
        TextureAtlasSprite composite = EclipticLeafSprites.select(leaf.getSprite(), snow.getSprite(), leaf.isTinted(), color);
        return composite == null ? null : retexture(leaf, composite);
    }

    static boolean compatible(BakedQuad leaf, BakedQuad snow)
    {
        Direction side = leaf.getDirection();
        if (!fullFace(leaf, side) || !fullFace(snow, side)) return false;
        for (int a = 0; a < 4; a++)
        {
            if (leaf.getVertices()[a * IQuadTransformer.STRIDE + IQuadTransformer.COLOR] != -1
                || snow.getVertices()[a * IQuadTransformer.STRIDE + IQuadTransformer.COLOR] != -1) return false;
            int b = matchingVertex(leaf, a, snow);
            if (b < 0) return false;
            float u = local(leaf, a, false), v = local(leaf, a, true);
            if (!(close(u, 0) || close(u, 1)) || !(close(v, 0) || close(v, 1))) return false;
            if (!close(u, local(snow, b, false)) || !close(v, local(snow, b, true))) return false;
        }
        // Require a complete UV square, not a degenerate face repeatedly sampling one texel.
        int corners = 0;
        for (int a = 0; a < 4; a++) corners |= 1 << ((local(leaf, a, false) > 0.5f ? 1 : 0) | (local(leaf, a, true) > 0.5f ? 2 : 0));
        return corners == 15;
    }

    public static boolean sameOverlay(BakedQuad a, BakedQuad b)
    {
        return a.getSprite() == b.getSprite() && a.getTintIndex() == b.getTintIndex() && compatible(a, b);
    }

    public static int singleFace(List<BakedQuad> quads, Direction side)
    {
        int found = -1;
        for (int i = 0; i < quads.size(); i++) if (fullFace(quads.get(i), side))
        {
            if (found >= 0) return -1;
            found = i;
        }
        return found;
    }

    public static List<BakedQuad> removeMerged(List<BakedQuad> quads, Direction side, BakedQuad committed)
    {
        if (committed == null) return quads;
        int index = singleFace(quads, side);
        if (index < 0 || !sameOverlay(committed, quads.get(index))) return quads;
        List<BakedQuad> result = new ArrayList<>(quads);
        result.remove(index);
        return result;
    }

    static BakedQuad retexture(BakedQuad leaf, TextureAtlasSprite target)
    {
        int[] vertices = Arrays.copyOf(leaf.getVertices(), leaf.getVertices().length);
        for (int v = 0; v < 4; v++)
        {
            int uv = v * IQuadTransformer.STRIDE + IQuadTransformer.UV0;
            vertices[uv] = Float.floatToRawIntBits(target.getU(local(leaf, v, false)));
            vertices[uv + 1] = Float.floatToRawIntBits(target.getV(local(leaf, v, true)));
        }
        return new BakedQuad(vertices, -1, leaf.getDirection(), target, leaf.isShade(), leaf.hasAmbientOcclusion());
    }

    private static int matchingVertex(BakedQuad leaf, int vertex, BakedQuad snow)
    {
        for (int b = 0; b < 4; b++)
        {
            boolean match = true;
            for (int axis = 0; axis < 3; axis++)
                match &= close(Float.intBitsToFloat(leaf.getVertices()[vertex * IQuadTransformer.STRIDE + IQuadTransformer.POSITION + axis]),
                    Float.intBitsToFloat(snow.getVertices()[b * IQuadTransformer.STRIDE + IQuadTransformer.POSITION + axis]));
            if (match) return b;
        }
        return -1;
    }

    private static float local(BakedQuad quad, int vertex, boolean v)
    {
        float uv = Float.intBitsToFloat(quad.getVertices()[vertex * IQuadTransformer.STRIDE + IQuadTransformer.UV0 + (v ? 1 : 0)]);
        return v ? quad.getSprite().getVOffset(uv) : quad.getSprite().getUOffset(uv);
    }

    // FaceBakery applies UV shrink; tolerate its standard edge inset, but not custom crops.
    private static boolean close(float a, float b) { return Math.abs(a - b) <= 0.005f; }
}
