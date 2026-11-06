package com.awildencounter.client;

import com.awildencounter.AWildEncounter;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageInputStream;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class GifAnimation {
    private static final int DEFAULT_FRAME_DELAY_MS = 100;

    private final List<Identifier> frames;
    private final List<Integer> delaysMs;
    private final int width;
    private final int height;
    private final int totalDurationMs;

    private GifAnimation(List<Identifier> frames, List<Integer> delaysMs, int width, int height) {
        this.frames = frames;
        this.delaysMs = delaysMs;
        this.width = width;
        this.height = height;
        int total = 0;
        for (int delay : delaysMs) {
            total += delay;
        }
        this.totalDurationMs = Math.max(1, total);
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getTotalDurationMs() {
        return totalDurationMs;
    }

    public boolean isEmpty() {
        return frames.isEmpty();
    }

    public Identifier frameAt(long elapsedMs) {
        if (frames.isEmpty()) {
            return null;
        }
        long remaining = elapsedMs;
        for (int i = 0; i < frames.size(); i++) {
            remaining -= delaysMs.get(i);
            if (remaining < 0) {
                return frames.get(i);
            }
        }
        return frames.get(frames.size() - 1);
    }

    public void close() {
        MinecraftClient client = MinecraftClient.getInstance();
        for (Identifier frame : frames) {
            client.getTextureManager().destroyTexture(frame);
        }
        frames.clear();
        delaysMs.clear();
    }

    public static GifAnimation load(InputStream stream, String texturePrefix) {
        try (ImageInputStream input = ImageIO.createImageInputStream(stream)) {
            if (input == null) {
                return null;
            }
            Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("gif");
            if (!readers.hasNext()) {
                return null;
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, false);
                int frameCount = reader.getNumImages(true);
                if (frameCount <= 0) {
                    return null;
                }

                List<Identifier> ids = new ArrayList<>();
                List<Integer> delays = new ArrayList<>();
                BufferedImage canvas = null;
                int width = 0;
                int height = 0;

                for (int i = 0; i < frameCount; i++) {
                    BufferedImage frame = reader.read(i);
                    if (canvas == null) {
                        width = frame.getWidth();
                        height = frame.getHeight();
                        canvas = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                    }

                    Graphics2D graphics = canvas.createGraphics();
                    graphics.drawImage(frame, 0, 0, null);
                    graphics.dispose();

                    Identifier id = AWildEncounter.id(texturePrefix + "_" + i);
                    if (!upload(canvas, id)) {
                        continue;
                    }
                    ids.add(id);
                    delays.add(frameDelayMs(reader.getImageMetadata(i)));
                }
                if (ids.isEmpty()) {
                    return null;
                }
                return new GifAnimation(ids, delays, width, height);
            } finally {
                reader.dispose();
            }
        } catch (Exception e) {
            AWildEncounter.LOGGER.warn("Could not decode the cutscene GIF: {}", e.toString());
            return null;
        }
    }

    private static boolean upload(BufferedImage source, Identifier id) {
        try {
            int width = source.getWidth();
            int height = source.getHeight();
            NativeImage image = new NativeImage(NativeImage.Format.RGBA, width, height, false);
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int argb = source.getRGB(x, y);
                    int alpha = (argb >> 24) & 0xFF;
                    int red = (argb >> 16) & 0xFF;
                    int green = (argb >> 8) & 0xFF;
                    int blue = argb & 0xFF;

                    image.setColor(x, y, (alpha << 24) | (blue << 16) | (green << 8) | red);
                }
            }
            MinecraftClient.getInstance().getTextureManager()
                    .registerTexture(id, new NativeImageBackedTexture(image));
            return true;
        } catch (Exception e) {
            AWildEncounter.LOGGER.warn("Could not upload a cutscene frame: {}", e.toString());
            return false;
        }
    }

    private static int frameDelayMs(IIOMetadata metadata) {
        try {
            IIOMetadataNode root = (IIOMetadataNode) metadata.getAsTree(metadata.getNativeMetadataFormatName());
            var descriptors = root.getElementsByTagName("GraphicControlExtension");
            if (descriptors.getLength() == 0) {
                return DEFAULT_FRAME_DELAY_MS;
            }
            IIOMetadataNode control = (IIOMetadataNode) descriptors.item(0);

            int hundredths = Integer.parseInt(control.getAttribute("delayTime"));
            return hundredths <= 0 ? DEFAULT_FRAME_DELAY_MS : hundredths * 10;
        } catch (Exception e) {
            return DEFAULT_FRAME_DELAY_MS;
        }
    }
}
