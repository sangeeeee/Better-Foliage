package com.eerussianguy.betterfoliage.model;

import java.io.Reader;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import com.google.gson.*;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.block.LeavesBlock;

/** Atlas-lifetime composites for ES' three standard leaf snow masks, separate from BF fluff. */
public final class EclipticLeafSprites
{
    private static final ResourceLocation[] MASKS = {
        ResourceLocation.parse("eclipticseasons:block/snow_overlay_leaves"),
        ResourceLocation.parse("eclipticseasons:block/snow_overlay_leaves_top"),
        ResourceLocation.parse("eclipticseasons:block/snow_spot_overlay_leaves")
    };
    private static final ResourceLocation SOLID_SNOW = ResourceLocation.parse("minecraft:block/snow");
    private static volatile Map<ResourceLocation, Entry> generated = Map.of();
    private static volatile boolean solidSnowOpaque;
    private static final Map<ResourceLocation, SnowCompositeSprites.SpriteSet> resolved = new ConcurrentHashMap<>();
    private record Entry(SnowPalette palette) {}
    private EclipticLeafSprites() {}

    public static void reset() { generated = Map.of(); solidSnowOpaque = false; resolved.clear(); }
    public static boolean available() { return solidSnowOpaque || !generated.isEmpty(); }

    static ResourceLocation id(ResourceLocation base, int variant, int color)
    {
        return ResourceLocation.fromNamespaceAndPath("betterfoliage", "generated/es_leaf/" + Integer.toHexString(color)
            + "/" + variant + "/" + base.getNamespace() + "/" + base.getPath());
    }

    static TextureAtlasSprite select(TextureAtlasSprite base, TextureAtlasSprite snow, boolean tinted, int color)
    {
        ResourceLocation overlay = snow.contents().name();
        if (overlay.equals(SOLID_SNOW)) return solidSnowOpaque ? snow : null;
        int variant = -1;
        for (int i = 0; i < MASKS.length; i++) if (MASKS[i].equals(overlay)) variant = i;
        if (variant < 0 || !generated.containsKey(base.contents().name())) return null;
        SnowCompositeSprites.SpriteSet set = resolved.computeIfAbsent(base.contents().name(), EclipticLeafSprites::resolve);
        return tinted ? set.select(color, variant) : set.untinted.length == 3 ? set.untinted[variant] : null;
    }

    private static SnowCompositeSprites.SpriteSet resolve(ResourceLocation base)
    {
        return resolve(base, Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS));
    }

    static SnowCompositeSprites.SpriteSet resolve(ResourceLocation base, Function<ResourceLocation, TextureAtlasSprite> getter)
    {
        Entry entry = generated.get(base);
        if (entry == null) return new SnowCompositeSprites.SpriteSet(new TextureAtlasSprite[0], null, null);
        TextureAtlasSprite[] white = new TextureAtlasSprite[3];
        for (int i = 0; i < 3; i++)
        {
            white[i] = getter.apply(id(base, i, 0xffffff));
            if (!white[i].contents().name().equals(id(base, i, 0xffffff)))
                return new SnowCompositeSprites.SpriteSet(new TextureAtlasSprite[0], null, null);
        }
        if (entry.palette == null) return new SnowCompositeSprites.SpriteSet(white, null, null);
        TextureAtlasSprite[][] colors = new TextureAtlasSprite[entry.palette.colors.length][3];
        for (int c = 0; c < colors.length; c++) for (int v = 0; v < 3; v++)
        {
            colors[c][v] = getter.apply(id(base, v, entry.palette.colors[c]));
            if (!colors[c][v].contents().name().equals(id(base, v, entry.palette.colors[c])))
                return new SnowCompositeSprites.SpriteSet(white, null, null);
        }
        return new SnowCompositeSprites.SpriteSet(white, entry.palette, colors);
    }

    static List<SpriteContents> generate(List<SpriteContents> original, ResourceManager resources, SnowPalette palette,
        Path cache, long budget, long diskBudget, int maxTextureSize, Set<ResourceLocation> roots)
    {
        reset();
        Map<ResourceLocation, SpriteContents> atlas = new HashMap<>();
        for (SpriteContents sprite : original) atlas.put(sprite.name(), sprite);
        SpriteContents top = atlas.get(SOLID_SNOW);
        solidSnowOpaque = top != null && opaque(top);
        SpriteContents[] masks = new SpriteContents[3];
        for (int i = 0; i < 3; i++)
        {
            masks[i] = atlas.get(MASKS[i]);
            if (masks[i] == null || masks[i].width() != masks[i].getOriginalImage().getWidth()
                || masks[i].height() != masks[i].getOriginalImage().getHeight()) return original;
        }
        long area = original.stream().mapToLong(s -> (long) s.width() * s.height()).sum();
        budget = Math.min(budget, Math.max(0L, ((long) maxTextureSize * maxTextureSize * 3 / 5 - area) * 16 / 3));
        Map<ResourceLocation, Boolean> candidates = candidates(SnowCompositeSprites.readModels(resources), roots);
        List<ResourceLocation> order = new ArrayList<>(candidates.keySet());
        order.sort(Comparator.<ResourceLocation, Boolean>comparing(candidates::get)
            .thenComparing(name -> !name.getNamespace().equals("minecraft")).thenComparing(ResourceLocation::toString));
        Map<ResourceLocation, Entry> ready = new HashMap<>();
        List<SpriteContents> result = new ArrayList<>(original);
        int hits = 0, fallbacks = 0;
        for (ResourceLocation name : order)
        {
            SpriteContents base = atlas.get(name);
            if (base == null) continue;
            List<SpriteContents> images = new ArrayList<>();
            try
            {
                long bytes = 0;
                for (SpriteContents mask : masks)
                {
                    var spec = SnowCompositeSprites.spec(base, mask, name);
                    bytes += (long) spec.width() * spec.height() * 16 / 3;
                }
                SnowPalette colors = candidates.get(name) ? palette : null;
                if (colors != null && bytes * colors.colors.length > budget) { colors = null; fallbacks++; }
                if (bytes > budget) { fallbacks++; continue; }
                int[] values = colors == null ? new int[]{0xffffff} : colors.colors;
                List<SnowTextureCache.ImageSpec> specs = new ArrayList<>(values.length * 3);
                for (int color : values) for (int v = 0; v < 3; v++)
                {
                    ResourceLocation target = id(name, v, color);
                    if (atlas.containsKey(target)) throw new IllegalArgumentException("generated ES sprite ID collision");
                    specs.add(SnowCompositeSprites.spec(base, masks[v], target));
                }
                // A distinct source key prevents overwriting the same texture's BF fluff cache bundle.
                Path file = cache == null ? null : SnowTextureCache.file(cache,
                    ResourceLocation.fromNamespaceAndPath("betterfoliage", "es_leaf/" + name.getNamespace() + "/" + name.getPath()));
                String key = file == null ? "" : SnowTextureCache.fingerprint(base, masks, values);
                List<SpriteContents> cached = file == null ? null : SnowTextureCache.read(file, key, specs, base.metadata());
                if (cached != null) { images = cached; hits++; }
                else
                {
                    for (int color : values) for (int v = 0; v < 3; v++)
                        images.add(SnowCompositeSprites.compose(base, masks[v], id(name, v, color), color));
                    if (file != null) SnowTextureCache.write(file, key, images);
                }
                result.addAll(images);
                budget -= bytes * values.length;
                ready.put(name, new Entry(colors));
            }
            catch (RuntimeException failure)
            {
                images.forEach(SpriteContents::close);
                LogUtils.getLogger().warn("Keeping ES layered leaf snow for {}: {}", name, failure.toString());
            }
        }
        generated = Map.copyOf(ready);
        if (cache != null) SnowTextureCache.trim(cache, diskBudget);
        LogUtils.getLogger().info("ES leaf composites: {} source textures, {} cache hits, {} budget fallbacks", ready.size(), hits, fallbacks);
        return result;
    }

    private static boolean opaque(SpriteContents sprite)
    {
        var image = sprite.getOriginalImage();
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++)
            if (image.getPixelRGBA(x, y) >>> 24 != 255) return false;
        return true;
    }

    static Set<ResourceLocation> activeLeafModels(ResourceManager resources)
    {
        Set<ResourceLocation> roots = new HashSet<>();
        resources.listResources("blockstates", id -> id.getPath().endsWith(".json")).forEach((id, resource) -> {
            String path = id.getPath();
            ResourceLocation block = ResourceLocation.fromNamespaceAndPath(id.getNamespace(), path.substring(12, path.length() - 5));
            if (!BuiltInRegistries.BLOCK.containsKey(block) || !(BuiltInRegistries.BLOCK.get(block) instanceof LeavesBlock)) return;
            try (Reader reader = resource.openAsReader()) { collectModels(JsonParser.parseReader(reader), roots); }
            catch (Exception ignored) { /* Custom blockstate formats keep the original ES overlay. */ }
        });
        return roots;
    }

    private static void collectModels(JsonElement element, Set<ResourceLocation> out)
    {
        if (element.isJsonArray()) for (JsonElement child : element.getAsJsonArray()) collectModels(child, out);
        else if (element.isJsonObject()) for (var entry : element.getAsJsonObject().entrySet())
        {
            if (entry.getKey().equals("model") && entry.getValue().isJsonPrimitive()) out.add(ResourceLocation.parse(entry.getValue().getAsString()));
            else collectModels(entry.getValue(), out);
        }
    }

    /** Only models referenced by registered leaf blockstates, including weighted variants and pack parents. */
    static Map<ResourceLocation, Boolean> candidates(Map<ResourceLocation, JsonObject> models, Set<ResourceLocation> roots)
    {
        Map<ResourceLocation, Boolean> result = new HashMap<>();
        for (ResourceLocation root : roots)
        {
            try
            {
                JsonObject cursor = models.get(root);
                Map<String, String> textures = new HashMap<>();
                JsonArray elements = null;
                Set<JsonObject> visited = Collections.newSetFromMap(new IdentityHashMap<>());
                while (cursor != null && visited.add(cursor))
                {
                    if (cursor.has("loader") && cursor.get("loader").getAsString().equals("betterfoliage:leaves"))
                    {
                        result.merge(ResourceLocation.parse(cursor.get("leaves").getAsString()),
                            !cursor.has("tintLeaves") || cursor.get("tintLeaves").getAsBoolean(), Boolean::logicalOr);
                        break;
                    }
                    if (elements == null && cursor.has("elements")) elements = cursor.getAsJsonArray("elements");
                    if (cursor.has("textures")) cursor.getAsJsonObject("textures").entrySet()
                        .forEach(entry -> textures.putIfAbsent(entry.getKey(), entry.getValue().getAsString()));
                    cursor = cursor.has("parent") ? models.get(ResourceLocation.parse(cursor.get("parent").getAsString())) : null;
                }
                if (elements == null) continue;
                for (JsonElement element : elements)
                {
                    JsonObject faces = element.getAsJsonObject().getAsJsonObject("faces");
                    if (faces == null) continue;
                    for (JsonElement value : faces.asMap().values())
                    {
                        JsonObject face = value.getAsJsonObject();
                        if (!face.has("cullface")) continue; // Excludes pack bushy planes; runtime validates cube geometry too.
                        String texture = face.get("texture").getAsString();
                        Set<String> aliases = new HashSet<>();
                        while (texture != null && texture.startsWith("#") && aliases.add(texture)) texture = textures.get(texture.substring(1));
                        if (texture != null && !texture.startsWith("#")) result.merge(ResourceLocation.parse(texture),
                            !SnowCompositeSprites.untinted(face), Boolean::logicalOr);
                    }
                }
            }
            catch (RuntimeException ignored) { /* Unsupported models are never suppressed. */ }
        }
        return result;
    }
}
