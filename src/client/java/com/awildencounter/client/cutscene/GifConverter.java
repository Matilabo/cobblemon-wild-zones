package com.awildencounter.client.cutscene;

import org.w3c.dom.Node;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public final class GifConverter {
    public static final int MAX_FRAMES = 600;
    public static final int MAX_DURATION_MS = 30_000;
    private static final int TARGET_W = 480;
    private static final int TARGET_H = 270;
    private static final int MAX_SIDE = 1024;
    private static final int MAX_SOURCE_PIXELS = 4096 * 4096;
    private static final int MAX_FPS = 50;

    public record Converted(String source, int fps, int width, int height, boolean transparent, List<byte[]> files,
                            List<Integer> sequence) {
        public int durationMs() {
            return Math.round(sequence.size() * 1000f / fps);
        }

        public CutsceneClip toClip(String id, boolean smooth) {
            List<CutsceneClip.Frame> frames = new ArrayList<>(sequence.size());
            for (int index : sequence) {
                frames.add(new CutsceneClip.Frame(files.get(index), null));
            }
            int middle = durationMs() / 2;
            return new CutsceneClip(id, fps, width, height, transparent, smooth, durationMs(), middle, middle, 0,
                    frames);
        }
    }

    public interface Progress {
        void update(int done, int total);

        boolean cancelled();
    }

    public static final class ConvertException extends IOException {
        public final String key;
        public final String english;

        ConvertException(String key, String english) {
            super(english);
            this.key = key;
            this.english = english;
        }
    }

    private GifConverter() {
    }

    public static Converted convert(Path gif, Progress progress) throws IOException {
        try (ImageInputStream input = ImageIO.createImageInputStream(gif.toFile())) {
            if (input == null) {
                throw new ConvertException("import.error.read", "The file could not be opened.");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("gif");
            if (!readers.hasNext()) {
                throw new ConvertException("import.error.read", "The file could not be opened.");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, false);
                return convert(gif.getFileName().toString(), reader, progress);
            } catch (ConvertException e) {
                throw e;
            } catch (IOException | RuntimeException e) {
                throw new ConvertException("import.error.gif", "This is not a GIF that can be read.");
            } finally {
                reader.dispose();
            }
        }
    }

    private record FrameInfo(int left, int top, String disposal, int delayMs) {
    }

    private static Converted convert(String source, ImageReader reader, Progress progress) throws IOException {
        int count = reader.getNumImages(true);
        if (count <= 0) {
            throw new ConvertException("import.error.gif", "This is not a GIF that can be read.");
        }
        if (count > MAX_FRAMES) {
            throw new ConvertException("import.error.frames", "The GIF has more than 600 frames.");
        }
        int[] screen = logicalScreen(reader.getStreamMetadata());
        BufferedImage first = reader.read(0);
        FrameInfo firstInfo = info(reader.getImageMetadata(0));
        int width = screen[0] > 0 ? screen[0] : firstInfo.left() + first.getWidth();
        int height = screen[1] > 0 ? screen[1] : firstInfo.top() + first.getHeight();
        if ((long) width * height > MAX_SOURCE_PIXELS) {
            throw new ConvertException("import.error.size", "The GIF is too big (more than 4096×4096).");
        }
        double scale = Math.min(1.0, Math.max(TARGET_W / (double) width, TARGET_H / (double) height));
        scale = Math.min(scale, MAX_SIDE / (double) Math.max(width, height));
        int outW = Math.max(1, (int) Math.round(width * scale));
        int outH = Math.max(1, (int) Math.round(height * scale));

        BufferedImage canvas = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        BufferedImage saved = null;
        FrameInfo previous = null;
        int previousW = 0;
        int previousH = 0;
        List<byte[]> files = new ArrayList<>();
        List<Integer> delays = new ArrayList<>();
        boolean transparent = false;
        int elapsed = 0;
        for (int i = 0; i < count && elapsed < MAX_DURATION_MS; i++) {
            if (progress.cancelled()) {
                return null;
            }
            BufferedImage frame = i == 0 ? first : reader.read(i);
            FrameInfo info = i == 0 ? firstInfo : info(reader.getImageMetadata(i));
            if (previous != null) {
                if ("restoreToBackgroundColor".equals(previous.disposal())) {
                    Graphics2D clear = canvas.createGraphics();
                    clear.setComposite(AlphaComposite.Clear);
                    clear.fillRect(previous.left(), previous.top(), previousW, previousH);
                    clear.dispose();
                } else if ("restoreToPrevious".equals(previous.disposal()) && saved != null) {
                    Graphics2D restore = canvas.createGraphics();
                    restore.setComposite(AlphaComposite.Src);
                    restore.drawImage(saved, 0, 0, null);
                    restore.dispose();
                }
            }
            if ("restoreToPrevious".equals(info.disposal())) {
                saved = copy(canvas);
            }
            Graphics2D draw = canvas.createGraphics();
            draw.drawImage(frame, info.left(), info.top(), null);
            draw.dispose();

            BufferedImage out = outW == width && outH == height ? canvas : scaled(canvas, outW, outH);
            boolean opaque = opaque(out);
            transparent |= !opaque;
            files.add(png(opaque ? rgb(out) : out));
            delays.add(info.delayMs());
            elapsed += info.delayMs();
            previous = info;
            previousW = frame.getWidth();
            previousH = frame.getHeight();
            progress.update(i + 1, count);
        }
        return timed(source, outW, outH, transparent, files, delays);
    }

    public static Converted keyed(Converted converted, ChromaKey key, Progress progress) throws IOException {
        List<byte[]> files = new ArrayList<>(converted.files().size());
        boolean transparent = converted.transparent();
        int total = converted.files().size();
        for (int i = 0; i < total; i++) {
            if (progress.cancelled()) {
                return null;
            }
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(converted.files().get(i)));
            if (image == null) {
                throw new IOException("Frame " + i + " could not be read");
            }
            int w = image.getWidth();
            int h = image.getHeight();
            int[] pixels = image.getRGB(0, 0, w, h, null, 0, w);
            boolean see = key.apply(pixels);
            BufferedImage out = new BufferedImage(w, h, see ? BufferedImage.TYPE_INT_ARGB
                    : BufferedImage.TYPE_INT_RGB);
            out.setRGB(0, 0, w, h, pixels, 0, w);
            transparent |= see;
            files.add(png(out));
            progress.update(i + 1, total);
        }
        return new Converted(converted.source(), converted.fps(), converted.width(), converted.height(), transparent,
                files, converted.sequence());
    }

    private static Converted timed(String source, int width, int height, boolean transparent, List<byte[]> files,
                                   List<Integer> delays) {
        int shortest = Integer.MAX_VALUE;
        long total = 0;
        for (int delay : delays) {
            shortest = Math.min(shortest, delay);
            total += delay;
        }
        total = Math.min(total, MAX_DURATION_MS);
        int fps = Math.max(1, Math.min(MAX_FPS, Math.round(1000f / shortest)));
        double step = 1000.0 / fps;
        int frames = Math.max(1, (int) Math.round(total / step));
        List<Integer> sequence = new ArrayList<>(frames);
        int index = 0;
        long end = delays.get(0);
        for (int k = 0; k < frames; k++) {
            double t = (k + 0.5) * step;
            while (t >= end && index < delays.size() - 1) {
                index++;
                end += delays.get(index);
            }
            sequence.add(index);
        }
        Map<Integer, Integer> renumber = new HashMap<>();
        List<byte[]> used = new ArrayList<>();
        List<Integer> remapped = new ArrayList<>(sequence.size());
        for (int original : sequence) {
            Integer number = renumber.get(original);
            if (number == null) {
                number = used.size();
                renumber.put(original, number);
                used.add(files.get(original));
            }
            remapped.add(number);
        }
        return new Converted(source, fps, width, height, transparent, used, remapped);
    }

    private static int[] logicalScreen(IIOMetadata metadata) {
        try {
            Node root = metadata.getAsTree("javax_imageio_gif_stream_1.0");
            Node screen = child(root, "LogicalScreenDescriptor");
            if (screen != null) {
                return new int[]{intAttribute(screen, "logicalScreenWidth", 0),
                        intAttribute(screen, "logicalScreenHeight", 0)};
            }
        } catch (Exception ignored) {
        }
        return new int[]{0, 0};
    }

    private static FrameInfo info(IIOMetadata metadata) {
        int left = 0;
        int top = 0;
        String disposal = "none";
        int delayCs = 10;
        try {
            Node root = metadata.getAsTree("javax_imageio_gif_image_1.0");
            Node descriptor = child(root, "ImageDescriptor");
            if (descriptor != null) {
                left = intAttribute(descriptor, "imageLeftPosition", 0);
                top = intAttribute(descriptor, "imageTopPosition", 0);
            }
            Node control = child(root, "GraphicControlExtension");
            if (control != null) {
                Node method = control.getAttributes().getNamedItem("disposalMethod");
                if (method != null) {
                    disposal = method.getNodeValue();
                }
                delayCs = intAttribute(control, "delayTime", 10);
            }
        } catch (Exception ignored) {
        }
        int delayMs = delayCs <= 1 ? 100 : delayCs * 10;
        return new FrameInfo(left, top, disposal, delayMs);
    }

    private static Node child(Node parent, String name) {
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (name.equals(node.getNodeName())) {
                return node;
            }
        }
        return null;
    }

    private static int intAttribute(Node node, String name, int fallback) {
        Node attribute = node.getAttributes().getNamedItem(name);
        try {
            return attribute == null ? fallback : Integer.parseInt(attribute.getNodeValue());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static BufferedImage copy(BufferedImage image) {
        BufferedImage copy = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = copy.createGraphics();
        graphics.setComposite(AlphaComposite.Src);
        graphics.drawImage(image, 0, 0, null);
        graphics.dispose();
        return copy;
    }

    private static BufferedImage scaled(BufferedImage image, int width, int height) {
        BufferedImage current = image;
        while (current.getWidth() / 2 >= width && current.getHeight() / 2 >= height) {
            current = resize(current, current.getWidth() / 2, current.getHeight() / 2);
        }
        return current.getWidth() == width && current.getHeight() == height ? current
                : resize(current, width, height);
    }

    private static BufferedImage resize(BufferedImage image, int width, int height) {
        BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = out.createGraphics();
        graphics.setComposite(AlphaComposite.Src);
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.drawImage(image, 0, 0, width, height, null);
        graphics.dispose();
        return out;
    }

    private static boolean opaque(BufferedImage image) {
        int[] pixels = image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
        for (int pixel : pixels) {
            if ((pixel >>> 24) != 0xFF) {
                return false;
            }
        }
        return true;
    }

    private static BufferedImage rgb(BufferedImage image) {
        BufferedImage out = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = out.createGraphics();
        graphics.drawImage(image, 0, 0, null);
        graphics.dispose();
        return out;
    }

    private static byte[] png(BufferedImage image) throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("png");
        if (!writers.hasNext()) {
            throw new IOException("No PNG writer");
        }
        ImageWriter writer = writers.next();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (MemoryCacheImageOutputStream output = new MemoryCacheImageOutputStream(bytes)) {
            writer.setOutput(output);
            writer.write(null, new IIOImage(image, null, null), writer.getDefaultWriteParam());
        } finally {
            writer.dispose();
        }
        return bytes.toByteArray();
    }
}
