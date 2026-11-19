package com.elstrico.pearlcatch;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public final class PearlCatchClient implements ClientModInitializer {
    private static final long RESULT_DURATION = 3_000_000_000L;
    private static final long MAX_ATTEMPT = 30_000_000_000L;
    // The pearl must be clearly travelling upward, not horizontally or toward the floor.
    private static final double MIN_UPWARD_RATIO = 0.30;
    private static final double MIN_UPWARD_SPEED = 0.12;

    private KeyBinding toggleKey;
    private boolean enabled = false;
    private boolean running = false;
    private final Set<UUID> seenPearls = new HashSet<>();
    private UUID trackedPearl;
    private Vec3d throwPosition;
    private Vec3d lastPosition;
    private long startNanos;
    private long resultUntilNanos;
    private String resultText;
    private int missingTicks;

    @Override
    public void onInitializeClient() {
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.enderpearlcatch.toggle", InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_O, KeyBinding.Category.MISC
        ));
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        HudElementRegistry.attachElementBefore(
            VanillaHudElements.CHAT,
            Identifier.of("enderpearlcatch", "timer"),
            (context, counter) -> render(context)
        );
    }

    private void clearAttempt() {
        running = false;
        trackedPearl = null;
        throwPosition = null;
        missingTicks = 0;
    }

    private void showResult(String text) {
        resultText = text;
        resultUntilNanos = System.nanoTime() + RESULT_DURATION;
        clearAttempt();
    }

    private void tick(MinecraftClient client) {
        while (toggleKey.wasPressed()) {
            enabled = !enabled;
            clearAttempt();
            resultText = null;
            // Action bar message only: the HUD will NOT also print an ON label.
            if (client.player != null) {
                client.player.sendMessage(
                    Text.literal("Ender Pearl Catch " + (enabled ? "ON" : "OFF")), true
                );
            }
        }

        if (client.player == null || client.world == null) {
            clearAttempt();
            resultText = null;
            seenPearls.clear();
            lastPosition = null;
            return;
        }

        Vec3d current = client.player.getEntityPos();
        Box area = client.player.getBoundingBox().expand(128);
        var pearls = client.world.getEntitiesByClass(EnderPearlEntity.class, area, p -> true);
        Set<UUID> visible = new HashSet<>();
        boolean trackedVisible = false;

        for (EnderPearlEntity pearl : pearls) {
            UUID id = pearl.getUuid();
            visible.add(id);
            if (id.equals(trackedPearl)) trackedVisible = true;

            if (!enabled || seenPearls.contains(id) || pearl.getOwner() == null
                || !pearl.getOwner().getUuid().equals(client.player.getUuid())) {
                continue;
            }

            Vec3d velocity = pearl.getVelocity();
            double speed = velocity.length();
            boolean upward = speed > 0.001
                && velocity.y >= MIN_UPWARD_SPEED
                && velocity.y / speed >= MIN_UPWARD_RATIO;

            // New throw replaces the old result or attempt immediately.
            resultText = null;
            if (!upward) {
                showResult("Failed");
            } else {
                clearAttempt();
                trackedPearl = id;
                throwPosition = current;
                startNanos = System.nanoTime();
                running = true;
            }
        }
        seenPearls.clear();
        seenPearls.addAll(visible);

        if (enabled && running) {
            if (trackedVisible) missingTicks = 0;
            else missingTicks++;

            // Completion requires an upward teleport after the tracked pearl disappears.
            if (lastPosition != null && throwPosition != null
                && current.y > throwPosition.y + 1.5
                && current.distanceTo(lastPosition) > 3.0
                && missingTicks > 0 && missingTicks <= 12) {
                double seconds = (System.nanoTime() - startNanos) / 1_000_000_000.0;
                showResult(seconds > 5.0
                    ? "Tooo Slow"
                    : String.format(Locale.ROOT, "%.3fs", seconds));
            } else if (System.nanoTime() - startNanos > MAX_ATTEMPT) {
                showResult("Failed");
            } else if (missingTicks > 12) {
                showResult("Failed");
            }
        }
        lastPosition = current;
    }

    private void render(DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!enabled || mc.player == null || mc.options.hudHidden) return;

        String label;
        if (running) {
            double seconds = (System.nanoTime() - startNanos) / 1_000_000_000.0;
            label = seconds > 5.0
                ? "Tooo Slow"
                : String.format(Locale.ROOT, "%.3fs", seconds);
        } else if (resultText != null && System.nanoTime() < resultUntilNanos) {
            label = resultText;
        } else {
            return; // No permanent ON text above the hearts.
        }

        int color = (label.equals("Failed") || label.equals("Tooo Slow"))
            ? 0xFFFF5555 : 0xFFFFFFFF;
        int x = (ctx.getScaledWindowWidth() - mc.textRenderer.getWidth(label)) / 2;
        int y = ctx.getScaledWindowHeight() - 64;
        ctx.drawTextWithShadow(mc.textRenderer, label, x, y, color);
    }
}
