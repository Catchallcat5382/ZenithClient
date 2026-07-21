package com.zenithclient.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ZenithClient implements ClientModInitializer {
    public static final String MOD_ID = "zenithclient";
    private static final ZenithConfig CONFIG = ZenithConfig.load();
    private static volatile Entity trajectoryTarget;
    private static KeyMapping openMenu;
    private static int ticks;
    private static double savedGamma = Double.NaN;
    private static boolean zenithNightVision;
    private static boolean flightWasActive;
    private static final List<BlockPos> HIGHLIGHTED_BLOCKS = new ArrayList<>();
    private static final List<BlockPos> XRAY_OUTLINE_BLOCKS = new ArrayList<>();
    private static final boolean[] KEY_STATES = new boolean[24];
    private static boolean jumpWasDown;
    private static boolean menuKeyWasDown;
    private static String toggleNotice = "";
    private static long toggleNoticeUntilMs;
    private static BlockPos lastBlockScanOrigin;
    private static ZenithConfig.BlockHighlightMode lastBlockScanMode;
    private static int lastBlockScanRadius = -1;
    private static boolean lastXrayState = CONFIG.xray;
    private static boolean initialXrayRefreshDone;
    private static BlockPos lastXrayOutlineOrigin;
    private static Vec3 freecamPosition;
    private static Vec3 freecamAnchor;
    private static int restoreSwapSlot = -1;
    private static int restoreSwapAfterTick = -1;
    private static int lastAuraAttackTick;
    private static boolean replayingAttack;

    public static ZenithConfig getConfig() { return CONFIG; }

    @Override
    public void onInitializeClient() {
        CONFIG.xray = false;
        lastXrayState = false;
        KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "general"));
        openMenu = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.zenithclient.open_menu", InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT_SHIFT, category
        ));

        ClientTickEvents.END_CLIENT_TICK.register(ZenithClient::tick);
        HudElementRegistry.attachElementBefore(
                VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath(MOD_ID, "status_hud"),
                ZenithClient::renderHud
        );
    }

    private static void tick(Minecraft client) {
        // Poll the physical menu key as well as the KeyMapping so the GUI can open
        // from title, pause, inventory, and other non-world screens.
        long window = client.getWindow().handle();
        boolean menuDown = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
        if ((menuDown && !menuKeyWasDown) || openMenu.consumeClick()) {
            Screen screen = currentScreen(client);
            if (screen instanceof ZenithScreen || screen instanceof ModuleSettingsScreen) {
                client.setScreenAndShow(null);
            } else if (screen == null) {
                client.setScreenAndShow(new ZenithScreen(null, CONFIG));
            }
        }
        menuKeyWasDown = menuDown;

        handleModuleKeybinds(client);
        updateFullbright(client);

        if (client.player == null || client.level == null) return;
        ticks++;
        updateFreecam(client);
        if (restoreSwapSlot >= 0 && ticks >= restoreSwapAfterTick) restoreAttributeSwap(client);
        if (!CONFIG.trajectoryPreview) setTrajectoryTarget(null);
        if (!CONFIG.xray) {
            initialXrayRefreshDone = false;
            if (!XRAY_OUTLINE_BLOCKS.isEmpty()) XRAY_OUTLINE_BLOCKS.clear();
            lastXrayOutlineOrigin = null;
        }

        if (CONFIG.autoSprint && client.options.keyUp.isDown() && !client.player.isCrouching()) {
            client.player.setSprinting(true);
        }

        if (CONFIG.flight) applySmoothFlight(client);
        applyMovementUtilities(client);
        if (CONFIG.autoTotem && ticks % 2 == 0) refillTotem(client);
        if (CONFIG.killAura) runKillAura(client);
        if (CONFIG.xray != lastXrayState) {
            lastXrayState = CONFIG.xray;
            refreshWorldRenderer();
        }
        if (CONFIG.xray) {
            BlockPos origin = client.player.blockPosition();
            if (lastXrayOutlineOrigin == null || blockDistanceSquared(lastXrayOutlineOrigin, origin) >= 256 || ticks % 120 == 0) {
                refreshXrayOutlines(client);
            }
        }

        if (CONFIG.blockHighlights) {
            BlockPos origin = client.player.blockPosition();
            boolean settingsChanged = lastBlockScanMode != CONFIG.blockHighlightMode || lastBlockScanRadius != CONFIG.blockRadius;
            boolean moved = lastBlockScanOrigin == null || blockDistanceSquared(lastBlockScanOrigin, origin) >= 4;
            if (settingsChanged || moved || ticks % 60 == 0) refreshHighlightedBlocks(client);
        } else if (!HIGHLIGHTED_BLOCKS.isEmpty()) {
            HIGHLIGHTED_BLOCKS.clear();
            lastBlockScanOrigin = null;
        }
    }

    private static void updateFreecam(Minecraft client) {
        if (!CONFIG.freecam) {
            freecamPosition = null;
            freecamAnchor = null;
            return;
        }
        if (freecamPosition == null) {
            freecamPosition = client.player.getEyePosition();
            freecamAnchor = client.player.position();
        }

        double forward = (client.options.keyUp.isDown() ? 1.0 : 0.0) - (client.options.keyDown.isDown() ? 1.0 : 0.0);
        double right = (client.options.keyRight.isDown() ? 1.0 : 0.0) - (client.options.keyLeft.isDown() ? 1.0 : 0.0);
        double vertical = (client.options.keyJump.isDown() ? 1.0 : 0.0) - (client.options.keyShift.isDown() ? 1.0 : 0.0);
        double length = Math.sqrt(forward * forward + right * right + vertical * vertical);
        if (length > 1.0) { forward /= length; right /= length; vertical /= length; }

        double speed = CONFIG.freecamSpeed * (client.options.keySprint.isDown() ? 2.5 : 1.0) * 0.35;
        double yaw = Math.toRadians(client.player.getYRot());
        double pitch = Math.toRadians(client.player.getXRot());
        Vec3 look = new Vec3(-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch)).normalize();
        Vec3 side = new Vec3(Math.cos(yaw), 0.0, Math.sin(yaw)).normalize();
        freecamPosition = freecamPosition.add(look.scale(forward * speed)).add(side.scale(-right * speed)).add(0.0, vertical * speed, 0.0);
        client.player.setDeltaMovement(0.0, 0.0, 0.0);
        if (freecamAnchor != null) client.player.setPos(freecamAnchor.x, freecamAnchor.y, freecamAnchor.z);
        client.player.fallDistance = 0.0F;
    }

    private static void updateFullbright(Minecraft client) {
        boolean active = CONFIG.fullbright || CONFIG.xray;
        if (active) {
            if (Double.isNaN(savedGamma)) savedGamma = client.options.gamma().get();
            client.options.gamma().set(1.0);
            if (client.player != null) {
                var effect = BuiltInRegistries.MOB_EFFECT.wrapAsHolder(MobEffects.NIGHT_VISION.value());
                MobEffectInstance current = client.player.getEffect(effect);
                if (current == null || current.getDuration() < 220) {
                    client.player.addEffect(new MobEffectInstance(effect, 420, 0));
                    zenithNightVision = true;
                }
            }
        } else {
            if (!Double.isNaN(savedGamma)) {
                client.options.gamma().set(savedGamma);
                savedGamma = Double.NaN;
            }
            if (client.player != null && zenithNightVision) {
                client.player.removeEffect(BuiltInRegistries.MOB_EFFECT.wrapAsHolder(MobEffects.NIGHT_VISION.value()));
                zenithNightVision = false;
            }
        }
    }

    private static void applySmoothFlight(Minecraft client) {
        if (client.player == null) return;

        double forward = (client.options.keyUp.isDown() ? 1.0 : 0.0) - (client.options.keyDown.isDown() ? 1.0 : 0.0);
        double right = (client.options.keyRight.isDown() ? 1.0 : 0.0) - (client.options.keyLeft.isDown() ? 1.0 : 0.0);
        double vertical = (client.options.keyJump.isDown() ? 1.0 : 0.0) - (client.options.keyShift.isDown() ? 1.0 : 0.0);

        double length = Math.hypot(forward, right);
        if (length > 1.0) { forward /= length; right /= length; }
        double speed = CONFIG.flightSpeed * (client.options.keySprint.isDown() ? CONFIG.flightSprintMultiplier : 1.0);
        double yaw = Math.toRadians(client.player.getYRot());
        double sin = Math.sin(yaw);
        double cos = Math.cos(yaw);
        double dx = (-sin * forward - cos * right) * speed;
        double dz = ( cos * forward - sin * right) * speed;
        double dy = vertical * CONFIG.flightVerticalSpeed;

        // One movement method only. v12 both teleported and applied velocity, causing doubled/inverted motion.
        client.player.setDeltaMovement(dx, dy, dz);
        client.player.fallDistance = 0.0F;
        flightWasActive = true;
    }

    public static void stopFlightMotion() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;
        Vec3 motion = client.player.getDeltaMovement();
        client.player.setDeltaMovement(0.0, Math.max(-0.08, Math.min(0.0, motion.y)), 0.0);
        client.player.fallDistance = 0.0F;
        flightWasActive = false;
    }

    private static void applyMovementUtilities(Minecraft client) {
        if (client.player == null) return;

        if (!CONFIG.flight && flightWasActive) stopFlightMotion();

        // No Slow / No Stun are handled by version-matched mixins. Do not overwrite
        // horizontal velocity here; doing that caused reversed controls and drift.
        if (CONFIG.speed && !CONFIG.flight) {
            double forward = (client.options.keyUp.isDown() ? 1.0 : 0.0) - (client.options.keyDown.isDown() ? 1.0 : 0.0);
            double right = (client.options.keyRight.isDown() ? 1.0 : 0.0) - (client.options.keyLeft.isDown() ? 1.0 : 0.0);
            double length = Math.hypot(forward, right);
            if (length > 0.0) {
                forward /= length;
                right /= length;
                double yaw = Math.toRadians(client.player.getYRot());
                double sin = Math.sin(yaw);
                double cos = Math.cos(yaw);
                double speed = Math.max(0.1, Math.min(10.0, CONFIG.speedAmount)) * 0.12;
                Vec3 motion = client.player.getDeltaMovement();
                client.player.setDeltaMovement((-sin * forward - cos * right) * speed, motion.y, (cos * forward - sin * right) * speed);
            }
        }

        if (CONFIG.airJump) {
            boolean jumpDown = client.options.keyJump.isDown();
            if (jumpDown && !jumpWasDown && !client.player.onGround() && !CONFIG.flight) {
                Vec3 motion = client.player.getDeltaMovement();
                client.player.setDeltaMovement(motion.x, 0.42, motion.z);
                client.player.fallDistance = 0.0F;
            }
            jumpWasDown = jumpDown;
        } else {
            jumpWasDown = client.options.keyJump.isDown();
        }

        // No Fall is handled by mutating the normal outgoing movement packet.
        // Do not inject extra packets or clamp velocity here: both cause visible
        // rubber-banding, duplicate landing sounds, and rough flight landings.
        if (CONFIG.noFall) client.player.fallDistance = 0.0F;
    }


    /** Called from ClientPacketListenerMixin before a movement packet is sent. */
    public static void modifyOutgoingMovementPacket(Object packet) {
        Minecraft client = Minecraft.getInstance();
        if (!CONFIG.noFall || client.player == null || packet == null) return;
        if (!(packet instanceof net.minecraft.network.protocol.game.ServerboundMovePlayerPacket)) return;
        if (client.player.isFallFlying() || client.player.isInWater() || client.player.isInLava()) return;

        // Packet mode: during normal falling only report grounded once
        // descent is meaningful. During Zenith flight, report grounded so
        // toggling flight off and landing remains smooth and damage-free.
        if (CONFIG.flight || client.player.getDeltaMovement().y < -0.5) {
            ((com.zenithclient.client.mixin.ServerboundMovePlayerPacketAccessor) packet).zenith$setOnGround(true);
        }
    }

    /** Called at the start of the vanilla attack method by CriticalsMixin. */
    public static void beforeAttack(Entity target) {
        Minecraft client = Minecraft.getInstance();
        prepareAttributeSwap(client);
        if (client.player == null || target == null) return;
        if (CONFIG.maceKill && isMace(client.player.getMainHandItem())) {
            double x = client.player.getX();
            double y = client.player.getY();
            double z = client.player.getZ();
            double height = Math.max(4.0, Math.min(200.0, CONFIG.maceKillHeight));
            sendMovementPacketReflectively("Pos", x, y + height, z, false);
            sendMovementPacketReflectively("Pos", x, y, z, false);
            client.player.fallDistance = (float) height;
        }
        if (!CONFIG.criticals) return;
        if (!client.player.onGround() || client.player.isInWater() || client.player.isInLava()
                || client.player.isCrouching() || client.player.getAbilities().flying) return;

        double x = client.player.getX();
        double y = client.player.getY();
        double z = client.player.getZ();
        // Two tiny, server-visible vertical offsets make the normal attack satisfy
        // vanilla's airborne critical-hit check without visibly moving the camera.
        sendMovementPacketReflectively("Pos", x, y + 0.0625, z, false);
        sendMovementPacketReflectively("Pos", x, y, z, false);
        client.player.fallDistance = 0.1F;
    }

    /** Called at the end of the vanilla attack method by CriticalsMixin. */
    public static void afterAttack(Entity target) {
        Minecraft client = Minecraft.getInstance();
        if (!replayingAttack && CONFIG.superPunch && client.player != null && client.gameMode != null && target != null) {
            int repeats = Math.max(2, Math.min(8, CONFIG.superPunchPackets));
            replayingAttack = true;
            try {
                for (int i = 0; i < repeats; i++) client.gameMode.attack(client.player, target);
            } finally {
                replayingAttack = false;
            }
        }
        if (restoreSwapSlot >= 0) restoreSwapAfterTick = ticks + 1;
    }

    private static void prepareAttributeSwap(Minecraft client) {
        restoreSwapSlot = -1;
        if (!CONFIG.attributeSwap || client.player == null || client.player.connection == null) return;
        int wanted = Math.max(0, Math.min(8, CONFIG.attributeSwapSlot - 1));
        int current = selectedHotbarSlot(client);
        if (wanted == current) return;
        restoreSwapSlot = current;
        restoreSwapAfterTick = ticks + 1;
        setSelectedHotbarSlot(client, wanted);
        client.player.connection.send(new ServerboundSetCarriedItemPacket(wanted));
    }

    private static void restoreAttributeSwap(Minecraft client) {
        if (restoreSwapSlot < 0 || client.player == null || client.player.connection == null) {
            restoreSwapSlot = -1;
            return;
        }
        int slot = restoreSwapSlot;
        restoreSwapSlot = -1;
        restoreSwapAfterTick = -1;
        setSelectedHotbarSlot(client, slot);
        client.player.connection.send(new ServerboundSetCarriedItemPacket(slot));
    }

    private static boolean isMace(net.minecraft.world.item.ItemStack stack) {
        return stack != null && !stack.isEmpty() && BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().endsWith(":mace");
    }

    private static int selectedHotbarSlot(Minecraft client) {
        Object inventory = client.player.getInventory();
        try {
            return (int) inventory.getClass().getMethod("getSelectedSlot").invoke(inventory);
        } catch (ReflectiveOperationException ignored) {
            try {
                java.lang.reflect.Field field = inventory.getClass().getField("selected");
                return field.getInt(inventory);
            } catch (ReflectiveOperationException ignoredAgain) {
                return 0;
            }
        }
    }

    private static void setSelectedHotbarSlot(Minecraft client, int slot) {
        Object inventory = client.player.getInventory();
        try {
            inventory.getClass().getMethod("setSelectedSlot", int.class).invoke(inventory, slot);
            return;
        } catch (ReflectiveOperationException ignored) {
            try {
                java.lang.reflect.Field field = inventory.getClass().getField("selected");
                field.setInt(inventory, slot);
            } catch (ReflectiveOperationException ignoredAgain) {
                // Server packet still tells the server which hotbar slot to use.
            }
        }
    }

    /**
     * Constructs movement packets by reflection so the project remains compatible
     * with 26.2's extra horizontal-collision constructor flag.
     */
    private static void sendMovementPacketReflectively(String packetKind, double x, double y, double z, boolean onGround) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.player.connection == null) return;
        try {
            Class<?> packetClass = Class.forName("net.minecraft.network.protocol.game.ServerboundMovePlayerPacket$" + packetKind);
            Object packet = null;
            for (var constructor : packetClass.getDeclaredConstructors()) {
                Class<?>[] types = constructor.getParameterTypes();
                Object[] args = new Object[types.length];
                int doubles = 0;
                boolean supported = true;
                for (int i = 0; i < types.length; i++) {
                    Class<?> type = types[i];
                    if (type == double.class) {
                        args[i] = doubles == 0 ? x : doubles == 1 ? y : z;
                        doubles++;
                    } else if (type == float.class) {
                        args[i] = doubles++ == 0 ? client.player.getYRot() : client.player.getXRot();
                    } else if (type == boolean.class) {
                        // The first boolean is onGround; newer versions append
                        // horizontalCollision as a second boolean.
                        int booleanIndex = 0;
                        for (int j = 0; j < i; j++) if (types[j] == boolean.class) booleanIndex++;
                        args[i] = booleanIndex == 0 && onGround;
                    } else {
                        supported = false;
                        break;
                    }
                }
                if (!supported) continue;
                constructor.setAccessible(true);
                packet = constructor.newInstance(args);
                break;
            }
            if (packet == null) return;
            for (var method : client.player.connection.getClass().getMethods()) {
                if (!method.getName().equals("send") || method.getParameterCount() != 1) continue;
                if (method.getParameterTypes()[0].isAssignableFrom(packet.getClass())) {
                    method.invoke(client.player.connection, packet);
                    return;
                }
            }
        } catch (ReflectiveOperationException ignored) {
            // Keep gameplay stable if Mojang changes packet internals again.
        }
    }

    private static void handleModuleKeybinds(Minecraft client) {
        if (currentScreen(client) != null) {
            java.util.Arrays.fill(KEY_STATES, false);
            return;
        }
        long window = client.getWindow().handle();
        int[] keys = {
                CONFIG.playerEspKey, CONFIG.entityHighlightsKey, CONFIG.blockHighlightsKey, CONFIG.trajectoryPreviewKey,
                CONFIG.flightKey, CONFIG.autoSprintKey, CONFIG.fullbrightKey,
                CONFIG.showFpsKey, CONFIG.showCoordinatesKey, CONFIG.xrayKey,
                CONFIG.noSlowKey, CONFIG.noStunKey, CONFIG.noFallKey, CONFIG.criticalsKey, CONFIG.autoTotemKey,
                CONFIG.attributeSwapKey, CONFIG.killAuraKey, CONFIG.reachKey, CONFIG.infiniteReachKey, CONFIG.speedKey, CONFIG.maceKillKey, CONFIG.superPunchKey,
                CONFIG.airJumpKey, CONFIG.freecamKey
        };

        for (int i = 0; i < keys.length; i++) {
            boolean down = keys[i] >= 0 && GLFW.glfwGetKey(window, keys[i]) == GLFW.GLFW_PRESS;
            if (down && !KEY_STATES[i]) toggleByIndex(i);
            KEY_STATES[i] = down;
        }
    }

    private static void toggleByIndex(int index) {
        switch (index) {
            case 0 -> CONFIG.playerEsp = !CONFIG.playerEsp;
            case 1 -> CONFIG.entityHighlights = !CONFIG.entityHighlights;
            case 2 -> CONFIG.blockHighlights = !CONFIG.blockHighlights;
            case 3 -> CONFIG.trajectoryPreview = !CONFIG.trajectoryPreview;
            case 4 -> CONFIG.flight = !CONFIG.flight;
            case 5 -> CONFIG.autoSprint = !CONFIG.autoSprint;
            case 6 -> CONFIG.fullbright = !CONFIG.fullbright;
            case 7 -> CONFIG.showFps = !CONFIG.showFps;
            case 8 -> CONFIG.showCoordinates = !CONFIG.showCoordinates;
            case 9 -> CONFIG.xray = !CONFIG.xray;
            case 10 -> CONFIG.noSlow = !CONFIG.noSlow;
            case 11 -> CONFIG.noStun = !CONFIG.noStun;
            case 12 -> CONFIG.noFall = !CONFIG.noFall;
            case 13 -> CONFIG.criticals = !CONFIG.criticals;
            case 14 -> CONFIG.autoTotem = !CONFIG.autoTotem;
            case 15 -> CONFIG.attributeSwap = !CONFIG.attributeSwap;
            case 16 -> CONFIG.killAura = !CONFIG.killAura;
            case 17 -> CONFIG.reach = !CONFIG.reach;
            case 18 -> CONFIG.infiniteReach = !CONFIG.infiniteReach;
            case 19 -> CONFIG.speed = !CONFIG.speed;
            case 20 -> CONFIG.maceKill = !CONFIG.maceKill;
            case 21 -> CONFIG.superPunch = !CONFIG.superPunch;
            case 22 -> CONFIG.airJump = !CONFIG.airJump;
            case 23 -> CONFIG.freecam = !CONFIG.freecam;
            default -> { return; }
        }
        CONFIG.save();
        sendToggleMessage(index);
        if (index == 4 && !CONFIG.flight) stopFlightMotion();
        if (index == 9) refreshWorldRenderer();
        if (index == 23 && !CONFIG.freecam) {
            freecamPosition = null;
            freecamAnchor = null;
        }
    }

    private static void runKillAura(Minecraft client) {
        if (client.player == null || client.level == null || client.gameMode == null) return;
        if (!isWeapon(client.player.getMainHandItem())) return;
        if (client.player.getAttackStrengthScale(0.0F) < 0.95F) return;
        if (ticks - lastAuraAttackTick < 2) return;

        double range = CONFIG.infiniteReach ? 20.0 : Math.max(1.0, Math.min(20.0, Math.max(CONFIG.killAuraRange, CONFIG.reach ? CONFIG.reachDistance : CONFIG.killAuraRange)));
        double rangeSq = range * range;
        Entity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Entity entity : client.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity living) || entity == client.player || !living.isAlive()) continue;
            if (!matchesAuraTarget(entity)) continue;
            double distance = entity.distanceToSqr(client.player);
            if (distance <= rangeSq && distance < bestDistance) {
                best = entity;
                bestDistance = distance;
            }
        }
        if (best == null) return;
        lastAuraAttackTick = ticks;
        client.gameMode.attack(client.player, best);
        client.player.swing(InteractionHand.MAIN_HAND);
    }

    private static boolean isWeapon(net.minecraft.world.item.ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().toLowerCase(Locale.ROOT);
        return id.contains("sword") || id.contains("axe") || id.contains("mace") || id.contains("trident");
    }

    private static boolean matchesAuraTarget(Entity entity) {
        String search = CONFIG.killAuraSearch == null ? "" : CONFIG.killAuraSearch.trim().toLowerCase(Locale.ROOT);
        if (!search.isEmpty()) {
            String id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString().toLowerCase(Locale.ROOT);
            return matchesRegistrySearch(id, search);
        }
        return matchesEntityMode(entity);
    }

    public static void refreshWorldRenderer() {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null) return;

        int centerX = client.player.blockPosition().getX() >> 4;
        int centerY = client.player.blockPosition().getY() >> 4;
        int centerZ = client.player.blockPosition().getZ() >> 4;
        int horizontalRadius = Math.max(2, client.options.renderDistance().get());
        int minSectionY = levelMinY(client) >> 4;
        int maxSectionY = (levelMaxY(client) - 1) >> 4;

        try {
            for (int sx = centerX - horizontalRadius; sx <= centerX + horizontalRadius; sx++) {
                for (int sz = centerZ - horizontalRadius; sz <= centerZ + horizontalRadius; sz++) {
                    for (int sy = minSectionY; sy <= maxSectionY; sy++) {
                        client.level.setSectionDirtyWithNeighbors(sx, sy, sz);
                    }
                }
            }
        } catch (RuntimeException ignored) {
            // Leave X-Ray state alone; chunks will naturally rebuild as the player moves.
        }
    }

    private static void refillTotem(Minecraft client) {
        if (client.player == null || client.gameMode == null) return;
        Screen screen = currentScreen(client);
        if (screen instanceof ChatScreen || screen instanceof ZenithScreen || screen instanceof ModuleSettingsScreen) return;
        if (client.player.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) return;

        int inventorySlot = -1;
        for (int i = 0; i < client.player.getInventory().getContainerSize(); i++) {
            if (i == net.minecraft.world.entity.player.Inventory.SLOT_OFFHAND) continue;
            if (client.player.getInventory().getItem(i).is(Items.TOTEM_OF_UNDYING)) {
                inventorySlot = i;
                break;
            }
        }
        if (inventorySlot < 0) return;

        int menuSlot = inventorySlot < 9 ? inventorySlot + 36 : inventorySlot;
        client.gameMode.handleContainerInput(client.player.inventoryMenu.containerId, menuSlot, 40, ContainerInput.SWAP, client.player);
    }

    private static Screen currentScreen(Minecraft client) {
        try {
            java.lang.reflect.Field field = Minecraft.class.getField("screen");
            Object value = field.get(client);
            return value instanceof Screen screen ? screen : null;
        } catch (ReflectiveOperationException ignored) {
            try {
                java.lang.reflect.Method method = client.gui.getClass().getMethod("screen");
                Object value = method.invoke(client.gui);
                return value instanceof Screen screen ? screen : null;
            } catch (ReflectiveOperationException ignoredAgain) {
                return null;
            }
        }
    }

    public static boolean handleChatCommand(String message) {
        if (message == null || !message.startsWith(".")) return false;
        String[] parts = message.trim().split("\\s+");
        String command = parts[0].toLowerCase(Locale.ROOT);
        if (!command.equals(".autovaultclip")) {
            warn("Unknown command. Available: .autovaultclip down | up | highest");
            return true;
        }
        String mode = parts.length >= 2 ? parts[1].toLowerCase(Locale.ROOT) : "down";
        if (!mode.equals("down") && !mode.equals("up") && !mode.equals("highest")) {
            warn("Usage: .autovaultclip down | up | highest");
            return true;
        }
        autoVaultClip(mode);
        return true;
    }

    private static void refreshXrayOutlines(Minecraft client) {
        XRAY_OUTLINE_BLOCKS.clear();
        if (client.player == null || client.level == null || !CONFIG.xray) return;
        BlockPos origin = client.player.blockPosition();
        int radius = Math.max(8, Math.min(64, client.options.renderDistance().get() * 8));
        int minY = levelMinY(client);
        int maxY = levelMaxY(client);
        int stride = radius > 48 ? 8 : 6;
        int maxBlocks = 220;
        for (int x = -radius; x <= radius && XRAY_OUTLINE_BLOCKS.size() < maxBlocks; x += stride) {
            for (int z = -radius; z <= radius && XRAY_OUTLINE_BLOCKS.size() < maxBlocks; z += stride) {
                for (int y = Math.max(minY, origin.getY() - 32); y <= Math.min(maxY, origin.getY() + 32) && XRAY_OUTLINE_BLOCKS.size() < maxBlocks; y += stride) {
                    BlockPos pos = new BlockPos(origin.getX() + x, y, origin.getZ() + z);
                    Block block = client.level.getBlockState(pos).getBlock();
                    if (block != Blocks.AIR && XrayHooks.isBlocked(block)) XRAY_OUTLINE_BLOCKS.add(pos.immutable());
                }
            }
        }
        lastXrayOutlineOrigin = origin.immutable();
    }

    private static void setSearchFilter(String type, String[] parts) {
        if (parts.length < 2 || parts[1].equalsIgnoreCase("help")) {
            warn("." + commandName(type) + " search <text>, clear, or list <text>");
            return;
        }
        String action = parts[1].toLowerCase(Locale.ROOT);
        if (action.equals("clear")) {
            if (type.equals("entity")) CONFIG.entitySearch = "";
            else if (type.equals("block")) CONFIG.blockSearch = "";
            else if (type.equals("aura")) CONFIG.killAuraSearch = "";
            else CONFIG.xraySearch = "";
            CONFIG.save();
            refreshHighlightedBlocksIfNeeded();
            refreshWorldRenderer();
            warn(type + " filter cleared.");
            return;
        }
        if (action.equals("list")) {
            String query = joinArgs(parts, 2).toLowerCase(Locale.ROOT);
            warn(type + " matches: " + registryMatches(type, query, 8));
            return;
        }
        String search = action.equals("search") ? joinArgs(parts, 2) : joinArgs(parts, 1);
        if (search.isBlank()) {
            warn("Type a search like ." + commandName(type) + " diamond_ore");
            return;
        }
        if (type.equals("entity")) CONFIG.entitySearch = search;
        else if (type.equals("block")) CONFIG.blockSearch = search;
        else if (type.equals("aura")) CONFIG.killAuraSearch = search;
        else CONFIG.xraySearch = search;
        CONFIG.save();
        refreshHighlightedBlocksIfNeeded();
        refreshWorldRenderer();
        warn(type + " filter set to: " + search);
    }

    private static String commandName(String type) {
        if (type.equals("xray")) return "xrayblocks";
        if (type.equals("aura")) return "killaura";
        return type + "esp";
    }

    private static String joinArgs(String[] parts, int start) {
        StringBuilder builder = new StringBuilder();
        for (int i = start; i < parts.length; i++) {
            if (!builder.isEmpty()) builder.append(' ');
            builder.append(parts[i]);
        }
        return builder.toString().trim();
    }

    private static String registryMatches(String type, String query, int max) {
        List<String> matches = new ArrayList<>();
        if (type.equals("entity") || type.equals("aura")) {
            BuiltInRegistries.ENTITY_TYPE.keySet().forEach(id -> {
                String value = id.toString();
                if ((query.isBlank() || value.contains(query)) && matches.size() < max) matches.add(value);
            });
        } else {
            BuiltInRegistries.BLOCK.keySet().forEach(id -> {
                String value = id.toString();
                if ((query.isBlank() || value.contains(query)) && matches.size() < max) matches.add(value);
            });
        }
        return matches.isEmpty() ? "none" : String.join(", ", matches);
    }

    private static void refreshHighlightedBlocksIfNeeded() {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null && client.level != null && CONFIG.blockHighlights) refreshHighlightedBlocks(client);
    }

    private static void autoVaultClip(String mode) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) return;
        BlockPos target = switch (mode) {
            case "up" -> findClipSpot(true, false);
            case "highest" -> findClipSpot(true, true);
            default -> findClipSpot(false, false);
        };
        if (target == null) {
            warn(mode.equals("down") ? "No safe block level below you. Not clipping into the void." : "No safe block level found.");
            return;
        }
        double x = client.player.getX();
        double y = target.getY();
        double z = client.player.getZ();
        sendMovementPacketReflectively("Pos", x, y, z, client.player.onGround());
        client.player.setPos(x, y, z);
        warn("AutoVaultClip moved " + mode + " to Y " + target.getY() + ".");
    }

    private static BlockPos findClipSpot(boolean upward, boolean highest) {
        Minecraft client = Minecraft.getInstance();
        BlockPos base = client.player.blockPosition();
        int minY = levelMinY(client) + 1;
        int maxY = levelMaxY(client) - 2;
        if (highest) {
            for (int y = maxY; y > base.getY(); y--) {
                BlockPos pos = new BlockPos(base.getX(), y, base.getZ());
                if (safeStandingSpot(pos)) return pos;
            }
            return null;
        }
        if (upward) {
            for (int y = base.getY() + 2; y <= maxY; y++) {
                BlockPos pos = new BlockPos(base.getX(), y, base.getZ());
                if (safeStandingSpot(pos)) return pos;
            }
            return null;
        }
        for (int y = base.getY() - 2; y >= minY; y--) {
            BlockPos pos = new BlockPos(base.getX(), y, base.getZ());
            if (client.level.getBlockState(pos).isAir() && client.level.getBlockState(pos.above()).isAir()) return pos;
        }
        return null;
    }

    private static boolean safeStandingSpot(BlockPos feet) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return false;
        BlockPos below = feet.below();
        return client.level.getBlockState(feet).isAir()
                && client.level.getBlockState(feet.above()).isAir()
                && !client.level.getBlockState(below).getCollisionShape(client.level, below).isEmpty();
    }

    private static int levelMinY(Minecraft client) {
        try {
            return (int) client.level.getClass().getMethod("getMinY").invoke(client.level);
        } catch (ReflectiveOperationException ignored) {
            try {
                return (int) client.level.getClass().getMethod("getMinBuildHeight").invoke(client.level);
            } catch (ReflectiveOperationException ignoredAgain) {
                return -64;
            }
        }
    }

    private static int levelMaxY(Minecraft client) {
        try {
            return (int) client.level.getClass().getMethod("getMaxY").invoke(client.level);
        } catch (ReflectiveOperationException ignored) {
            try {
                return (int) client.level.getClass().getMethod("getMaxBuildHeight").invoke(client.level);
            } catch (ReflectiveOperationException ignoredAgain) {
                return 320;
            }
        }
    }

    public static boolean isFreecamActive() {
        return CONFIG.freecam && freecamPosition != null;
    }

    public static Vec3 freecamPosition() {
        return freecamPosition;
    }

    private static void warn(String text) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;
        client.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("[ZenithClient] " + text));
    }

    public static void setTrajectoryTarget(Entity entity) {
        trajectoryTarget = entity;
    }

    public static boolean isTrajectoryTarget(Entity entity) {
        return CONFIG.trajectoryPreview && entity != null && entity == trajectoryTarget;
    }

    public static boolean shouldGlowEsp(Entity entity) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || entity == null || entity == client.player) return false;
        if (entity.distanceToSqr(client.player) > (double) CONFIG.entityRange * CONFIG.entityRange) return false;
        if (entity instanceof Player) return CONFIG.playerEsp;
        if (entity instanceof ItemEntity) return CONFIG.itemEsp;
        if (entity instanceof Projectile) return CONFIG.projectileEsp;
        return CONFIG.entityHighlights && matchesEntityMode(entity);
    }

    public static boolean isEspControllingGlow() {
        return CONFIG.playerEsp || CONFIG.entityHighlights || CONFIG.itemEsp || CONFIG.projectileEsp || CONFIG.trajectoryPreview;
    }

    public static int espColor(Entity entity) {
        if (isTrajectoryTarget(entity)) return CONFIG.trajectoryColor & 0xFFFFFF;
        if (!shouldGlowEsp(entity)) return -1;
        if (entity instanceof Player) return CONFIG.playerOutlineColor & 0xFFFFFF;
        if (entity instanceof ItemEntity) return CONFIG.itemEspColor & 0xFFFFFF;
        if (entity instanceof Projectile) return CONFIG.projectileEspColor & 0xFFFFFF;
        return CONFIG.entityOutlineColor & 0xFFFFFF;
    }



    public static boolean matchesEntityMode(Entity entity) {
        String typeName = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString().toLowerCase(Locale.ROOT);
        String search = CONFIG.entitySearch == null ? "" : CONFIG.entitySearch.trim().toLowerCase(Locale.ROOT);
        if (!search.isEmpty()) {
            for (String token : search.split(",")) {
                String wanted = token.trim();
                if (!wanted.isEmpty() && typeName.contains(wanted)) return true;
            }
            return false;
        }
        return switch (CONFIG.entityHighlightMode) {
            case PLAYERS -> entity instanceof net.minecraft.world.entity.player.Player;
            case HOSTILE_MOBS -> entity instanceof Monster;
            case PASSIVE_MOBS -> entity instanceof Animal;
            case ZOMBIES -> typeName.endsWith("zombie") || typeName.contains("zombie_");
            case CREEPERS -> typeName.endsWith("creeper");
            case SKELETONS -> typeName.endsWith("skeleton");
            case ALL_MOBS -> entity instanceof Mob;
            case ALL_ENTITIES -> true;
        };
    }

    private static void refreshHighlightedBlocks(Minecraft client) {
        HIGHLIGHTED_BLOCKS.clear();
        if (!CONFIG.blockHighlights) return;
        BlockPos origin = client.player.blockPosition();
        int radius = Math.max(4, Math.min(128, CONFIG.blockRadius));
        int radiusSquared = radius * radius;
        int maxBlocks = radius > 48 ? 1536 : 768;
        for (int x = -radius; x <= radius && HIGHLIGHTED_BLOCKS.size() < maxBlocks; x++) {
            for (int y = -radius; y <= radius && HIGHLIGHTED_BLOCKS.size() < maxBlocks; y++) {
                for (int z = -radius; z <= radius && HIGHLIGHTED_BLOCKS.size() < maxBlocks; z++) {
                    if (x * x + y * y + z * z > radiusSquared) continue;
                    BlockPos pos = origin.offset(x, y, z);
                    Block block = client.level.getBlockState(pos).getBlock();
                    if (matchesBlockMode(block)) HIGHLIGHTED_BLOCKS.add(pos.immutable());
                }
            }
        }
        lastBlockScanOrigin = origin.immutable();
        lastBlockScanMode = CONFIG.blockHighlightMode;
        lastBlockScanRadius = CONFIG.blockRadius;
    }

    private static int blockDistanceSquared(BlockPos a, BlockPos b) {
        int dx = a.getX() - b.getX();
        int dy = a.getY() - b.getY();
        int dz = a.getZ() - b.getZ();
        return dx * dx + dy * dy + dz * dz;
    }

    private static boolean matchesBlockMode(Block block) {
        String search = CONFIG.blockSearch == null ? "" : CONFIG.blockSearch.trim().toLowerCase(Locale.ROOT);
        if (!search.isEmpty()) return matchesRegistrySearch(BuiltInRegistries.BLOCK.getKey(block).toString(), search);
        return switch (CONFIG.blockHighlightMode) {
            case VALUABLE_ORES -> isValuableOre(block);
            case ALL_ORES -> isAnyOre(block);
            case CONTAINERS -> isContainer(block);
            case SPAWNERS -> block == Blocks.SPAWNER;
            case ANCIENT_DEBRIS -> block == Blocks.ANCIENT_DEBRIS;
            case DIAMOND_ORE -> block == Blocks.DIAMOND_ORE || block == Blocks.DEEPSLATE_DIAMOND_ORE;
        };
    }

    private static boolean matchesRegistrySearch(String id, String search) {
        String lower = id.toLowerCase(Locale.ROOT);
        for (String token : search.split(",")) {
            String wanted = token.trim().toLowerCase(Locale.ROOT);
            if (!wanted.isEmpty() && lower.contains(wanted)) return true;
        }
        return false;
    }

    private static boolean isValuableOre(Block block) {
        return block == Blocks.DIAMOND_ORE || block == Blocks.DEEPSLATE_DIAMOND_ORE
                || block == Blocks.EMERALD_ORE || block == Blocks.DEEPSLATE_EMERALD_ORE
                || block == Blocks.GOLD_ORE || block == Blocks.DEEPSLATE_GOLD_ORE
                || block == Blocks.NETHER_GOLD_ORE || block == Blocks.ANCIENT_DEBRIS;
    }

    private static boolean isAnyOre(Block block) {
        return isValuableOre(block)
                || block == Blocks.COAL_ORE || block == Blocks.DEEPSLATE_COAL_ORE
                || block == Blocks.IRON_ORE || block == Blocks.DEEPSLATE_IRON_ORE
                || block == Blocks.COPPER_ORE || block == Blocks.DEEPSLATE_COPPER_ORE
                || block == Blocks.REDSTONE_ORE || block == Blocks.DEEPSLATE_REDSTONE_ORE
                || block == Blocks.LAPIS_ORE || block == Blocks.DEEPSLATE_LAPIS_ORE
                || block == Blocks.NETHER_QUARTZ_ORE;
    }

    private static boolean isContainer(Block block) {
        return block == Blocks.CHEST || block == Blocks.TRAPPED_CHEST || block == Blocks.BARREL
                || block == Blocks.ENDER_CHEST || block == Blocks.SHULKER_BOX;
    }

    private static void renderHud(net.minecraft.client.gui.GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;
        ScreenSpaceVisualRenderer.render(graphics, client, deltaTracker, CONFIG, HIGHLIGHTED_BLOCKS, XRAY_OUTLINE_BLOCKS);
        int y = 6;
        if (CONFIG.showFps) {
            graphics.text(client.font, "Zenith | " + client.getFps() + " FPS", 6, y,
                    0xFF000000 | (CONFIG.uiAccentColor & 0x00FFFFFF), true);
            y += 11;
        }
        if (CONFIG.showCoordinates) {
            graphics.text(client.font,
                    String.format("XYZ %.1f / %.1f / %.1f", client.player.getX(), client.player.getY(), client.player.getZ()),
                    6, y, 0xFFFFFFFF, true);
        }

        if (!toggleNotice.isEmpty() && System.currentTimeMillis() < toggleNoticeUntilMs) {
            int noticeX = 6;
            int noticeY = client.getWindow().getGuiScaledHeight() - 30;
            graphics.text(client.font, toggleNotice, noticeX, noticeY,
                    0xFF000000 | (CONFIG.uiAccentColor & 0x00FFFFFF), true);
        }
    }
    private static void sendToggleMessage(int index) {
        Minecraft client = Minecraft.getInstance();
        if (!CONFIG.chatToggleMessages || client.player == null) return;
        String name; boolean enabled;
        switch (index) {
            case 0 -> { name = "Player ESP"; enabled = CONFIG.playerEsp; }
            case 1 -> { name = "Entity ESP"; enabled = CONFIG.entityHighlights; }
            case 2 -> { name = "Block ESP"; enabled = CONFIG.blockHighlights; }
            case 3 -> { name = "Trajectories"; enabled = CONFIG.trajectoryPreview; }
            case 4 -> { name = "Flight"; enabled = CONFIG.flight; }
            case 5 -> { name = "Auto Sprint"; enabled = CONFIG.autoSprint; }
            case 6 -> { name = "Fullbright"; enabled = CONFIG.fullbright; }
            case 7 -> { name = "FPS HUD"; enabled = CONFIG.showFps; }
            case 8 -> { name = "Coordinates HUD"; enabled = CONFIG.showCoordinates; }
            case 9 -> { name = "X-Ray"; enabled = CONFIG.xray; }
            case 10 -> { name = "No Slow"; enabled = CONFIG.noSlow; }
            case 11 -> { name = "No Stun"; enabled = CONFIG.noStun; }
            case 12 -> { name = "No Fall"; enabled = CONFIG.noFall; }
            case 13 -> { name = "Criticals"; enabled = CONFIG.criticals; }
            case 14 -> { name = "Auto Totem"; enabled = CONFIG.autoTotem; }
            case 15 -> { name = "Attribute Swap"; enabled = CONFIG.attributeSwap; }
            case 16 -> { name = "Kill Aura"; enabled = CONFIG.killAura; }
            case 17 -> { name = "Reach"; enabled = CONFIG.reach; }
            case 18 -> { name = "Infinite Reach"; enabled = CONFIG.infiniteReach; }
            case 19 -> { name = "Speed"; enabled = CONFIG.speed; }
            case 20 -> { name = "Mace Kill"; enabled = CONFIG.maceKill; }
            case 21 -> { name = "Super Punch"; enabled = CONFIG.superPunch; }
            case 22 -> { name = "Air Jump"; enabled = CONFIG.airJump; }
            case 23 -> { name = "Freecam"; enabled = CONFIG.freecam; }
            default -> { return; }
        }
        net.minecraft.network.chat.MutableComponent message = net.minecraft.network.chat.Component.literal("[")
                .withStyle(net.minecraft.ChatFormatting.DARK_GRAY)
                .append(net.minecraft.network.chat.Component.literal("ZenithClient").withStyle(net.minecraft.ChatFormatting.GOLD))
                .append(net.minecraft.network.chat.Component.literal("] ").withStyle(net.minecraft.ChatFormatting.DARK_GRAY))
                .append(net.minecraft.network.chat.Component.literal(name + " ").withStyle(net.minecraft.ChatFormatting.GRAY))
                .append(net.minecraft.network.chat.Component.literal(enabled ? "ON" : "OFF")
                        .withStyle(enabled ? net.minecraft.ChatFormatting.GREEN : net.minecraft.ChatFormatting.RED));
        client.player.sendSystemMessage(message);
    }

}
