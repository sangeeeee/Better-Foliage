package com.eerussianguy.betterfoliage.model;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.security.*;
import java.util.*;
import java.util.zip.*;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceMetadata;

/** One versioned gzip pixel bundle per base texture, atomically replaced when its content fingerprint changes. */
final class SnowTextureCache
{
    private static final int MAGIC = 0x42465303;
    record ImageSpec(ResourceLocation name, int width, int height, int frameWidth, int frameHeight) {}
    private SnowTextureCache() {}

    static Path file(Path directory, ResourceLocation base)
    {
        return directory.resolve(HexFormat.of().formatHex(digest().digest(base.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8))) + ".bfs");
    }

    static String fingerprint(SpriteContents base, SpriteContents[] snow, int[] colors)
    {
        MessageDigest hash = digest();
        update(hash, MAGIC);
        for (int color : colors) update(hash, color);
        update(hash, colors.length);
        for (SpriteContents sprite : new SpriteContents[]{base, snow[0], snow[1], snow[2]})
        {
            NativeImage image = sprite.getOriginalImage();
            update(hash, sprite.width()); update(hash, sprite.height());
            update(hash, image.getWidth()); update(hash, image.getHeight());
            for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) update(hash, image.getPixelRGBA(x, y));
        }
        return HexFormat.of().formatHex(hash.digest());
    }

    static List<SpriteContents> read(Path file, String key, List<ImageSpec> expected, ResourceMetadata metadata)
    {
        List<SpriteContents> loaded = new ArrayList<>(expected.size());
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) return null;
        try (DataInputStream input = new DataInputStream(new BufferedInputStream(new GZIPInputStream(Files.newInputStream(file)), 65536)))
        {
            if (input.readInt() != MAGIC || !input.readUTF().equals(key) || input.readInt() != expected.size()) return null;
            for (ImageSpec spec : expected)
            {
                if (input.readInt() != spec.width || input.readInt() != spec.height
                    || input.readInt() != spec.frameWidth || input.readInt() != spec.frameHeight) throw new IOException("cache dimensions changed");
                NativeImage image = new NativeImage(spec.width, spec.height, false);
                try
                {
                    for (int y = 0; y < spec.height; y++) for (int x = 0; x < spec.width; x++) image.setPixelRGBA(x, y, input.readInt());
                    loaded.add(new SpriteContents(spec.name, new FrameSize(spec.frameWidth, spec.frameHeight), image, metadata));
                }
                catch (Exception | Error e) { image.close(); throw e; }
            }
            // Reading through EOF validates gzip's checksum; never trust partially written/corrupt files.
            if (input.read() != -1) throw new IOException("trailing cache data");
        }
        catch (Exception e)
        {
            loaded.forEach(SpriteContents::close);
            return null;
        }
        catch (Error e)
        {
            loaded.forEach(SpriteContents::close);
            throw e;
        }
        try { Files.setLastModifiedTime(file, FileTime.fromMillis(System.currentTimeMillis())); } catch (IOException ignored) {}
        return loaded;
    }

    static void write(Path file, String key, List<SpriteContents> images)
    {
        Path temporary = null;
        try
        {
            Files.createDirectories(file.getParent());
            temporary = Files.createTempFile(file.getParent(), "snow-", ".tmp");
            try (DataOutputStream output = new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(Files.newOutputStream(temporary), 65536), 65536)))
            {
                output.writeInt(MAGIC); output.writeUTF(key); output.writeInt(images.size());
                for (SpriteContents sprite : images)
                {
                    NativeImage image = sprite.getOriginalImage();
                    output.writeInt(image.getWidth()); output.writeInt(image.getHeight());
                    output.writeInt(sprite.width()); output.writeInt(sprite.height());
                    for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) output.writeInt(image.getPixelRGBA(x, y));
                }
            }
            try { Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING); }
        }
        catch (IOException e) { LogUtils.getLogger().warn("Snow texture disk cache unavailable: {}", e.toString()); }
        finally { if (temporary != null) try { Files.deleteIfExists(temporary); } catch (IOException ignored) {} }
    }

    static void trim(Path directory, long budget)
    {
        if (!Files.isDirectory(directory)) return;
        record Entry(Path path, long size, long modified) {}
        try (var paths = Files.list(directory))
        {
            List<Entry> files = new ArrayList<>();
            for (Path path : paths.toList())
                if (path.getFileName().toString().matches("[0-9a-f]{64}\\.bfs") && Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    files.add(new Entry(path, Files.size(path), Files.getLastModifiedTime(path).toMillis()));
            files.sort(Comparator.comparingLong(Entry::modified));
            long size = files.stream().mapToLong(Entry::size).sum();
            for (Entry entry : files)
            {
                if (size <= budget) break;
                Files.deleteIfExists(entry.path); size -= entry.size;
            }
        }
        catch (IOException e) { LogUtils.getLogger().warn("Could not trim snow texture cache: {}", e.toString()); }
    }

    private static MessageDigest digest()
    {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private static void update(MessageDigest digest, int value)
    {
        digest.update((byte) value); digest.update((byte) (value >>> 8));
        digest.update((byte) (value >>> 16)); digest.update((byte) (value >>> 24));
    }
}
