package com.eerussianguy.betterfoliage.model;

import java.io.ByteArrayInputStream;
import java.nio.file.*;
import java.util.*;
import com.google.gson.*;
import com.mojang.blaze3d.platform.NativeImage;
import com.eerussianguy.betterfoliage.compat.EclipticLeafMergeState;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.*;
import net.neoforged.neoforge.client.model.IQuadTransformer;
import org.joml.Vector3f;

final class EclipticLeafTest
{
    private static int checks;
    static void run() throws Exception
    {
        Map<ResourceLocation, JsonObject> models = new HashMap<>();
        models.put(id("test:block/plain"), json("{\"loader\":\"betterfoliage:leaves\",\"leaves\":\"test:block/plain\",\"fluff\":\"test:block/plain_fluff\",\"tintLeaves\":false}"));
        models.put(id("test:block/colored"), json("{\"parent\":\"test:block/cube\",\"textures\":{\"all\":\"test:block/colored\"}}"));
        models.put(id("test:block/cube"), json("{\"textures\":{\"side\":\"#all\"},\"elements\":[{\"faces\":{\"north\":{\"texture\":\"#side\",\"cullface\":\"north\",\"tintindex\":0},\"east\":{\"texture\":\"test:bushy\",\"tintindex\":0}}}]}"));
        models.put(id("absent:block/unloaded"), json("{\"loader\":\"betterfoliage:leaves\",\"leaves\":\"absent:block/leaves\"}"));
        Set<ResourceLocation> roots = Set.of(id("test:block/plain"), id("test:block/colored"));
        check(EclipticLeafSprites.candidates(models, roots).equals(Map.of(id("test:block/plain"), false, id("test:block/colored"), true)),
            "active core textures only; model tint flags and inheritance; exclude bushy and unloaded mods");
        Map<ResourceLocation, Resource> resources = new HashMap<>();
        models.forEach((name, model) -> resources.put(ResourceLocation.fromNamespaceAndPath(name.getNamespace(), "models/" + name.getPath() + ".json"),
            new Resource(null, () -> new ByteArrayInputStream(model.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)))));
        ResourceManager manager = SnowPaletteCacheTest.manager(resources);
        List<SpriteContents> original = new ArrayList<>();
        for (String name : List.of("test:block/plain", "test:block/colored", "minecraft:block/snow",
            "eclipticseasons:block/snow_overlay_leaves", "eclipticseasons:block/snow_overlay_leaves_top", "eclipticseasons:block/snow_spot_overlay_leaves"))
        {
            NativeImage image = new NativeImage(16, 16, true);
            if (!name.startsWith("eclipticseasons"))
                for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) image.setPixelRGBA(x, y, name.startsWith("minecraft") ? 0xffffffff : 0xffaabbcc);
            else image.setPixelRGBA(0, 0, 0xfff0f1f2);
            original.add(new SpriteContents(id(name), new FrameSize(16, 16), image, ResourceMetadata.EMPTY));
        }
        SnowPalette palette = new SnowPalette(new int[]{0xffffff, 0x619961});
        Path directory = Files.createTempDirectory(Path.of("."), "es-leaf-test-");
        List<SpriteContents> first = List.of(), second = List.of(), limited = List.of();
        try
        {
            first = EclipticLeafSprites.generate(original, manager, palette, directory, 1_000_000, 1_000_000, 4096, roots);
            check(first.size() == original.size() + 3 + 6, "untinted three masks, tinted two palette colors times three masks");
            try (var files = Files.list(directory)) { check(files.count() == 2, "one bundle per core texture"); }
            second = EclipticLeafSprites.generate(original, manager, palette, directory, 1_000_000, 1_000_000, 4096, roots);
            Map<ResourceLocation, TextureAtlasSprite> atlas = new HashMap<>();
            for (SpriteContents sprite : second) atlas.put(sprite.name(), new TestSprite(sprite, 64));
            var plainSet = EclipticLeafSprites.resolve(id("test:block/plain"), atlas::get);
            var tintedSet = EclipticLeafSprites.resolve(id("test:block/colored"), atlas::get);
            check(plainSet.untinted.length == 3 && plainSet.select(0x619961, 0) == null, "untinted base never recolored");
            check(tintedSet.select(0xff0000, 2) != null, "distant colors still select finite palette");
            check(tintedSet.select(0x619961, 1).contents().getOriginalImage().getPixelRGBA(1, 1) == SnowPalette.tint(0xffaabbcc, 0x619961), "leaf color baked into core composite");
            check(tintedSet.select(0x619961, 1).contents().getOriginalImage().getPixelRGBA(0, 0) == 0xfff0f1f2, "snow mask stays white");
            for (int i = original.size(); i < first.size(); i++)
                for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++)
                    check(first.get(i).getOriginalImage().getPixelRGBA(x, y) == second.get(i).getOriginalImage().getPixelRGBA(x, y), "ES cached pixels exact");
            TextureAtlasSprite base = atlas.get(id("test:block/plain")), snow = atlas.get(id("minecraft:block/snow"));
            for (Direction side : Direction.values())
            {
                BakedQuad leaf = face(base, side, 0), overlay = face(snow, side, -1);
                int[] snapshot = leaf.getVertices().clone();
                check(EclipticLeafQuads.fullFace(leaf, side), "all six standard cube planes eligible");
                check(EclipticLeafQuads.compatible(leaf, overlay), "matched geometry and local UV");
                var bakery = new FaceBakery();
                var faceUv = new BlockFaceUV(new float[]{0, 0, 16, 16}, 0);
                var leafFace = new BlockElementFace(side, 0, "", faceUv);
                var snowFace = new BlockElementFace(side, -1, "", faceUv);
                var realLeaf = bakery.bakeQuad(new Vector3f(0), new Vector3f(16), leafFace, base, side, BlockModelRotation.X0_Y0, null, true);
                var realSnow = bakery.bakeQuad(new Vector3f(0), new Vector3f(16), snowFace, snow, side, BlockModelRotation.X0_Y0, null, true);
                check(EclipticLeafQuads.compatible(realLeaf, realSnow), "real Minecraft FaceBakery UV inset accepted");
                BakedQuad merged = EclipticLeafQuads.merge(leaf, overlay, 0xff0000);
                check(merged != null && !merged.isTinted() && merged.getSprite() == snow, "opaque full snow requires no colored variants");
                check(Arrays.equals(snapshot, leaf.getVertices()), "shared original cube geometry immutable");
                EclipticLeafMergeState session = new EclipticLeafMergeState();
                session.merged[side.ordinal()] = overlay; session.hasTint = true; session.probing = true;
                session.reset();
                check(session.merged[side.ordinal()] == null && !session.hasTint && !session.probing, "no merge state leaks between blocks");
                BakedQuad cropped = face(base, side, 0);
                cropped.getVertices()[IQuadTransformer.UV0] = Float.floatToRawIntBits(base.getU(0.5f));
                check(!EclipticLeafQuads.compatible(cropped, overlay), "cropped UV retains layered snow");
                BakedQuad flipped = face(base, side, 0);
                for (int v = 0; v < 4; v++)
                {
                    int uv = v * IQuadTransformer.STRIDE + IQuadTransformer.UV0;
                    flipped.getVertices()[uv] = Float.floatToRawIntBits(base.getU(1 - base.getUOffset(Float.intBitsToFloat(flipped.getVertices()[uv]))));
                }
                check(!EclipticLeafQuads.compatible(flipped, overlay), "different UV orientation not silently merged");
                BakedQuad colored = face(base, side, -1); colored.getVertices()[IQuadTransformer.COLOR] = 0xff00ff00;
                check(!EclipticLeafQuads.compatible(colored, overlay), "custom vertex coloring preserved by fallback");
                check(EclipticLeafQuads.singleFace(List.of(leaf, colored), side) == -1, "multi-layer fruit/colored cube models remain untouched");
                List<BakedQuad> snowQuads = List.of(overlay);
                check(EclipticLeafQuads.removeMerged(snowQuads, side, null) == snowQuads, "no suppression before base replacement commits");
                check(EclipticLeafQuads.removeMerged(snowQuads, side, overlay).isEmpty() && snowQuads.size() == 1, "one committed replacement removes exactly one original snow face without mutation");
                check(EclipticLeafQuads.removeMerged(snowQuads, side, leaf) == snowQuads, "different snow texture never suppressed");
                BakedQuad shifted = face(base, side, -1);
                shifted.getVertices()[IQuadTransformer.POSITION] = Float.floatToRawIntBits(0.004f);
                check(!EclipticLeafQuads.fullFace(shifted, side), "offset/fluff geometry never mistaken for cube boundary");
            }
            limited = EclipticLeafSprites.generate(original, manager, palette, directory, 4200, 1_000_000, 4096, roots);
            check(limited.size() == original.size() + 3, "untinted core priority and bounded atlas allocation");
            EclipticLeafSprites.reset();
            check(!EclipticLeafSprites.available(), "resource reload/disabled integration clears core availability");
        }
        finally
        {
            for (var list : List.of(first, second, limited)) for (int i = original.size(); i < list.size(); i++) list.get(i).close();
            original.forEach(SpriteContents::close);
            EclipticLeafSprites.reset();
        }
        System.out.println("Ecliptic leaf composites: " + checks + " checks passed");
    }

    private static BakedQuad face(TextureAtlasSprite sprite, Direction side, int tint)
    {
        int[] data = new int[IQuadTransformer.STRIDE * 4];
        int axis = switch (side.getAxis()) { case X -> 0; case Y -> 1; case Z -> 2; };
        for (int v = 0; v < 4; v++)
        {
            int offset = v * IQuadTransformer.STRIDE, bit = 0;
            for (int a = 0; a < 3; a++)
            {
                float value = a == axis ? (side.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1 : 0) : (v >> bit++) & 1;
                data[offset + IQuadTransformer.POSITION + a] = Float.floatToRawIntBits(value);
            }
            data[offset + IQuadTransformer.COLOR] = -1;
            data[offset + IQuadTransformer.UV0] = Float.floatToRawIntBits(sprite.getU(v & 1));
            data[offset + IQuadTransformer.UV0 + 1] = Float.floatToRawIntBits(sprite.getV(v >> 1));
        }
        return new BakedQuad(data, tint, side, sprite, true, true);
    }

    private static final class TestSprite extends TextureAtlasSprite
    {
        TestSprite(SpriteContents contents, int x) { super(id("minecraft:textures/atlas/blocks.png"), contents, 1024, 1024, x, 32); }
    }
    private static ResourceLocation id(String value) { return ResourceLocation.parse(value); }
    private static JsonObject json(String value) { return JsonParser.parseString(value).getAsJsonObject(); }
    private static void check(boolean condition, String message) { checks++; if (!condition) throw new AssertionError(message); }
}
