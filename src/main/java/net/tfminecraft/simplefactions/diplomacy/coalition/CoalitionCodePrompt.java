package net.tfminecraft.simplefactions.diplomacy.coalition;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;

import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.managers.LogManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.war.declare.WarDeclareCodeService;

/**
 * Takes the staff code that makes a coalition offensive from chat, the same way
 * {@code DeclareCodePrompt} takes a war code. The code is spent off the main thread,
 * and the coalition changes back on it.
 */
public class CoalitionCodePrompt implements Listener {
	private static final long PROMPT_TIMEOUT_TICKS = 20L * 60;

	private static final Map<Player, Pending> pending = new ConcurrentHashMap<>();

	static final class Pending {
		final String coalitionId;
		final String leaderId;
		final String targetId;
		final AtomicBoolean submitted = new AtomicBoolean();

		Pending(String coalitionId, String leaderId, String targetId) {
			this.coalitionId = coalitionId;
			this.leaderId = leaderId;
			this.targetId = targetId;
		}
	}

	public static void begin(Player player, Faction leader) {
		if (!CoalitionSettings.enabled) {
			player.sendMessage(CoalitionService.DISABLED);
			return;
		}
		Coalition coalition = CoalitionService.of(leader);
		if (coalition == null || !coalition.isLeader(leader.getId())) {
			player.sendMessage(CoalitionService.NOT_LEADER);
			return;
		}
		if (coalition.isOffensive()) {
			player.sendMessage("§cThe coalition is already offensive.");
			return;
		}
		if (CoalitionService.targetLost(coalition)) {
			player.sendMessage("§cChoose a new target before asking for an offensive code.");
			return;
		}
		Pending state = new Pending(coalition.getId(), leader.getId(), coalition.getTargetId());
		pending.put(player, state);
		player.sendMessage("§eEnter the offensive coalition code staff gave you in chat:");
		player.sendMessage("§7Staff mint one in Discord. Type §fcancel §7to back out.");
		new BukkitRunnable() {
			@Override
			public void run() {
				if (!state.submitted.get() && pending.remove(player, state) && player.isOnline()) {
					player.sendMessage("§cCode entry timed out.");
				}
			}
		}.runTaskLater(SimpleFactions.getInstance(), PROMPT_TIMEOUT_TICKS);
	}

	// Retain Bukkit chat-event ordering and String message semantics for existing integrations.
	@SuppressWarnings("deprecation")
	@EventHandler
	public void onPlayerChat(AsyncPlayerChatEvent event) {
		Player player = event.getPlayer();
		Pending state = pending.get(player);
		if (state == null || !state.submitted.compareAndSet(false, true)) {
			return;
		}
		event.setCancelled(true);
		pending.remove(player, state);
		String code = event.getMessage().trim();
		if (code.isEmpty() || code.equalsIgnoreCase("cancel")) {
			player.sendMessage("§7Cancelled. The coalition stays defensive.");
			return;
		}
		player.sendMessage("§7Checking your code...");
		SimpleFactions plugin = SimpleFactions.getInstance();
		Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
			// Check the kind before spending: a war code typed here must stay usable.
			WarDeclareCodeService.Result checked = WarDeclareCodeService.validate(
					code, state.leaderId, state.targetId, WarDeclareCodeService.Kind.COALITION_OFFENSIVE);
			WarDeclareCodeService.Result result = checked.ok
					? WarDeclareCodeService.redeemCoalition(code, state.leaderId, state.targetId)
					: checked;
			Bukkit.getScheduler().runTask(plugin, () -> apply(player, state, result));
		});
	}

	static void apply(Player player, Pending state, WarDeclareCodeService.Result result) {
		if (!result.ok) {
			if (player.isOnline()) player.sendMessage(result.error);
			return;
		}
		Coalition coalition = CoalitionRegistry.get(state.coalitionId);
		if (coalition == null || !coalition.isLeader(state.leaderId) || !coalition.isTarget(state.targetId)) {
			// The code is spent, so this is for staff to sort out.
			LogManager.war("COALITION_CODE_UNUSED coalition=%s leader=%s target=%s",
					state.coalitionId, state.leaderId, state.targetId);
			if (player.isOnline()) {
				player.sendMessage("§cThe code was used, but the coalition changed meanwhile. Ask staff for help.");
			}
			return;
		}
		LogManager.war("COALITION_CODE coalition=%s leader=%s target=%s",
				state.coalitionId, state.leaderId, state.targetId);
		CoalitionService.setOffensive(coalition, true);
	}

	@EventHandler
	public void onPlayerQuit(PlayerQuitEvent event) {
		pending.remove(event.getPlayer());
	}
}
