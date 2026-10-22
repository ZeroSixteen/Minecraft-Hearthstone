package com.braden.hearthstone;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.level.Level;

/**
 * Hold right-click to channel: the stone is raised in your hand for the whole cast (vanilla item-use),
 * and the teleport fires when the use timer runs out. Letting go cancels it.
 */
public class HearthstoneItem extends Item {
	public HearthstoneItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		// Pick up any edits to config/hearthstone.json before the use timer is read.
		HearthConfig.reload();

		// Both sides start "using" so the client plays the raise animation; the server tracks the cast.
		player.startUsingItem(hand);
		if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
			HearthManager.tryStart(serverPlayer);
		}
		return InteractionResult.CONSUME;
	}

	/** How long the channel lasts, in ticks (from the config). */
	@Override
	public int getUseDuration(ItemStack stack, LivingEntity entity) {
		return HearthConfig.castTicks();
	}

	/** The hold-up pose. Other options: BOW, TOOT_HORN, BRUSH, BLOCK, SPEAR, CROSSBOW, BUNDLE. */
	@Override
	public ItemUseAnimation getUseAnimation(ItemStack stack) {
		return ItemUseAnimation.SPYGLASS;
	}

	/** Runs when the use timer completes. The teleport itself is deferred to the end of the server tick. */
	@Override
	public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
		if (!level.isClientSide() && entity instanceof ServerPlayer serverPlayer) {
			HearthManager.markFinished(serverPlayer);
		}
		return stack;
	}
}
