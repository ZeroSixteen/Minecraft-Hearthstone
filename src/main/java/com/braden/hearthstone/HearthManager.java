package com.braden.hearthstone;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

/**
 * Server-side bookkeeping for Hearthstone channels. The item's use timer does the timing;
 * this class adds the WoW-style interrupts (movement, damage) and performs the teleport.
 */
public final class HearthManager {
	private static final Map<UUID, Cast> CASTS = new HashMap<>();

	private static final class Cast {
		final Vec3 origin;
		float lastHealth;
		/** Translation key to show (and abort) if the cast can't work, e.g. no bed. Null when fine. */
		final String invalidKey;
		/** Set by the item when the use timer completes. */
		boolean finished;

		Cast(ServerPlayer player, String invalidKey) {
			this.origin = player.position();
			this.lastHealth = player.getHealth();
			this.invalidKey = invalidKey;
		}
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(HearthManager::tick);
		// The integrated server can run several worlds in one session; don't leak casts between them.
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> CASTS.clear());
	}

	/** Called by the item on right-click (server side). The use animation has already started. */
	public static void tryStart(ServerPlayer player) {
		String invalidKey = null;
		if (player.getRespawnConfig() == null) {
			invalidKey = "message.hearthstone.no_bed";
		} else {
			// Dry run (false = don't consume a respawn anchor charge) to check the bed still exists and isn't blocked.
			TeleportTransition destination = player.findRespawnPositionAndUseSpawnBlock(false, TeleportTransition.DO_NOTHING);
			if (destination.missingRespawnBlock()) {
				invalidKey = "message.hearthstone.bed_missing";
			}
		}

		CASTS.put(player.getUUID(), new Cast(player, invalidKey));

		if (invalidKey == null) {
			ServerLevel level = (ServerLevel) player.level();
			level.playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8F, 1.4F);
			player.displayClientMessage(Component.translatable("message.hearthstone.casting", HearthConfig.CAST_SECONDS), true);
		}
	}

	/** Called by the item when the channel runs its full length. */
	public static void markFinished(ServerPlayer player) {
		Cast cast = CASTS.get(player.getUUID());
		if (cast != null) {
			cast.finished = true;
		}
	}

	private static void tick(MinecraftServer server) {
		if (CASTS.isEmpty()) {
			return;
		}
		Iterator<Map.Entry<UUID, Cast>> it = CASTS.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, Cast> entry = it.next();
			Cast cast = entry.getValue();
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());

			if (player == null || !player.isAlive()) {
				it.remove();
				continue;
			}

			// Channel ran its full length: do the teleport now, outside the player's own tick.
			if (cast.finished) {
				it.remove();
				complete(player);
				continue;
			}

			// Couldn't start (no bed / blocked). Message, drop the raised item, done.
			if (cast.invalidKey != null) {
				it.remove();
				player.stopUsingItem();
				player.displayClientMessage(Component.translatable(cast.invalidKey), true);
				continue;
			}

			// Player let go of right-click or switched slots: vanilla already ended the use.
			if (!player.isUsingItem() || !player.getUseItem().is(ModItems.HEARTHSTONE)) {
				it.remove();
				interrupted(player);
				continue;
			}

			boolean moved = player.position().distanceToSqr(cast.origin) > HearthConfig.MOVE_TOLERANCE * HearthConfig.MOVE_TOLERANCE;
			boolean hurt = player.getHealth() < cast.lastHealth;
			if (moved || hurt) {
				it.remove();
				player.stopUsingItem();
				interrupted(player);
				continue;
			}

			cast.lastHealth = player.getHealth();

			int remaining = player.getUseItemRemainingTicks();
			ServerLevel level = (ServerLevel) player.level();
			if (remaining % 4 == 0) {
				level.sendParticles(ParticleTypes.PORTAL, player.getX(), player.getY() + 1.0, player.getZ(), 6, 0.4, 0.8, 0.4, 0.2);
			}
			if (remaining > 0 && remaining % 20 == 0) {
				player.displayClientMessage(Component.translatable("message.hearthstone.casting", remaining / 20), true);
			}
		}
	}

	private static void interrupted(ServerPlayer player) {
		player.displayClientMessage(Component.translatable("message.hearthstone.interrupted"), true);
		((ServerLevel) player.level()).playSound(null, player.blockPosition(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.8F, 1.0F);
	}

	private static void complete(ServerPlayer player) {
		// Still false so a respawn anchor isn't drained by hearthing.
		TeleportTransition destination = player.findRespawnPositionAndUseSpawnBlock(false, TeleportTransition.DO_NOTHING);
		if (destination.missingRespawnBlock()) {
			player.displayClientMessage(Component.translatable("message.hearthstone.bed_missing"), true);
			return;
		}

		ServerLevel from = (ServerLevel) player.level();
		from.playSound(null, player.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F);
		from.sendParticles(ParticleTypes.PORTAL, player.getX(), player.getY() + 1.0, player.getZ(), 40, 0.4, 0.8, 0.4, 0.5);

		player.teleport(destination);

		ServerLevel to = (ServerLevel) player.level();
		to.playSound(null, player.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F);
		to.sendParticles(ParticleTypes.PORTAL, player.getX(), player.getY() + 1.0, player.getZ(), 40, 0.4, 0.8, 0.4, 0.5);

		player.getCooldowns().addCooldown(new ItemStack(ModItems.HEARTHSTONE), HearthConfig.COOLDOWN_TICKS);
	}

	private HearthManager() {
	}
}
