package dev.latvian.mods.kubejs.player;

import dev.architectury.event.EventResult;
import dev.architectury.event.events.common.ChatEvent;
import dev.architectury.event.events.common.PlayerEvent;
import dev.architectury.event.events.common.TickEvent;
import dev.latvian.mods.kubejs.CommonProperties;
import dev.latvian.mods.kubejs.KubeJS;
import dev.latvian.mods.kubejs.bindings.event.PlayerEvents;
import dev.latvian.mods.kubejs.script.AttachDataEvent;
import dev.latvian.mods.kubejs.script.ScriptType;
import dev.latvian.mods.kubejs.stages.Stages;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.Advancement;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.InventoryMenu;
import org.jetbrains.annotations.NotNull;

/**
 * @author LatvianModder
 */
public class KubeJSPlayerEventHandler {
	public static void init() {
		PlayerEvent.PLAYER_JOIN.register(KubeJSPlayerEventHandler::loggedIn);
		PlayerEvent.PLAYER_QUIT.register(KubeJSPlayerEventHandler::loggedOut);
		PlayerEvent.PLAYER_RESPAWN.register(KubeJSPlayerEventHandler::respawn);
		PlayerEvent.PLAYER_CLONE.register(KubeJSPlayerEventHandler::cloned);
		TickEvent.PLAYER_POST.register(KubeJSPlayerEventHandler::tick);
		ChatEvent.SERVER.register(KubeJSPlayerEventHandler::chat);
		PlayerEvent.PLAYER_ADVANCEMENT.register(KubeJSPlayerEventHandler::advancement);
		PlayerEvent.OPEN_MENU.register(KubeJSPlayerEventHandler::inventoryOpened);
		PlayerEvent.CLOSE_MENU.register(KubeJSPlayerEventHandler::inventoryClosed);
	}

	public static void loggedIn(ServerPlayer player) {
		var p = new ServerPlayerDataJS(player.server, player.getUUID(), player.getGameProfile().getName(), KubeJS.nextClientHasClientMod);
		KubeJS.nextClientHasClientMod = false;
		player.server.kjs$getPlayerMap().put(p.getId(), p);
		AttachDataEvent.forPlayer(p).invoke();
		PlayerEvents.LOGGED_IN.post(new SimplePlayerEventJS(player));
		player.inventoryMenu.addSlotListener(new InventoryListener(player));

		if (!ScriptType.SERVER.errors.isEmpty() && !CommonProperties.get().hideServerScriptErrors) {
			player.displayClientMessage(Component.literal("KubeJS errors found [" + ScriptType.SERVER.errors.size() + "]! Run ")
							.append(Component.literal("'/kubejs errors'")
									.click(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/kubejs errors"))
									.hover(Component.literal("Click to run")))
							.append(Component.literal(" for more info"))
							.withStyle(ChatFormatting.DARK_RED),
					false);
		}

		Stages.get(player).sync();
	}

	private static void respawn(ServerPlayer player, boolean b) {
		Stages.get(player).sync();
	}

	public static void loggedOut(ServerPlayer player) {
		if (!player.server.kjs$getPlayerMap().containsKey(player.getUUID())) {
			return;
		}

		PlayerEvents.LOGGED_OUT.post(new SimplePlayerEventJS(player));
		player.server.kjs$getPlayerMap().remove(player.getUUID());
	}

	public static void cloned(ServerPlayer oldPlayer, ServerPlayer newPlayer, boolean wonGame) {
		newPlayer.kjs$getPersistentData().merge(oldPlayer.kjs$getPersistentData());
		newPlayer.inventoryMenu.addSlotListener(new InventoryListener(newPlayer));
	}

	public static void tick(Player player) {
		if (player instanceof ServerPlayer) {
			PlayerEvents.TICK.post(new SimplePlayerEventJS(player));
		}
	}

	@NotNull
	public static EventResult chat(ServerPlayer player, ChatEvent.ChatComponent component) {
		var event = new PlayerChatEventJS(player, component.getRaw());
		if (PlayerEvents.CHAT.post(event)) {
			return EventResult.interruptFalse();
		}
		component.setRaw(event.component);
		return EventResult.pass();
	}

	public static void advancement(ServerPlayer player, Advancement advancement) {
		PlayerEvents.ADVANCEMENT.post(String.valueOf(advancement.getId()), new PlayerAdvancementEventJS(player, advancement));
	}

	public static void inventoryOpened(Player player, AbstractContainerMenu menu) {
		if (player instanceof ServerPlayer serverPlayer) {
			if (!(menu instanceof InventoryMenu)) {
				menu.addSlotListener(new InventoryListener(serverPlayer));
			}

			PlayerEvents.INVENTORY_OPENED.post(new InventoryEventJS(serverPlayer, menu));

			if (menu instanceof ChestMenu) {
				PlayerEvents.CHEST_OPENED.post(new ChestEventJS(serverPlayer, menu));
			}
		}
	}

	public static void inventoryClosed(Player player, AbstractContainerMenu menu) {
		if (player instanceof ServerPlayer serverPlayer) {
			PlayerEvents.INVENTORY_CLOSED.post(new InventoryEventJS(serverPlayer, menu));

			if (menu instanceof ChestMenu) {
				PlayerEvents.CHEST_CLOSED.post(new ChestEventJS(serverPlayer, menu));
			}
		}
	}
}