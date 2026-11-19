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
