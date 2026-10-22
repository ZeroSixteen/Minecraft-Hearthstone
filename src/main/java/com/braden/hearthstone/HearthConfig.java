package com.braden.hearthstone;

/** Tweak these and rebuild. 20 ticks = 1 second. */
public final class HearthConfig {
	/** How long the channel takes. WoW uses 10 seconds. */
	public static final int CAST_SECONDS = 10;

	/** Cooldown applied after a successful hearth. WoW uses 30 minutes. */
	public static final int COOLDOWN_MINUTES = 30;

	/** How far (in blocks) you can drift before the cast is interrupted. */
	public static final double MOVE_TOLERANCE = 0.25;

	public static final int CAST_TICKS = CAST_SECONDS * 20;
	public static final int COOLDOWN_TICKS = COOLDOWN_MINUTES * 60 * 20;

	private HearthConfig() {
	}
}
