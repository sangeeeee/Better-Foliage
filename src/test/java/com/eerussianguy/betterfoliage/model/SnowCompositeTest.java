package com.eerussianguy.betterfoliage.model;

import java.util.*;
import com.google.gson.*;
import net.minecraft.resources.ResourceLocation;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.server.packs.resources.ResourceMetadata;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.client.model.IQuadTransformer;

public final class SnowCompositeTest
{
    private static int checks;
    public static void main(String[] args) throws Exception
    {
        Random random = new Random(73);
        for (int i = 0; i < 1000; i++)
        {
            int leaf = random.nextInt(), snow = random.nextInt();
            check(SnowCompositeSprites.compositePixel(leaf, snow & 0xffffff) == leaf, "transparent preserves leaf");
            check(SnowCompositeSprites.compositePixel(leaf, snow | 0xff000000) == (snow | 0xff000000), "opaque snow replaces leaf");
            check(SnowCompositeSprites.compositePixel(leaf, (snow & 0xffffff) | 0x80000000)
                == ((snow & 0xffffff) | 0x80000000), "nonzero-alpha replacement");
        }
        check(SnowCompositeSprites.untinted(json("{}")), "absent tint");
        check(SnowCompositeSprites.untinted(json("{\"tintindex\":-1}")), "explicit no tint");
        check(!SnowCompositeSprites.untinted(json("{\"tintindex\":0}")), "tint zero is enabled");
        check(!SnowCompositeSprites.untinted(json("{\"tintindex\":2}")), "custom tint index");
        check(!SnowCompositeSprites.untinted(json("{\"tintindex\":-2}")), "matches BakedQuad sentinel precisely");
        Map<ResourceLocation, JsonObject> models = new HashMap<>();
        models.put(id("test:explicit"), json("{\"loader\":\"betterfoliage:leaves\",\"fluff\":\"test:gray_but_untinted\",\"tintLeaves\":false,\"tintOverlay\":true}"));
        models.put(id("test:default"), json("{\"loader\":\"betterfoliage:leaves\",\"fluff\":\"test:color_but_tinted\"}"));
        models.put(id("test:tinted"), json("{\"loader\":\"betterfoliage:leaves\",\"fluff\":\"test:tinted\",\"tintLeaves\":true}"));
        models.put(id("test:parent"), json("{\"textures\":{\"bush\":\"#leaves\",\"leaves\":\"test:bushy_parent\"},\"elements\":[{\"faces\":{\"north\":{\"texture\":\"#bush\"}}}]}"));
        models.put(id("test:child"), json("{\"parent\":\"test:parent\",\"textures\":{\"leaves\":\"test:bushy_child\"}}"));
        models.put(id("test:tinted_pack"), json("{\"textures\":{\"bush\":\"test:bushy_tinted\"},\"elements\":[{\"faces\":{\"north\":{\"texture\":\"#bush\",\"tintindex\":0}}}]}"));
        models.put(id("test:loop"), json("{\"parent\":\"test:loop\",\"textures\":{\"bush\":\"#bush\"},\"elements\":[{\"faces\":{\"north\":{\"texture\":\"#bush\"}}}]}"));
        Set<ResourceLocation> found = SnowCompositeSprites.candidates(models);
        check(found.equals(Set.of(id("test:gray_but_untinted"), id("test:bushy_parent"), id("test:bushy_child"))),
            "model flags, inheritance, child texture override, aliases and cycle handling");
        check(SnowCompositeSprites.candidates(models, true).equals(Set.of(id("test:color_but_tinted"),
            id("test:tinted"), id("test:bushy_tinted"))), "tinted BF and pack models are distinct candidates");
        check(!SnowCompositeSprites.id(id("a:x"), 0).equals(SnowCompositeSprites.id(id("b:x"), 0)), "namespace isolation");
        check(!SnowCompositeSprites.id(id("a:x"), 0).equals(SnowCompositeSprites.id(id("a:x"), 1)), "snow variants");
        imageAndUvTests();
        reloadTests();
        SnowPaletteCacheTest.run();
        if (args.length > 0) resourcePackTests(java.nio.file.Path.of(args[0]));
        System.out.println("Snow composites: " + checks + " checks passed");
    }
    private static void resourcePackTests(java.nio.file.Path path) throws Exception
    {
        Map<ResourceLocation, JsonObject> models = new HashMap<>();
        byte[] foliageMap;
        try (var zip = new java.util.zip.ZipFile(path.toFile()))
        {
            try (var input = zip.getInputStream(zip.getEntry("assets/minecraft/textures/colormap/foliage.png")))
            {
                foliageMap = input.readAllBytes();
            }
            for (var entries = zip.entries(); entries.hasMoreElements();)
            {
                var entry = entries.nextElement();
                String name = entry.getName();
                if (!name.startsWith("assets/minecraft/models/") || !name.endsWith(".json")) continue;
                try (var reader = new java.io.InputStreamReader(zip.getInputStream(entry), java.nio.charset.StandardCharsets.UTF_8))
                {
                    models.put(id("minecraft:" + name.substring("assets/minecraft/models/".length(), name.length() - 5)),
                        JsonParser.parseReader(reader).getAsJsonObject());
                }
            }
        }
        var tinted = SnowCompositeSprites.candidates(models, true);
        var untinted = SnowCompositeSprites.candidates(models);
        check(tinted.contains(id("minecraft:block/oak_leaves_bushy")), "real Stay True oak tinted");
        check(tinted.contains(id("minecraft:block/oak_leaves_bushy1")), "real Stay True alternate oak tinted");
        check(untinted.contains(id("minecraft:block/birch_leaves_bushy")), "real Stay True birch untinted");
        check(!tinted.contains(id("minecraft:block/birch_leaves_bushy")), "real Stay True birch not accidentally recolored");
        System.out.println("Stay True pack: " + tinted.size() + " tinted bushy textures, " + untinted.size() + " untinted bushy textures recognized");
        var manager = SnowPaletteCacheTest.manager(Map.of(id("minecraft:textures/colormap/foliage.png"),
            new net.minecraft.server.packs.resources.Resource(null, () -> new java.io.ByteArrayInputStream(foliageMap))));
        SnowPalette palette = SnowPalette.load(manager, 8, 1024, 8, "");
        int covered = 0, total = 0, largestError = 0;
        try (NativeImage map = NativeImage.read(new java.io.ByteArrayInputStream(foliageMap)))
        {
            for (int y = 0; y < map.getHeight(); y++) for (int x = 0; x < map.getWidth(); x++)
            {
                int pixel = map.getPixelRGBA(x, y);
                int rgb = (pixel & 255) << 16 | (pixel & 0xff00) | (pixel >> 16 & 255);
                int index = palette.select(rgb);
                total++;
                if (index >= 0)
                {
                    covered++;
                    largestError = Math.max(largestError, SnowPalette.channelError(rgb, palette.colors[index]));
                }
            }
        }
        check(largestError <= 8, "actual Stay True colormap error bound");
        System.out.println("Stay True default palette: " + palette.colors.length + " colors, " + covered + "/" + total
            + " colormap samples covered, max selected RGB channel error " + largestError);
    }
    private static void imageAndUvTests()
    {
        NativeImage leaves = new NativeImage(2, 4, true), snow = new NativeImage(4, 4, true);
        for (int y = 0; y < 4; y++) for (int x = 0; x < 2; x++) leaves.setPixelRGBA(x, y, y < 2 ? 0xff224466 : 0xff557799);
        snow.setPixelRGBA(0, 0, 0xffffffff);
        try (SpriteContents base = new SpriteContents(id("test:base"), new FrameSize(2, 2), leaves, ResourceMetadata.EMPTY);
             SpriteContents cover = new SpriteContents(id("test:snow"), new FrameSize(4, 4), snow, ResourceMetadata.EMPTY);
             SpriteContents combined = SnowCompositeSprites.compose(base, cover, id("test:combined")))
        {
            check(combined.width() == 4 && combined.height() == 4, "preserve higher snow resolution");
            check(combined.getOriginalImage().getHeight() == 8, "preserve animation frame grid");
            check(combined.getOriginalImage().getPixelRGBA(0, 0) == 0xffffffff
                && combined.getOriginalImage().getPixelRGBA(0, 4) == 0xffffffff, "overlay every frame");
            check(combined.getOriginalImage().getPixelRGBA(3, 3) == 0xff224466
                && combined.getOriginalImage().getPixelRGBA(3, 7) == 0xff557799, "preserve each leaf frame");
            check(combined.metadata() == base.metadata(), "preserve animation metadata");
            check(leaves.getPixelRGBA(0, 0) == 0xff224466, "source remains unchanged");
            TextureAtlasSprite original = new TestSprite(base, 16, 32), target = new TestSprite(combined, 128, 64);
            int[] vertices = new int[4 * IQuadTransformer.STRIDE];
            for (int i = 0; i < 4; i++)
            {
                int uv = i * IQuadTransformer.STRIDE + IQuadTransformer.UV0;
                vertices[uv] = Float.floatToRawIntBits(original.getU(i % 2));
                vertices[uv + 1] = Float.floatToRawIntBits(original.getV(i / 2));
            }
            BakedQuad quad = new BakedQuad(vertices, 0, Direction.NORTH, original, false, false);
            List<BakedQuad> output = new ArrayList<>();
            LeavesBakedModel.appendPositionRotation(output, List.of(quad), List.of(), 0, target);
            check(output.size() == 1 && output.getFirst().getSprite() == target && !output.getFirst().isTinted(), "one untinted replacement quad");
            for (int i = 0; i < 4; i++)
            {
                int uv = i * IQuadTransformer.STRIDE + IQuadTransformer.UV0;
                check(Float.intBitsToFloat(output.getFirst().getVertices()[uv]) == target.getU(i % 2), "local U remapped to composite sprite");
                check(Float.intBitsToFloat(output.getFirst().getVertices()[uv + 1]) == target.getV(i / 2), "local V remapped to composite sprite");
            }
        }
    }
    private static void reloadTests()
    {
        Map<ResourceLocation, net.minecraft.server.packs.resources.Resource> resources = new HashMap<>();
        byte[] model = "{\"loader\":\"betterfoliage:leaves\",\"fluff\":\"test:reload\",\"tintLeaves\":false}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        resources.put(id("test:models/block/leaf.json"), new net.minecraft.server.packs.resources.Resource(null,
            () -> new java.io.ByteArrayInputStream(model)));
        var manager = (net.minecraft.server.packs.resources.ResourceManager) java.lang.reflect.Proxy.newProxyInstance(
            SnowCompositeTest.class.getClassLoader(), new Class<?>[]{net.minecraft.server.packs.resources.ResourceManager.class},
            (proxy, method, args) -> {
                if (method.getName().equals("listResources")) return resources;
                throw new UnsupportedOperationException(method.getName());
            });
        List<SpriteContents> originals = new ArrayList<>();
        List<SpriteContents> first = List.of(), second = List.of();
        try
        {
            originals.add(new SpriteContents(id("test:reload"), new FrameSize(2, 2), new NativeImage(2, 2, true), ResourceMetadata.EMPTY));
            for (int i = 0; i < 3; i++)
            {
                NativeImage cover = new NativeImage(2, 2, true);
                cover.setPixelRGBA(0, 0, 0xffffffff);
                originals.add(new SpriteContents(id("betterfoliage:block/better_leaves_snowed_" + i), new FrameSize(2, 2), cover, ResourceMetadata.EMPTY));
            }
            first = SnowCompositeSprites.generate(originals, manager);
            check(first.size() == 7 && originals.size() == 4, "three atlas additions without mutating input list");
            originals.get(1).getOriginalImage().setPixelRGBA(0, 0, 0xff998877);
            second = SnowCompositeSprites.generate(originals, manager);
            check(first.get(4).getOriginalImage().getPixelRGBA(0, 0) == 0xffffffff, "previous atlas remains immutable");
            check(second.get(4).getOriginalImage().getPixelRGBA(0, 0) == 0xff998877, "reload recomposes changed snow pixels");
            resources.clear();
            check(SnowCompositeSprites.generate(originals, manager).size() == 4, "removed model produces no new composites");
            check(SnowCompositeSprites.find(id("test:reload"), ignored -> { throw new AssertionError("stale composite lookup"); }).length == 0,
                "reload invalidates availability cache");
        }
        finally
        {
            for (int i = 4; i < first.size(); i++) first.get(i).close();
            for (int i = 4; i < second.size(); i++) second.get(i).close();
            originals.forEach(SpriteContents::close);
        }
    }

    private static final class TestSprite extends TextureAtlasSprite
    {
        TestSprite(SpriteContents contents, int x, int y)
        {
            super(id("minecraft:textures/atlas/blocks.png"), contents, 256, 256, x, y);
        }
    }
    private static JsonObject json(String value) { return JsonParser.parseString(value).getAsJsonObject(); }
    private static ResourceLocation id(String value) { return ResourceLocation.parse(value); }
    private static void check(boolean value, String description)
    {
        checks++;
        if (!value) throw new AssertionError(description);
    }
}
