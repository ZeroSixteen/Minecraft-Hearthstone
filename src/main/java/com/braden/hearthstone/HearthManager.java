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
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

/**
 * Server-side bookkeeping for Hearthstone channels. The item's use timer does the timing;
 * this class adds the WoW-style interrupts (movement, damage), shows the cast bar, and performs the teleport.
 *
 * Progress is shown on a boss bar and in chat, never the action bar, so it can't fight with other
 * mods (day counters etc.) that keep writing to the action bar.
 */
public final class HearthManager {
	private static final Map<UUID, Cast> CASTS = new HashMap<>();

	private static final class Cast {
		final Vec3 origin;
		final int totalTicks;
		/** Null when the cast bar is turned off in the config. */
		final ServerBossEvent bar;
		/** Translation key to show (and abort) if the cast can't work, e.g. no bed. Null when fine. */
		final String invalidKey;
		float lastHealth;
		int lastSecondsShown;
		/** Set by the item when the use timer completes. */
		boolean finished;

		Cast(ServerPlayer player, int totalTicks, String invalidKey, ServerBossEvent bar) {
			this.origin = player.position();
			this.lastHealth = player.getHealth();
			this.totalTicks = totalTicks;
			this.invalidKey = invalidKey;
			this.bar = bar;
			this.lastSecondsShown = secondsFor(totalTicks);
		}

		void close() {
			if (bar != null) {
				bar.removeAllPlayers();
			}
		}
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(HearthManager::tick);
		// The integrated server can run several worlds in one session; don't leak casts between them.
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			CASTS.values().forEach(Cast::close);
			CASTS.clear();
		});
	}

	private static int secondsFor(int ticks) {
		return (int) Math.ceil(ticks / 20.0);
	}

	/** Called by the item on right-click (server side). The use animation has already started. */
	public static void tryStart(ServerPlayer player) {
		Cast previous = CASTS.remove(player.getUUID());
		if (previous != null) {
			previous.close();
		}

		int totalTicks = HearthConfig.castTicks();

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

		ServerBossEvent bar = null;
		if (invalidKey == null && HearthConfig.showCastBar()) {
			bar = new ServerBossEvent(
					UUID.randomUUID(),
					Component.translatable("message.hearthstone.casting", secondsFor(totalTicks)),
					BossEvent.BossBarColor.BLUE,
					BossEvent.BossBarOverlay.PROGRESS);
			bar.setProgress(0.0F);
			bar.addPlayer(player);
		}

		CASTS.put(player.getUUID(), new Cast(player, totalTicks, invalidKey, bar));

		if (invalidKey == null) {
			ServerLevel level = (ServerLevel) player.level();
			level.playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8F, 1.4F);
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
				cast.close();
				it.remove();
				continue;
			}

			// Channel ran its full length: do the teleport now, outside the player's own tick.
			if (cast.finished) {
				cast.close();
				it.remove();
				complete(player);
				continue;
			}

			// Couldn't start (no bed / blocked). Say why, drop the raised item, done.
			if (cast.invalidKey != null) {
				cast.close();
				it.remove();
				player.stopUsingItem();
				say(player, Component.translatable(cast.invalidKey));
				continue;
			}

			// Player let go of right-click or switched slots: vanilla already ended the use.
			if (!player.isUsingItem() || !player.getUseItem().is(ModItems.HEARTHSTONE)) {
				cast.close();
				it.remove();
				interrupted(player);
				continue;
			}

			boolean moved = HearthConfig.interruptOnMove()
					&& player.position().distanceToSqr(cast.origin) > HearthConfig.moveTolerance() * HearthConfig.moveTolerance();
			boolean hurt = HearthConfig.interruptOnDamage() && player.getHealth() < cast.lastHealth;
			if (moved || hurt) {
				cast.close();
				it.remove();
				player.stopUsingItem();
				interrupted(player);
				continue;
			}

			cast.lastHealth = player.getHealth();

			int remaining = Math.max(0, player.getUseItemRemainingTicks());
			if (cast.bar != null) {
				float progress = 1.0F - (remaining / (float) cast.totalTicks);
				cast.bar.setProgress(Math.min(1.0F, Math.max(0.0F, progress)));

				int seconds = secondsFor(remaining);
				if (seconds != cast.lastSecondsShown) {
					cast.lastSecondsShown = seconds;
					cast.bar.setName(Component.translatable("message.hearthstone.casting", seconds));
				}
			}

			if (HearthConfig.showParticles()) {
				swirl(player, cast.totalTicks - remaining, cast.totalTicks);
			}
		}
	}

	private static void interrupted(ServerPlayer player) {
		say(player, Component.translatable("message.hearthstone.interrupted"));
		((ServerLevel) player.level()).playSound(null, player.blockPosition(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.8F, 1.0F);
	}

	private static void complete(ServerPlayer player) {
		// Still false so a respawn anchor isn't drained by hearthing.
		TeleportTransition destination = player.findRespawnPositionAndUseSpawnBlock(false, TeleportTransition.DO_NOTHING);
		if (destination.missingRespawnBlock()) {
			say(player, Component.translatable("message.hearthstone.bed_missing"));
			return;
		}

		ServerLevel from = (ServerLevel) player.level();
		from.playSound(null, player.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F);
		if (HearthConfig.showParticles()) {
			burst(from, player);
		}

		player.teleport(destination);

		ServerLevel to = (ServerLevel) player.level();
		to.playSound(null, player.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F);
		if (HearthConfig.showParticles()) {
			burst(to, player);
		}

		int cooldown = HearthConfig.cooldownTicks();
		if (cooldown > 0) {
			player.getCooldowns().addCooldown(new ItemStack(ModItems.HEARTHSTONE), cooldown);
		}
	}

	/** Two blue flames spiral up around the player, tightening toward them as the channel nears its end. */
	private static void swirl(ServerPlayer player, int elapsedTicks, int totalTicks) {
		ServerLevel level = (ServerLevel) player.level();
		double progress = Math.min(1.0, elapsedTicks / (double) totalTicks);
		double radius = 1.2 - 0.8 * progress;
		double angle = elapsedTicks * 0.45;
		double rise = ((elapsedTicks % 30) / 30.0) * 2.0;
		for (int i = 0; i < 2; i++) {
			double a = angle + i * Math.PI;
			level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
					player.getX() + Math.cos(a) * radius,
					player.getY() + rise,
					player.getZ() + Math.sin(a) * radius,
					1, 0.0, 0.0, 0.0, 0.0);
		}
	}

	/** Blue flash with a few white sparkles, used when leaving and arriving. */
	private static void burst(ServerLevel level, ServerPlayer player) {
		level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, player.getX(), player.getY() + 1.0, player.getZ(), 40, 0.4, 0.8, 0.4, 0.12);
		level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1.0, player.getZ(), 14, 0.4, 0.8, 0.4, 0.08);
	}

	/** Plain chat message (not the action bar). */
	private static void say(ServerPlayer player, Component message) {
		player.sendSystemMessage(message, false);
	}

	private HearthManager() {
	}
}
