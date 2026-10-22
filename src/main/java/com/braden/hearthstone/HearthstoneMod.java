package com.braden.hearthstone;

import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;

public class HearthstoneMod implements ModInitializer {
	public static final String MOD_ID = "hearthstone";

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		ModItems.initialize();
		HearthManager.init();
	}
}
