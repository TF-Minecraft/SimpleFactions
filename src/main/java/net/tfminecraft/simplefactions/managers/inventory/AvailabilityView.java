package net.tfminecraft.simplefactions.managers.inventory;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.managers.InventoryManager;
import net.tfminecraft.simplefactions.managers.WarManager;
import net.tfminecraft.simplefactions.managers.holder.AvailabilityHolder;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.war.campaign.availability.AvailabilityCalendar;
import net.tfminecraft.simplefactions.war.campaign.availability.AvailabilityCalendar.DateState;
import net.tfminecraft.simplefactions.war.campaign.availability.AvailabilityMarks;
import net.tfminecraft.simplefactions.war.campaign.availability.AvailabilityScheduler;
import net.tfminecraft.simplefactions.war.campaign.availability.AvailabilityTally;
import net.tfminecraft.simplefactions.war.campaign.availability.AvailabilityTally.HourCount;
import net.tfminecraft.simplefactions.war.campaign.availability.BattleAvailability;
import net.tfminecraft.simplefactions.war.campaign.availability.LockedBattleDay;
import net.tfminecraft.simplefactions.war.campaign.progression.BelligerentRole;
import net.tfminecraft.simplefactions.war.campaign.runtime.BattleScheduleLookups;
import net.tfminecraft.simplefactions.war.campaign.runtime.BattleWindowService;
import net.tfminecraft.simplefactions.war.campaign.runtime.CampaignClock;
import net.tfminecraft.simplefactions.war.campaign.ui.CampaignUiCopy;
import net.tfminecraft.simplefactions.war.campaign.ui.CampaignUiTimeFormatter;
import net.tfminecraft.simplefactions.war.campaign.vote.BattleVoteReminders;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.war.history.WarHistoryEvents;
import net.tfminecraft.tlibs.objects.api.subapi.StringFormatter;

/**
 * "When can you fight?": one column per date, one row per start hour. The top row holds the dates
 * (a click marks or clears the whole date), the left column the start times, the right column the
 * player's status, how it works, "none of these days work", the website link and the way back.
 */
public class AvailabilityView {
	public static final int DATE_COLUMNS = 7;
	public static final int HOUR_ROWS = 5;
	static final int STATUS_SLOT = 8;
	static final int HELP_SLOT = 17;
	static final int NONE_SLOT = 26;
	static final int WEBSITE_SLOT = 35;
	static final int BACK_SLOT = 53;

	public InventoryManager inv;

	public AvailabilityView(InventoryManager inv) {
		this.inv = inv;
	}

	/** Opens the grid for a player who marks in this war, or says why not. Returns whether it opened. */
	public boolean open(Player player, War war) {
		if (war == null || !AvailabilityScheduler.isScheduling(war)) {
			player.sendMessage("§c" + AvailabilityMarks.NO_SCHEDULE);
			return false;
		}
		if (BattleScheduleLookups.voterFaction(war, player) == null) {
			player.sendMessage("§c" + AvailabilityMarks.NOT_ELIGIBLE);
			return false;
		}
		Instant now = CampaignClock.now();
		List<LocalDate> dates = AvailabilityCalendar.visibleDates(war, now);
		Inventory inventory = SimpleFactions.plugin.getServer().createInventory(
				new AvailabilityHolder(war.getId(), dates), 54, war.getName() + " §7When can you fight?");
		populate(inventory, war, dates, player.getUniqueId(), now);
		player.openInventory(inventory);
		return true;
	}

	// Keep the existing legacy text representation, formatting, and exact-string comparisons.
	@SuppressWarnings("deprecation")
	void populate(Inventory inventory, War war, List<LocalDate> dates, UUID account, Instant now) {
		inventory.clear();
		List<Integer> hours = BattleWindowService.listValidHours();
		LocalDate first = dates.get(0);
		inventory.setItem(0, item(Material.CLOCK, CampaignUiCopy.VALUE + "Start times",
				List.of(CampaignUiCopy.LABEL + "Each row is a start time, each column a day.")));
		for (int row = 1; row <= HOUR_ROWS; row++) {
			inventory.setItem(row * 9, row - 1 < hours.size()
					// The schedule's own clock is the same on every date; each cell gives its date's other time zones.
					? item(Material.CLOCK, CampaignUiCopy.VALUE + BattleWindowService.formatHourLabel(hours.get(row - 1)),
							List.of())
					: inv.getFiller(Material.GRAY_STAINED_GLASS_PANE));
		}
		var sides = AvailabilityTally.sides(war);
		for (int column = 1; column <= DATE_COLUMNS; column++) {
			if (column - 1 >= dates.size()) {
				for (int row = 0; row <= HOUR_ROWS; row++) {
					inventory.setItem(row * 9 + column, inv.getFiller(Material.GRAY_STAINED_GLASS_PANE));
				}
				continue;
			}
			LocalDate date = dates.get(column - 1);
			boolean editable = AvailabilityCalendar.editable(war, date, now);
			inventory.setItem(column, header(war, date, account, editable));
			List<HourCount> counts = AvailabilityTally.count(war, date, sides);
			for (int row = 1; row <= HOUR_ROWS; row++) {
				inventory.setItem(row * 9 + column, row - 1 < counts.size()
						? cell(war, date, counts.get(row - 1), account, editable)
						: inv.getFiller(Material.GRAY_STAINED_GLASS_PANE));
			}
		}
		inventory.setItem(STATUS_SLOT, status(war, dates, account, now));
		inventory.setItem(HELP_SLOT, help(war));
		inventory.setItem(NONE_SLOT, none(war, account, first));
		inventory.setItem(WEBSITE_SLOT, BattleVoteReminders.voteUrl(war) != null
				? item(Material.COMPASS, "§bVote on the website", List.of(CampaignUiCopy.LABEL + "Click to get the link in chat."))
				: inv.getFiller(Material.GRAY_STAINED_GLASS_PANE));
		inventory.setItem(44, inv.getFiller(Material.GRAY_STAINED_GLASS_PANE));
		inventory.setItem(BACK_SLOT, item(Material.ARROW, "§cBack", List.of(CampaignUiCopy.LABEL + "Back to the campaign.")));
	}

	private ItemStack header(War war, LocalDate date, UUID account, boolean editable) {
		DateState state = AvailabilityCalendar.state(war, date);
		String label = AvailabilityCalendar.label(date);
		List<String> lore = new ArrayList<>();
		String name;
		Material material;
		if (state == DateState.BATTLE) {
			material = Material.GOLD_INGOT;
			name = CampaignUiCopy.STATUS_HIGHLIGHT + "⚔ " + label + ": battle day";
			lore.add(CampaignUiCopy.LABEL + "Start time decided " + CampaignUiTimeFormatter.formatDateTime(
					AvailabilityCalendar.timeLockAt(date)));
		} else if (state == DateState.NO_BATTLE) {
			material = Material.GRAY_DYE;
			name = CampaignUiCopy.MUTED + label + ": no battle";
			lore.add(CampaignUiCopy.LABEL + noBattleText(war.getAvailability().noBattleReason(date)));
		} else {
			material = Material.PAPER;
			name = CampaignUiCopy.VALUE + label;
			lore.add(CampaignUiCopy.LABEL + "Battle or not is decided "
					+ CampaignUiTimeFormatter.formatDateTime(AvailabilityCalendar.dayLockAt(date)));
		}
		if (editable) {
			boolean full = war.getAvailability().marksOn(account, date).containsAll(BattleWindowService.listValidHours());
			lore.add(full ? CampaignUiCopy.REMOVE + "Click: clear this day" : CampaignUiCopy.SELECT + "Click: mark every time");
		} else {
			lore.add(CampaignUiCopy.MUTED + "Decided. It can't change.");
		}
		return item(material, name, lore);
	}

	private ItemStack cell(War war, LocalDate date, HourCount count, UUID account, boolean editable) {
		boolean marked = war.getAvailability().marksOn(account, date).contains(count.hour());
		Material material = editable
				? (marked ? Material.LIME_STAINED_GLASS_PANE : Material.WHITE_STAINED_GLASS_PANE)
				: (marked ? Material.GREEN_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE);
		List<String> lore = new ArrayList<>();
		lore.add(CampaignUiCopy.VALUE + CampaignUiTimeFormatter.formatUtcHour(date, count.hour()));
		lore.add(CampaignUiCopy.LABEL + "Attackers " + CampaignUiCopy.VALUE + count.attackers()
				+ CampaignUiCopy.LABEL + " · Defenders " + CampaignUiCopy.VALUE + count.defenders());
		if (editable) {
			lore.add(marked ? CampaignUiCopy.REMOVE + "Click: I can't make it" : CampaignUiCopy.SELECT + "Click: I can fight then");
		}
		return item(material, (marked ? CampaignUiCopy.SELECT + "✔ " : CampaignUiCopy.LABEL)
				+ AvailabilityCalendar.label(date) + " " + BattleWindowService.formatHourLabel(count.hour()), lore);
	}

	private ItemStack status(War war, List<LocalDate> dates, UUID account, Instant now) {
		BattleAvailability availability = war.getAvailability();
		boolean any = dates.stream().anyMatch(date -> availability.hasMarksOn(account, date));
		String name = any ? CampaignUiCopy.SELECT + "✔ You marked when you can fight"
				: AvailabilityMarks.saidNone(war, account, dates.get(0)) ? CampaignUiCopy.WARNING + "You said none of these days work"
				: CampaignUiCopy.REMOVE + "✘ You haven't marked anything";
		List<String> lore = new ArrayList<>();
		LockedBattleDay next = availability.head();
		if (next == null) {
			lore.add(CampaignUiCopy.LABEL + "No battle day yet.");
		} else if (next.hour() == null) {
			lore.add(CampaignUiCopy.STATUS_HIGHLIGHT + "⚔ Next battle: " + AvailabilityCalendar.label(next.date())
					+ CampaignUiCopy.LABEL + ", start time decided "
					+ CampaignUiTimeFormatter.formatDateTime(AvailabilityCalendar.timeLockAt(next.date())));
		} else {
			lore.add(CampaignUiCopy.STATUS_HIGHLIGHT + "⚔ Next battle: " + AvailabilityCalendar.label(next.date()) + ", "
					+ CampaignUiTimeFormatter.formatUtcHour(next.date(), next.hour()));
		}
		Instant lock = AvailabilityCalendar.nextLockAt(war, now);
		if (lock != null) {
			lore.add(CampaignUiCopy.LABEL + "Next decision: " + CampaignUiCopy.VALUE + CampaignUiTimeFormatter.formatDateTime(lock));
		}
		return item(Material.NETHER_STAR, name, lore);
	}

	private ItemStack help(War war) {
		int attackers = AvailabilityTally.need(war, BelligerentRole.ATTACKER);
		int defenders = AvailabilityTally.need(war, BelligerentRole.DEFENDER);
		List<String> lore = new ArrayList<>();
		lore.add(CampaignUiCopy.VALUE + "Mark every day and time you could fight.");
		lore.add(CampaignUiCopy.VALUE + "A day becomes a battle when " + needText(attackers, defenders)
				+ " can come at the same time.");
		lore.add(CampaignUiCopy.VALUE + "It's decided " + hours(Cache.warAvailabilityDayLockHours)
				+ " before. The time is decided " + hours(Cache.warAvailabilityTimeLockHours) + " before.");
		if (Cache.warAvailabilityMaxDaysWithoutBattle > 0) {
			lore.add(CampaignUiCopy.VALUE + "After " + Cache.warAvailabilityMaxDaysWithoutBattle
					+ " days without a battle, the last one is a battle anyway.");
		}
		lore.add(CampaignUiCopy.LABEL + "Green: you can fight then. Click a day at the top to mark all of it.");
		return item(Material.BOOK, CampaignUiCopy.VALUE + "How it works", lore);
	}

	private ItemStack none(War war, UUID account, LocalDate first) {
		if (AvailabilityMarks.saidNone(war, account, first)) {
			return item(Material.BARRIER, CampaignUiCopy.WARNING + "You said none of these days work",
					List.of(CampaignUiCopy.LABEL + "Click to take it back."));
		}
		return item(Material.BARRIER, CampaignUiCopy.REMOVE + "None of these days work for me",
				List.of(CampaignUiCopy.LABEL + "Clears your marks and stops the reminders."));
	}

	/** Why a decided date has no battle, as the date's header says it. */
	static String noBattleText(BattleAvailability.NoBattleReason reason) {
		if (reason == null) {
			return "Not enough people could come at the same time.";
		}
		return switch (reason) {
			case QUEUE_FULL -> "Battles were already planned.";
			case TOO_LATE -> "It was decided too late to plan a battle.";
		};
	}

	/** {@code 3 of each side}, or {@code 3 attackers and 1 defender}. */
	static String needText(int attackers, int defenders) {
		if (attackers == defenders) {
			return attackers + " of each side";
		}
		return attackers + (attackers == 1 ? " attacker" : " attackers") + " and " + defenders
				+ (defenders == 1 ? " defender" : " defenders");
	}

	/** {@code 2 days}, {@code 1 day} or {@code 36 hours}. */
	static String hours(int hours) {
		if (hours % 24 != 0) {
			return hours + " hours";
		}
		int days = hours / 24;
		return days == 1 ? "1 day" : days + " days";
	}

	// Keep the existing legacy text representation, formatting, and exact-string comparisons.
	@SuppressWarnings("deprecation")
	private static ItemStack item(Material material, String name, List<String> lore) {
		ItemStack item = new ItemStack(material, 1);
		ItemMeta meta = item.getItemMeta();
		meta.setDisplayName(StringFormatter.formatHex(name));
		List<String> lines = new ArrayList<>();
		for (String line : lore) {
			lines.add(StringFormatter.formatHex(line));
		}
		meta.setLore(lines);
		item.setItemMeta(meta);
		return item;
	}

	// Keep the existing legacy text representation, formatting, and exact-string comparisons.
	@SuppressWarnings("deprecation")
	public void click(InventoryClickEvent e, Inventory inventory, Player player) {
		if (!(inventory.getHolder(false) instanceof AvailabilityHolder holder)
				|| !InventoryManager.isTopInventoryClick(e.getRawSlot(), inventory.getSize())) {
			return;
		}
		e.setCancelled(true);
		War war = WarManager.getById(holder.getWarId());
		if (war == null || !war.isActive()) {
			player.sendMessage("§cWar not found.");
			player.closeInventory();
			return;
		}
		int slot = e.getRawSlot();
		if (slot == BACK_SLOT) {
			inv.openCampaignView(player, war);
			return;
		}
		if (slot == WEBSITE_SLOT) {
			String url = BattleVoteReminders.voteUrl(war);
			if (url != null) {
				player.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§b§l[Vote on the website] §7" + url)
						.clickEvent(ClickEvent.openUrl(url)));
			}
			return;
		}
		Faction faction = BattleScheduleLookups.voterFaction(war, player);
		UUID account = player.getUniqueId();
		Instant now = CampaignClock.now();
		String refusal;
		if (slot == NONE_SLOT) {
			if (AvailabilityMarks.saidNone(war, account, holder.getDates().get(0))) {
				AvailabilityMarks.clearNone(war, account);
				refusal = null;
			} else {
				refusal = AvailabilityMarks.setNone(war, account, faction, now);
			}
		} else {
			int row = slot / 9;
			int column = slot % 9;
			if (column < 1 || column > DATE_COLUMNS || column - 1 >= holder.getDates().size()) {
				return;
			}
			LocalDate date = holder.getDates().get(column - 1);
			List<Integer> hours = BattleWindowService.listValidHours();
			if (row == 0) {
				refusal = AvailabilityMarks.toggleDate(war, account, faction, date, now);
			} else if (row - 1 < Math.min(HOUR_ROWS, hours.size())) {
				refusal = AvailabilityMarks.toggleHour(war, account, faction, date, hours.get(row - 1), now);
			} else {
				return;
			}
		}
		if (refusal != null) {
			player.sendMessage("§c" + refusal);
			refresh(inventory, holder, war, account, now);
			return;
		}
		WarManager.persist(war);
		WarHistoryEvents.votesChanged(war);
		player.playSound(player, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
		refresh(inventory, holder, war, account, now);
	}

	/** Redraws in place with the dates as they are now. */
	private void refresh(Inventory inventory, AvailabilityHolder holder, War war, UUID account, Instant now) {
		List<LocalDate> dates = AvailabilityCalendar.visibleDates(war, now);
		holder.setDates(dates);
		populate(inventory, war, dates, account, now);
	}
}
