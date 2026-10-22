package com.braden.hearthstone;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.item.ItemUseAnimation;

/**
 * Settings live in config/hearthstone.json (created on first launch).
 * The file is re-read every time you start a cast, so you can edit it while the game is running.
 */
public final class HearthConfig {
	private static final Logger LOGGER = LoggerFactory.getLogger("hearthstone");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("hearthstone.json");

	/** Field names here are exactly the keys in the JSON file. */
	private static final class Data {
		/** How long the channel takes, in seconds. */
		double castSeconds = 10.0;
		/** Cooldown after a successful hearth, in minutes. Fractions are fine (0.5 = 30 seconds). 0 = no cooldown. */
		double cooldownMinutes = 30.0;
		/** How far (in blocks) you can drift before the cast is interrupted. */
		double moveToleranceBlocks = 0.25;
		/** Set false to allow moving while channeling. */
		boolean interruptOnMove = true;
		/** Set false to keep channeling when you take damage. */
		boolean interruptOnDamage = true;
		/** Show the blue cast bar at the top of the screen. */
		boolean showCastBar = true;
		/** Blue swirl while casting and a burst on arrival. */
		boolean showParticles = true;
		/**
		 * Hand pose while channeling. Try NONE, BLOCK, SPYGLASS, TOOT_HORN, BOW, BRUSH, SPEAR, CROSSBOW, EAT, DRINK.
		 * NONE means no special pose at all.
		 */
		String useAnimation = "BLOCK";
	}

	private static volatile Data data = new Data();
	private static volatile ItemUseAnimation animation = ItemUseAnimation.SPYGLASS;
	private static long loadedStamp = Long.MIN_VALUE;

	private static void apply(Data newData) {
		data = newData;
		try {
			animation = ItemUseAnimation.valueOf(String.valueOf(newData.useAnimation).trim().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			LOGGER.warn("Unknown useAnimation '{}' in hearthstone.json. Valid options: {}",
					newData.useAnimation, Arrays.toString(ItemUseAnimation.values()));
		}
	}

	/** Reads the config file (writing defaults if it doesn't exist yet). Cheap if the file hasn't changed. */
	public static synchronized void reload() {
		try {
			if (!Files.exists(FILE)) {
				apply(new Data());
				Files.createDirectories(FILE.getParent());
				try (Writer writer = Files.newBufferedWriter(FILE)) {
					GSON.toJson(data, writer);
				}
				loadedStamp = Files.getLastModifiedTime(FILE).toMillis();
				return;
			}

			long stamp = Files.getLastModifiedTime(FILE).toMillis();
			if (stamp == loadedStamp) {
				return;
			}
			loadedStamp = stamp; // set first so a broken file only logs once per edit

			try (Reader reader = Files.newBufferedReader(FILE)) {
				Data parsed = GSON.fromJson(reader, Data.class);
				if (parsed != null) {
					apply(parsed);
				}
			}
		} catch (Exception e) {
			LOGGER.warn("Could not read config/hearthstone.json; keeping the previous values", e);
		}
	}

	public static int castTicks() {
		return Math.max(1, (int) Math.round(data.castSeconds * 20.0));
	}

	public static int cooldownTicks() {
		return Math.max(0, (int) Math.round(data.cooldownMinutes * 60.0 * 20.0));
	}

	public static double moveTolerance() {
		return Math.max(0.0, data.moveToleranceBlocks);
	}

	public static boolean interruptOnMove() {
		return data.interruptOnMove;
	}

	public static boolean interruptOnDamage() {
		return data.interruptOnDamage;
	}

	public static boolean showCastBar() {
		return data.showCastBar;
	}

	public static boolean showParticles() {
		return data.showParticles;
	}

	public static ItemUseAnimation useAnimation() {
		return animation;
	}

	private HearthConfig() {
	}
}
