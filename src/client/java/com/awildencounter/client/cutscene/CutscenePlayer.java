package com.awildencounter.client.cutscene;

import com.awildencounter.AWildEncounter;
import com.awildencounter.client.GifAnimation;
import com.awildencounter.net.CutsceneCoveredPayload;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class CutscenePlayer {
    private static final ExecutorService DECODER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "A Wild Cobblemon Appeared! cutscene decoder");
        thread.setDaemon(true);
        return thread;
    });

    private static Playback current;

    private CutscenePlayer() {
    }

    public static void play(String clipId) {
        play(clipId, false);
    }

    public static void play(String clipId, boolean reportCoverage) {
        stop();
        Playback playback = new Playback(clipId, reportCoverage);
        current = playback;
        playback.begin();
    }

    public static boolean isPlaying() {
        return current != null;
    }

    public static void stop() {
        if (current != null) {
            current.close();
            current = null;
        }
    }

    public static void renderHud(DrawContext context) {
        if (MinecraftClient.getInstance().currentScreen == null) {
            render(context);
        }
    }

    public static void renderOverScreen(DrawContext context) {
        render(context);
    }

    private static void render(DrawContext context) {
        Playback playback = current;
        if (playback == null) {
            return;
        }
        if (!playback.render(context)) {
            playback.close();
            if (current == playback) {
                current = null;
            }
        }
    }

    private static final class Playback {
        private static final int LOOKAHEAD = 6;

        private static final long FALLBACK_MS = 2500L;

        private final String clipId;
        private final boolean reportCoverage;
        private final long startedAt = System.currentTimeMillis();
        private boolean reported;
        private final Map<Integer, NativeImage> decoded = new ConcurrentHashMap<>();

        private volatile CutsceneClip clip;
        private volatile boolean failed;
        private volatile boolean cancelled;
        private volatile int wanted;

        private NativeImageBackedTexture texture;
        private Identifier textureId;
        private int shown = -1;
        private GifAnimation override;

        Playback(String clipId, boolean reportCoverage) {
            this.clipId = clipId;
            this.reportCoverage = reportCoverage;
        }

        private void reportCovered() {
            if (reported || !reportCoverage) {
                return;
            }
            reported = true;
            if (ClientPlayNetworking.canSend(CutsceneCoveredPayload.ID)) {
                ClientPlayNetworking.send(new CutsceneCoveredPayload(clipId));
            }
        }

        void begin() {
            override = loadOverride(clipId);
            if (override == null) {
                DECODER.execute(this::decodeLoop);
            }
        }

        private void decodeLoop() {
            try {
                CutsceneClip loaded = loadOrDefault(clipId);
                clip = loaded;
                for (int i = 0; i < loaded.frameCount() && !cancelled; i++) {
                    while (!cancelled && i > wanted + LOOKAHEAD) {
                        Thread.sleep(2);
                    }
                    if (i < wanted) {
                        continue;
                    }
                    NativeImage image = decodeFrame(loaded.frames().get(i));
                    if (image == null) {
                        continue;
                    }
                    decoded.put(i, image);
                    if (cancelled) {
                        NativeImage orphan = decoded.remove(i);
                        if (orphan != null) {
                            orphan.close();
                        }
                    }
                }
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                failed = true;
                AWildEncounter.LOGGER.warn("Could not play cutscene {}: {}", clipId, e.toString());
            }
        }

        boolean render(DrawContext context) {
            long elapsed = System.currentTimeMillis() - startedAt;
            MinecraftClient client = MinecraftClient.getInstance();
            int screenWidth = client.getWindow().getScaledWidth();
            int screenHeight = client.getWindow().getScaledHeight();

            if (override != null) {
                if (elapsed >= override.getTotalDurationMs() / 2) {
                    reportCovered();
                }
                if (elapsed > override.getTotalDurationMs()) {
                    reportCovered();
                    return false;
                }
                Identifier frame = override.frameAt(elapsed);
                if (frame != null) {
                    drawCovering(context, frame, override.getWidth(), override.getHeight(),
                            screenWidth, screenHeight, 1.0f);
                }
                return true;
            }

            CutsceneClip loaded = clip;
            if (loaded == null) {
                if (elapsed > FALLBACK_MS) {
                    reportCovered();
                    return false;
                }
                if (failed) {
                    drawFallback(context, screenWidth, screenHeight, elapsed / (float) FALLBACK_MS);
                }
                return true;
            }
            if (elapsed >= loaded.durationMs()) {
                reportCovered();
                return false;
            }

            int due = (int) (elapsed * loaded.fps() / 1000L);
            wanted = due;
            showNewestReady(loaded, due);
            if (shown >= coverFrame(loaded)) {
                reportCovered();
            }
            if (texture == null) {
                return true;
            }

            float alpha = 1.0f;
            if (loaded.fadeOutMs() > 0) {
                long remaining = loaded.durationMs() - elapsed;
                alpha = Math.min(1.0f, remaining / (float) loaded.fadeOutMs());
            }
            drawCovering(context, textureId, loaded.width(), loaded.height(), screenWidth, screenHeight, alpha);
            return true;
        }

        private void showNewestReady(CutsceneClip loaded, int due) {
            NativeImage next = null;
            int nextIndex = -1;
            for (int i = due; i > shown; i--) {
                NativeImage image = decoded.remove(i);
                if (image == null) {
                    continue;
                }
                if (next == null) {
                    next = image;
                    nextIndex = i;
                } else {
                    image.close();
                }
            }
            if (next == null) {
                return;
            }
            if (texture == null) {
                texture = new NativeImageBackedTexture(next);
                texture.setFilter(loaded.smooth(), false);
                textureId = AWildEncounter.id("cutscene_" + Integer.toHexString(clipId.hashCode()) + "_"
                        + Integer.toHexString(System.identityHashCode(this)));
                MinecraftClient.getInstance().getTextureManager().registerTexture(textureId, texture);
            } else {
                texture.setImage(next);
                texture.upload();
            }
            shown = nextIndex;
        }

        private int coverFrame(CutsceneClip loaded) {
            return Math.max(0, loaded.coverAtMs() * loaded.fps() / 1000);
        }

        void close() {
            cancelled = true;
            decoded.values().forEach(NativeImage::close);
            decoded.clear();
            if (textureId != null) {
                MinecraftClient.getInstance().getTextureManager().destroyTexture(textureId);
                textureId = null;
                texture = null;
            }
            if (override != null) {
                override.close();
                override = null;
            }
        }
    }

    private static void drawCovering(DrawContext context, Identifier texture, int textureWidth, int textureHeight,
                                     int screenWidth, int screenHeight, float alpha) {
        float scale = Math.max(screenWidth / (float) textureWidth, screenHeight / (float) textureHeight);
        int drawWidth = Math.round(textureWidth * scale);
        int drawHeight = Math.round(textureHeight * scale);
        int x = (screenWidth - drawWidth) / 2;
        int y = (screenHeight - drawHeight) / 2;

        context.getMatrices().push();

        context.getMatrices().translate(0.0f, 0.0f, 900.0f);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, alpha);
        context.drawTexture(texture, x, y, drawWidth, drawHeight, 0.0f, 0.0f,
                textureWidth, textureHeight, textureWidth, textureHeight);
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        context.getMatrices().pop();
    }

    private static void drawFallback(DrawContext context, int width, int height, float progress) {
        MinecraftClient client = MinecraftClient.getInstance();
        int centerY = height / 2;
        int band = (int) (Math.sin(Math.min(1.0f, progress * 2.0f) * Math.PI) * height * 0.35f);
        context.getMatrices().push();
        context.getMatrices().translate(0.0f, 0.0f, 900.0f);
        context.fill(0, centerY - band, width, centerY + band, 0xCC262B0B);
        context.drawCenteredTextWithShadow(client.textRenderer,
                Text.literal(com.awildencounter.client.gui.Tr.get("ui.wild_encounter", "A wild encounter!")),
                width / 2, centerY - 4, 0xFFC8DD3C);
        context.getMatrices().pop();
    }

    private static CutsceneClip loadOrDefault(String clipId) throws java.io.IOException {
        try {
            return CutsceneClip.load(clipId);
        } catch (java.io.IOException e) {
            if (!com.awildencounter.encounter.CustomClips.isCustom(clipId)) {
                throw e;
            }
            AWildEncounter.LOGGER.info("The custom animation {} is not here ({}); playing the default one",
                    clipId, e.getMessage());
            return CutsceneClip.load(com.awildencounter.encounter.EncounterAnimations.DEFAULT);
        }
    }

    public static NativeImage decodeFrame(CutsceneClip.Frame frame) {
        NativeImage image = decode(frame.colour());
        if (image != null && frame.alpha() != null) {
            applyAlphaMask(image, frame.alpha());
        }
        return image;
    }

    private static void applyAlphaMask(NativeImage image, byte[] bytes) {
        ByteBuffer input = MemoryUtil.memAlloc(bytes.length);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            input.put(bytes).flip();
            IntBuffer width = stack.mallocInt(1);
            IntBuffer height = stack.mallocInt(1);
            IntBuffer channels = stack.mallocInt(1);
            ByteBuffer mask = STBImage.stbi_load_from_memory(input, width, height, channels, 1);
            if (mask == null) {
                return;
            }
            try {
                int w = Math.min(image.getWidth(), width.get(0));
                int h = Math.min(image.getHeight(), height.get(0));
                int stride = width.get(0);
                for (int y = 0; y < h; y++) {
                    for (int x = 0; x < w; x++) {
                        int alpha = mask.get(y * stride + x) & 255;
                        image.setColor(x, y, (image.getColor(x, y) & 0x00FFFFFF) | (alpha << 24));
                    }
                }
            } finally {
                STBImage.stbi_image_free(mask);
            }
        } finally {
            MemoryUtil.memFree(input);
        }
    }

    private static NativeImage decode(byte[] bytes) {
        ByteBuffer input = MemoryUtil.memAlloc(bytes.length);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            input.put(bytes).flip();
            IntBuffer width = stack.mallocInt(1);
            IntBuffer height = stack.mallocInt(1);
            IntBuffer channels = stack.mallocInt(1);
            ByteBuffer pixels = STBImage.stbi_load_from_memory(input, width, height, channels, 4);
            if (pixels == null) {
                AWildEncounter.LOGGER.debug("Could not decode a cutscene frame: {}", STBImage.stbi_failure_reason());
                return null;
            }
            try {
                int w = width.get(0);
                int h = height.get(0);
                pixels.order(ByteOrder.LITTLE_ENDIAN);
                NativeImage image = new NativeImage(w, h, false);

                for (int y = 0; y < h; y++) {
                    int row = y * w;
                    for (int x = 0; x < w; x++) {
                        image.setColor(x, y, pixels.getInt((row + x) * 4));
                    }
                }
                return image;
            } finally {
                STBImage.stbi_image_free(pixels);
            }
        } finally {
            MemoryUtil.memFree(input);
        }
    }

    private static GifAnimation loadOverride(String clipId) {
        if (com.awildencounter.encounter.CustomClips.isCustom(clipId)) {
            return null;
        }
        Path folder = FabricLoader.getInstance().getConfigDir().resolve(AWildEncounter.MOD_ID);
        Path file = folder.resolve(clipId + ".gif");
        if (!Files.isReadable(file) && "default".equals(clipId)) {
            file = folder.resolve("cutscene.gif");
        }
        if (!Files.isReadable(file)) {
            return null;
        }
        try (InputStream stream = Files.newInputStream(file)) {
            GifAnimation animation = GifAnimation.load(stream, "override_" + clipId);
            if (animation != null) {
                AWildEncounter.LOGGER.info("Using {} for the {} encounter animation", file, clipId);
            }
            return animation;
        } catch (Exception e) {
            AWildEncounter.LOGGER.warn("Could not read {}: {}", file, e.toString());
            return null;
        }
    }
}
