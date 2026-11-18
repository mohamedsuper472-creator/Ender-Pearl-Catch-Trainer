package com.elstrico.pearlcatch;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.entity.projectile.WindChargeEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Client-side visual practice timer. Collision detection is proximity-based,
 * not authoritative projectile hit confirmation. */
public final class PearlCatchClient implements ClientModInitializer {
    private final Set<UUID> seenPearls = new HashSet<>();
    private final Set<UUID> seenCharges = new HashSet<>();
    private UUID trackedPearl;
    private long startedAt;
    private long stoppedAt;
    private boolean running;
    private boolean finished;

    @Override public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
            Identifier.of("enderpearlcatch", "timer"), (context, tickCounter) -> draw(context));
    }

    private void tick(MinecraftClient client) {
        if (client.world == null || client.player == null) {
            running = false; finished = false; trackedPearl = null;
            seenPearls.clear(); seenCharges.clear(); return;
        }
        Box area = client.player.getBoundingBox().expand(96);
        var pearls = client.world.getEntitiesByClass(EnderPearlEntity.class, area, e -> true);
        var charges = client.world.getEntitiesByClass(WindChargeEntity.class, area, e -> true);
        Set<UUID> currentPearls = new HashSet<>();
        for (EnderPearlEntity pearl : pearls) {
            UUID id = pearl.getUuid(); currentPearls.add(id);
            if (!seenPearls.contains(id) && pearl.getOwner() == client.player) {
                trackedPearl = id;
                startedAt = System.nanoTime(); stoppedAt = 0;
                running = true; finished = false;
            }
        }
        seenPearls.clear(); seenPearls.addAll(currentPearls);
        Set<UUID> currentCharges = new HashSet<>();
        for (WindChargeEntity charge : charges) {
            UUID id = charge.getUuid(); currentCharges.add(id);
            if (running && trackedPearl != null && charge.getOwner() == client.player) {
                for (EnderPearlEntity pearl : pearls) {
                    if (pearl.getUuid().equals(trackedPearl)
                        && charge.squaredDistanceTo(pearl) < 0.55 * 0.55) {
                        stoppedAt = System.nanoTime();
                        running = false; finished = true;
                        break;
                    }
                }
            }
        }
        seenCharges.clear(); seenCharges.addAll(currentCharges);
        if (running && System.nanoTime() - startedAt > 15_000_000_000L) {
            running = false; finished = true; stoppedAt = System.nanoTime();
        }
    }

    private void draw(DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.options.hudHidden || (!running && !finished)) return;
        double seconds = ((running ? System.nanoTime() : stoppedAt) - startedAt) / 1_000_000_000.0;
        String line = seconds > 5 ? "Tooo Slow" : String.format(java.util.Locale.ROOT, "%.3fs", seconds);
        int color = seconds > 5 ? 0xFFFF5555 : 0xFFFFFFFF;
        int x = (ctx.getScaledWindowWidth() - mc.textRenderer.getWidth(line)) / 2;
        int y = ctx.getScaledWindowHeight() - 64;
        ctx.drawTextWithShadow(mc.textRenderer, line, x, y, color);
    }
}
