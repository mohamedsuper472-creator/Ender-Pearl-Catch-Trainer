# Ender Pearl Catch Timer — Fabric 1.21.11

Press **O** to enable/disable. When enabled, throwing your own ender pearl upwards starts a white HUD timer. The timer stops when your character teleports abruptly to a higher position, shortly after the tracked pearl disappears. More than five seconds shows **Tooo Slow**.

This is a **client-side estimate of teleport completion**, not an authoritative server-side teleport event. Teleports caused by other mods/plugins can potentially trigger a false positive, and some server lag or unusual pearl trajectories may be missed. It does NOT stop on wind charge proximity. A Fabric server-side event hook would be needed for fully authoritative teleport detection.

Build on GitHub: put the contents of this directory at repository root, open Actions > Build Ender Pearl Catch > Run workflow, and download the JAR artifact. Fabric API required.
