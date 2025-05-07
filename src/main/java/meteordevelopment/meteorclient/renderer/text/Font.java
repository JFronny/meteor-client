/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */
package meteordevelopment.meteorclient.renderer.text;

import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.TextureFormat;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import meteordevelopment.meteorclient.renderer.MeshBuilder;
import meteordevelopment.meteorclient.renderer.Texture;
import meteordevelopment.meteorclient.utils.render.color.Color;
import org.lwjgl.BufferUtils;
import org.lwjgl.stb.*;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.function.IntPredicate;

public class Font {
    public Texture texture;
    private final int height;
    private final float scale;
    private final float ascent;
    private final Int2ObjectOpenHashMap<CharData> charMap = new Int2ObjectOpenHashMap<>();
    private static final int size = 2048;

    private final ByteBuffer buffer;
    private final ByteBuffer bitmap;
    private final STBTTPackContext packContext;

    public Font(ByteBuffer buffer, int height) {
        this.buffer = buffer;
        this.height = height;

        // Initialize font
        STBTTFontinfo fontInfo = STBTTFontinfo.create();
        STBTruetype.stbtt_InitFont(fontInfo, buffer);

        // Allocate buffers
        this.bitmap = BufferUtils.createByteBuffer(size * size);

        // create and initialise packing context
        this.packContext = STBTTPackContext.create();
        STBTruetype.stbtt_PackBegin(packContext, bitmap, size, size, 0, 1);

        // Create texture object and get font scale
        texture = new Texture(size, size, TextureFormat.RED8, FilterMode.LINEAR, FilterMode.LINEAR);
        texture.upload(bitmap);
        scale = STBTruetype.stbtt_ScaleForPixelHeight(fontInfo, height);

        // Get font vertical ascent
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer ascent = stack.mallocInt(1);
            STBTruetype.stbtt_GetFontVMetrics(fontInfo, ascent, null, null);
            this.ascent = ascent.get(0);
        }

        // Preload basic ASCII characters
        preloadAsciiCharacters();
    }

    private void preloadAsciiCharacters() {
        STBTTPackedchar.Buffer cdata = STBTTPackedchar.create(128); // Basic Latin

        // create the pack range
        STBTTPackRange.Buffer packRange = STBTTPackRange.create(1);
        packRange.put(STBTTPackRange.create().set(height, 32, null, 128, cdata, (byte) 2, (byte) 2));
        packRange.flip();

        // pack font ranges
        STBTruetype.stbtt_PackFontRanges(packContext, buffer, 0, packRange);

        // Load character data into charMap
        for (int i = 0; i < cdata.capacity(); i++) {
            STBTTPackedchar packedChar = cdata.get(i);
            putCharData(i + 32, packedChar);
        }

        // Create texture
        texture.upload(bitmap);
    }

    private long lastLoad = 0;
    private int loadCount = 0;

    private void loadCharacters(String s) {
        loadCharacters(s.chars()
            .filter(((IntPredicate) charMap::containsKey).negate())
            .toArray());
    }

    private void loadCharacters(int... codePoints) {
        if (codePoints.length == 0) return;

        // Throttle character loading to avoid blocking the rendering thread
        if (System.currentTimeMillis() - lastLoad > 100) {
            lastLoad = System.currentTimeMillis();
            loadCount = 0;
        }
        if (loadCount >= 8) return;

        for (int codePoint : codePoints) {
            if (charMap.containsKey(codePoint)) continue;

            STBTTPackedchar.Buffer cdata = STBTTPackedchar.create(1);

            // create the pack range
            STBTTPackRange.Buffer packRange = STBTTPackRange.create(1);
            packRange.put(STBTTPackRange.create()
                .set(height, codePoint, null, 1, cdata, (byte) 2, (byte) 2)
            );
            packRange.flip();

            // pack font ranges
            STBTruetype.stbtt_PackFontRanges(packContext, buffer, 0, packRange);

            STBTTPackedchar packedChar = cdata.get(0);
            putCharData(codePoint, packedChar);
        }

        // Re-create texture
        texture.upload(bitmap);
        loadCount++;
    }

    private void putCharData(int codePoint, STBTTPackedchar packedChar) {
        float ipw = 1f / size; // pixel width and height
        float iph = 1f / size;

        charMap.put(codePoint, new CharData(
            packedChar.xoff(),
            packedChar.yoff(),
            packedChar.xoff2(),
            packedChar.yoff2(),
            packedChar.x0() * ipw,
            packedChar.y0() * iph,
            packedChar.x1() * ipw,
            packedChar.y1() * iph,
            packedChar.xadvance()
        ));
    }

    private CharData getCharData(int codePoint) {
        CharData c = charMap.get(codePoint);
        if (c == null) c = charMap.get('�');
        if (c == null) c = charMap.get(' ');
        return c;
    }

    public double getWidth(String string, int length) {
        loadCharacters(string);
        return string.chars().limit(length).mapToDouble(i -> getCharData(i).xAdvance).sum();
    }

    public double getWidth(String string) {
        loadCharacters(string);
        return string.chars().mapToDouble(i -> getCharData(i).xAdvance).sum();
    }

    public int getHeight() {
        return height;
    }

    public double render(MeshBuilder mesh, String string, double x, double y, Color color, double scale) {
        loadCharacters(string);

        y += ascent * this.scale * scale;

        for (CharData c : (Iterable<CharData>) string.chars().mapToObj(this::getCharData)::iterator) {
            mesh.quad(
                mesh.vec2(x + c.x0 * scale, y + c.y0 * scale).vec2(c.u0, c.v0).color(color).next(),
                mesh.vec2(x + c.x0 * scale, y + c.y1 * scale).vec2(c.u0, c.v1).color(color).next(),
                mesh.vec2(x + c.x1 * scale, y + c.y1 * scale).vec2(c.u1, c.v1).color(color).next(),
                mesh.vec2(x + c.x1 * scale, y + c.y0 * scale).vec2(c.u1, c.v0).color(color).next()
            );

            x += c.xAdvance * scale;
        }

        return x;
    }

    private record CharData(float x0, float y0, float x1, float y1, float u0, float v0, float u1, float v1, float xAdvance) {}
}
