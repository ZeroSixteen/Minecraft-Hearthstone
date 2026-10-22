package com.braden.hearthstone;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;

public final class ModItemIds {
	public static final ResourceKey<Item> HEARTHSTONE = create("hearthstone");

	private static ResourceKey<Item> create(String name) {
		return ResourceKey.create(Registries.ITEM, HearthstoneMod.id(name));
	}

	private ModItemIds() {
	}
}
