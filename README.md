# Ender Pearl Catch Timer (Fabric 1.21.11)

Source project, not a compiled or tested JAR. Requires Java 21 and Gradle (or Gradle wrapper).
Run `gradle build` and find the JAR in `build/libs`.

- Timer starts when the local player's thrown ender pearl appears client-side.
- Timer stops when a locally-owned wind charge comes within 0.55 blocks of the tracked pearl.
- Timer is white, centered above health/hunger; after 5 seconds it displays red `Tooo Slow`.
- New pearl resets the timer.

**Limitations:** Proximity is not guaranteed to mean an actual hit. This client-only prototype does not modify projectile physics or add the previously discussed transparent target / randomized positions / 10-attempt scoring. Client tick visibility and network latency affect the timing. Compile compatibility with Fabric 1.21.11 has not been verified.
