package com.awildencounter.client.zone;

import com.awildencounter.client.gui.SpeciesIcons;
import com.awildencounter.zone.WildZone;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.List;

public final class ZoneRenderer {
    private static final float ZONE_ALPHA = 0.35f;
    private static final float SELECTION_ALPHA = 0.9f;
    private static final float CORNER_ALPHA = 0.6f;
    private static final double INSET = 0.002;
    private static final double DRAW_DISTANCE = 256.0;

    private static final int SELECTION_COLOR = 0xED9130;
    private static final int FIRST_COLOR = 0xC8DD3C;
    private static final int SECOND_COLOR = 0xED9130;

    private ZoneRenderer() {
    }

    public static void render(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null || !ZoneTool.showing(client.player)) {
            return;
        }
        VertexConsumerProvider consumers = context.consumers();
        MatrixStack matrices = context.matrixStack();
        if (consumers == null || matrices == null) {
            return;
        }
        String dimension = client.world.getRegistryKey().getValue().toString();
        Vec3d camera = context.camera().getPos();
        VertexConsumer lines = consumers.getBuffer(RenderLayer.getLines());

        matrices.push();
        matrices.translate(-camera.x, -camera.y, -camera.z);

        WildZone preview = ClientZones.preview();
        for (WildZone zone : ClientZones.all()) {
            WildZone shown = preview != null && preview.id.equals(zone.id) ? preview : zone;
            if (!shown.dimension.equals(dimension) || !near(shown, camera)) {
                continue;
            }
            box(matrices, lines, shown.minX, shown.minY, shown.minZ, shown.maxX, shown.maxY, shown.maxZ, shown.color,
                    preview == shown ? SELECTION_ALPHA : ZONE_ALPHA);
        }

        BlockPos first = ZoneTool.first();
        BlockPos second = ZoneTool.second();
        if (dimension.equals(ZoneTool.dimension())) {
            if (first != null && second != null) {
                box(matrices, lines, Math.min(first.getX(), second.getX()), Math.min(first.getY(), second.getY()),
                        Math.min(first.getZ(), second.getZ()), Math.max(first.getX(), second.getX()),
                        Math.max(first.getY(), second.getY()), Math.max(first.getZ(), second.getZ()),
                        SELECTION_COLOR, SELECTION_ALPHA);
            }
            if (first != null) {
                box(matrices, lines, first.getX(), first.getY(), first.getZ(), first.getX(), first.getY(),
                        first.getZ(), FIRST_COLOR, CORNER_ALPHA);
            }
            if (second != null) {
                box(matrices, lines, second.getX(), second.getY(), second.getZ(), second.getX(), second.getY(),
                        second.getZ(), SECOND_COLOR, CORNER_ALPHA);
            }
        }
        if (consumers instanceof VertexConsumerProvider.Immediate immediate) {
            immediate.draw(RenderLayer.getLines());
        }

        for (WildZone zone : ClientZones.all()) {
            WildZone shown = preview != null && preview.id.equals(zone.id) ? preview : zone;
            if (shown.dimension.equals(dimension) && distanceSquared(shown, camera) <= SPAWNS_DISTANCE * SPAWNS_DISTANCE) {
                drawSpawns(matrices, consumers, context.camera(), client.textRenderer, shown);
            }
        }
        matrices.pop();

        if (consumers instanceof VertexConsumerProvider.Immediate immediate) {
            immediate.draw();
        }
    }

    private static final double SPAWNS_DISTANCE = 48.0;
    private static final float LABEL_SCALE = 0.025f;
    private static final int ICON = 16;
    private static final int ICON_GAP = 2;
    private static final int ICONS_PER_ROW = 10;
    private static final int PADDING = 3;
    private static final int LIGHT = LightmapTextureManager.MAX_LIGHT_COORDINATE;

    private static void drawSpawns(MatrixStack matrices, VertexConsumerProvider consumers, Camera camera,
                                   TextRenderer font, WildZone zone) {
        ClientZones.Spawns spawns = ClientZones.spawns(zone.id);
        List<String> entries = spawns == null ? List.of() : spawns.entries();
        String title = zone.name;
        if (spawns == null) {
            title += " …";
        } else if (spawns.total() > entries.size()) {
            title += " (+" + (spawns.total() - entries.size()) + ")";
        }
        int columns = Math.max(1, Math.min(ICONS_PER_ROW, entries.size()));
        int rows = (entries.size() + ICONS_PER_ROW - 1) / ICONS_PER_ROW;
        int gridW = columns * ICON + (columns - 1) * ICON_GAP;
        int innerW = Math.max(gridW, font.getWidth(title));
        int innerH = 9 + (rows > 0 ? 3 + rows * ICON + (rows - 1) * ICON_GAP : 0);
        float left = -innerW / 2.0f - PADDING;
        float top = -innerH - PADDING;

        double x = (zone.minX + zone.maxX + 1) / 2.0;
        double y = Math.min(zone.maxY + 1, zone.minY + 3) + 0.5 + PADDING * LABEL_SCALE;
        double z = (zone.minZ + zone.maxZ + 1) / 2.0;
        matrices.push();
        matrices.translate(x, y, z);
        matrices.multiply(camera.getRotation());
        matrices.scale(LABEL_SCALE, -LABEL_SCALE, LABEL_SCALE);
        Matrix4f matrix = matrices.peek().getPositionMatrix();

        int background = (int) (MinecraftClient.getInstance().options.getTextBackgroundOpacity(0.25f) * 255.0f) << 24;
        VertexConsumer panel = consumers.getBuffer(RenderLayer.getTextBackground());
        float right = left + innerW + PADDING * 2;
        float bottom = top + innerH + PADDING * 2;
        panel.vertex(matrix, left, top, 0.01f).color(background).light(LIGHT);
        panel.vertex(matrix, right, top, 0.01f).color(background).light(LIGHT);
        panel.vertex(matrix, right, bottom, 0.01f).color(background).light(LIGHT);
        panel.vertex(matrix, left, bottom, 0.01f).color(background).light(LIGHT);

        font.draw(title, -font.getWidth(title) / 2.0f, top + PADDING, 0xFF000000 | zone.color, false, matrix,
                consumers, TextRenderer.TextLayerType.NORMAL, 0, LIGHT);

        float gridLeft = -gridW / 2.0f;
        float gridTop = top + PADDING + 12;
        for (int i = 0; i < entries.size(); i++) {
            Identifier icon = SpeciesIcons.icon(entries.get(i));
            if (icon == null) {
                continue;
            }
            SpeciesIcons.smooth(icon);
            float x0 = gridLeft + (i % ICONS_PER_ROW) * (ICON + ICON_GAP);
            float y0 = gridTop + (i / ICONS_PER_ROW) * (ICON + ICON_GAP);
            VertexConsumer quad = consumers.getBuffer(RenderLayer.getText(icon));
            quad.vertex(matrix, x0, y0, 0.0f).color(0xFFFFFFFF).texture(0.0f, 0.0f).light(LIGHT);
            quad.vertex(matrix, x0, y0 + ICON, 0.0f).color(0xFFFFFFFF).texture(0.0f, 1.0f).light(LIGHT);
            quad.vertex(matrix, x0 + ICON, y0 + ICON, 0.0f).color(0xFFFFFFFF).texture(1.0f, 1.0f).light(LIGHT);
            quad.vertex(matrix, x0 + ICON, y0, 0.0f).color(0xFFFFFFFF).texture(1.0f, 0.0f).light(LIGHT);
        }
        matrices.pop();
    }

    private static double distanceSquared(WildZone zone, Vec3d camera) {
        double dx = Math.max(0, Math.max(zone.minX - camera.x, camera.x - (zone.maxX + 1)));
        double dy = Math.max(0, Math.max(zone.minY - camera.y, camera.y - (zone.maxY + 1)));
        double dz = Math.max(0, Math.max(zone.minZ - camera.z, camera.z - (zone.maxZ + 1)));
        return dx * dx + dy * dy + dz * dz;
    }

    private static boolean near(WildZone zone, Vec3d camera) {
        double dx = Math.max(0, Math.max(zone.minX - camera.x, camera.x - (zone.maxX + 1)));
        double dz = Math.max(0, Math.max(zone.minZ - camera.z, camera.z - (zone.maxZ + 1)));
        return dx * dx + dz * dz <= DRAW_DISTANCE * DRAW_DISTANCE;
    }

    private static void box(MatrixStack matrices, VertexConsumer lines, int minX, int minY, int minZ, int maxX,
                            int maxY, int maxZ, int color, float alpha) {
        float red = ((color >> 16) & 0xFF) / 255.0f;
        float green = ((color >> 8) & 0xFF) / 255.0f;
        float blue = (color & 0xFF) / 255.0f;
        WorldRenderer.drawBox(matrices, lines, minX - INSET, minY - INSET, minZ - INSET, maxX + 1 + INSET,
                maxY + 1 + INSET, maxZ + 1 + INSET, red, green, blue, alpha);
    }
}
