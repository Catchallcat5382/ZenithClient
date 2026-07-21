package com.zenithclient.client;

/**
 * Compatibility shim for upgrades copied over older ZenithClient versions.
 *
 * Older releases contained a custom entity renderer that referenced rendering
 * APIs removed or renamed in Minecraft 26.2. Keeping this small class at the
 * same path ensures that extracting this update over an existing project
 * overwrites the incompatible source file instead of leaving it behind.
 *
 * Entity highlighting is handled by ZenithClient's Minecraft 26.2-compatible
 * implementation. This class intentionally has no rendering dependencies.
 */
final class EntityEspRenderer {
    private EntityEspRenderer() {
    }

    static void initialize() {
        // Compatibility no-op. Retained so older local source changes that still
        // call initialize() can compile safely after copying this update over them.
    }
}
