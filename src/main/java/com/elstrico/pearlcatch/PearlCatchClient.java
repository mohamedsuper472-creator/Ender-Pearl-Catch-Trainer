
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

    private KeyBinding toggleKey;
    private boolean enabled, running, finished;

    private final Set<UUID> seenPearls = new HashSet<>();
    private UUID trackedPearl;

    private Vec3d throwPosition, lastPosition;
    private long startNanos, endNanos, resultNanos;
    private int pearlMissingTicks;

    @Override
    public void onInitializeClient() {
        toggleKey = KeyBindingHelper.registerKeyBinding(
            new KeyBinding(
                "key.enderpearlcatch.toggle",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_O,
                KeyBinding.Category.MISC
            )
        );

        ClientTickEvents.END_CLIENT_TICK.register(this::tick);

        HudElementRegistry.attachElementBefore(
            VanillaHudElements.CHAT,
            Identifier.of("enderpearlcatch", "timer"),
            (context, counter) -> render(context)
        );
    }

    private void reset() {
        running = false;
        finished = false;
        trackedPearl = null;
        throwPosition = null;
        lastPosition = null;
        pearlMissingTicks = 0;
        seenPearls.clear();
    }

    private void tick(MinecraftClient client) {

        while (toggleKey.wasPressed()) {
            enabled = !enabled;
            reset();

            if (client.player != null) {
                client.player.sendMessage(
                    Text.literal(
                        "Ender Pearl Catch " +
                        (enabled ? "ON" : "OFF")
                    ),
                    true
                );
            }
        }

        if (client.player == null || client.world == null) {
            reset();
            return;
        }

        if (!enabled) {
            lastPosition = client.player.getEntityPos();
            return;
        }

        Vec3d nowPosition = client.player.getEntityPos();

        Box searchArea = client.player
            .getBoundingBox()
            .expand(128);

        var pearls = client.world.getEntitiesByClass(
            EnderPearlEntity.class,
            searchArea,
            p -> true
        );

        Set<UUID> visible = new HashSet<>();
        boolean trackedVisible = false;

        for (EnderPearlEntity pearl : pearls) {

            UUID id = pearl.getUuid();
            visible.add(id);

            if (id.equals(trackedPearl)) {
                trackedVisible = true;
            }

            if (!seenPearls.contains(id)
                && pearl.getOwner() != null
                && pearl.getOwner().getUuid()
                    .equals(client.player.getUuid())
                && pearl.getVelocity().y > 0.05) {

                trackedPearl = id;
                throwPosition = nowPosition;
                startNanos = System.nanoTime();

                running = true;
                finished = false;
                pearlMissingTicks = 0;
            }
        }

        seenPearls.clear();
        seenPearls.addAll(visible);

        if (running && trackedPearl != null) {

            if (trackedVisible) {
                pearlMissingTicks = 0;
            } else {
                pearlMissingTicks++;
            }

            if (lastPosition != null
                && throwPosition != null
                && nowPosition.y > throwPosition.y + 1.5
                && nowPosition.distanceTo(lastPosition) > 3.0
                && pearlMissingTicks > 0
                && pearlMissingTicks <= 12) {

                endNanos = System.nanoTime();
                resultNanos = endNanos;

                running = false;
                finished = true;
            }

            if (System.nanoTime() - startNanos
                > 30_000_000_000L) {

                running = false;
                finished = false;
                trackedPearl = null;
            }
        }

        lastPosition = nowPosition;
    }

    private void render(DrawContext ctx) {

        MinecraftClient mc = MinecraftClient.getInstance();

        if (!enabled
            || mc.player == null
            || mc.options.hudHidden) {
            return;
        }

        if (finished
            && System.nanoTime() - resultNanos
                > 5_000_000_000L) {
            finished = false;
        }

        String label;
        int color = 0xFFFFFFFF;

        if (running || finished) {

            double seconds =
                ((running ? System.nanoTime() : endNanos)
                - startNanos) / 1_000_000_000.0;

            label = seconds > 5.0
                ? "Tooo Slow"
                : String.format(
                    Locale.ROOT,
                    "%.3fs",
                    seconds
                );

            if (seconds > 5.0) {
                color = 0xFFFF5555;
            }

        } else {
            label = "Ender Pearl Catch ON";
        }

        int x = (
            ctx.getScaledWindowWidth()
            - mc.textRenderer.getWidth(label)
        ) / 2;

        int y = ctx.getScaledWindowHeight() - 64;

        ctx.drawTextWithShadow(
            mc.textRenderer,
            label,
            x,
            y,
            color
        );
    }
}
