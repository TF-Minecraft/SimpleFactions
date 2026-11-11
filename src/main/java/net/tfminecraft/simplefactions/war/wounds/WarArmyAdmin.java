package net.tfminecraft.simplefactions.war.wounds;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import net.tfminecraft.simplefactions.army.Regiment;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.WarManager;
import net.tfminecraft.simplefactions.mercenary.company.MercenaryCompany;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.war.campaign.runtime.CampaignClock;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.war.core.WarCommandHelper;

/**
 * Staff tools for battle losses, under {@code /war admin}:
 * <ul>
 * <li>{@code losses <war>}: every recorded battle and what the war's end will take;</li>
 * <li>{@code losses <war> <battle#> <attacker|defender> set|add|remove <deaths> [reason]}: fixes a
 * battle's deaths, and its wounds follow;</li>
 * <li>{@code army <faction>}: each regiment's slots, wounds per war and what can fight;</li>
 * <li>{@code wounds <war> <faction> <regiment> add|remove <lives> [reason]}: wounds directly;</li>
 * <li>{@code wounds company <guild> [set <lives>]}: a mercenary company's wounds.</li>
 * </ul>
 */
public final class WarArmyAdmin {
	public static final String LOSSES_USAGE =
			"§cUsage: /war admin losses <war> [<battle#> attacker|defender set|add|remove <deaths> [reason]]";
	public static final String ARMY_USAGE = "§cUsage: /war admin army <faction>";
	public static final String WOUNDS_USAGE = "§cUsage: /war admin wounds <war> <faction> <regiment> add|remove <lives> [reason]"
			+ " or /war admin wounds company <guild> [set <lives>]";

	private WarArmyAdmin() {}

	/** {@code args} start at {@code admin}. */
	public static List<String> losses(String[] args, String by) {
		if (args.length < 3) {
			return List.of(LOSSES_USAGE);
		}
		War war = war(args[2]);
		if (war == null) {
			return List.of("§cNo active war with id " + args[2]);
		}
		if (args.length == 3) {
			return ledgerLines(war);
		}
		if (args.length < 7) {
			return List.of(LOSSES_USAGE);
		}
		Integer number = parse(args[3]);
		String side = args[4].toLowerCase(Locale.ROOT);
		Integer amount = parse(args[6]);
		if (number == null || amount == null || !(side.equals(WarLedger.ATTACKER) || side.equals(WarLedger.DEFENDER))) {
			return List.of(LOSSES_USAGE);
		}
		return WarWounds.editDeaths(war, number, side, args[5].toLowerCase(Locale.ROOT), amount, by, reason(args, 7))
				.lines();
	}

	public static List<String> army(String[] args) {
		if (args.length != 3) {
			return List.of(ARMY_USAGE);
		}
		Faction faction = FactionManager.getByString(args[2]);
		if (faction == null || faction.getMilitary() == null) {
			return List.of("§cUnknown faction: " + args[2]);
		}
		List<String> lines = new ArrayList<>();
		lines.add("§6" + faction.getName() + " §7(" + faction.getId() + ") army");
		List<Regiment> soldiers = faction.getMilitary().getRegiments().stream()
				.filter(regiment -> !regiment.isLevy() && !regiment.isEquipment() && !regiment.isMercenary())
				.toList();
		for (Regiment regiment : soldiers) {
			int law = Math.min(Math.max(0, regiment.getFreeSlots()), regiment.getCurrentSlots());
			lines.add("§e" + regiment.getId() + "§7: " + regiment.getCurrentSlots() + " slots (" + law + " law, "
					+ regiment.getPaidSlots() + " paid), " + WarWounds.woundedSlots(faction, regiment)
					+ " wounded, §a" + WarWounds.availableSlots(faction, regiment) + " can fight");
			for (War war : WarManager.getActive()) {
				int lives = war.getLedger().isSettled() ? 0
						: war.getLedger().factionWoundLives(faction.getId(), regiment.getId());
				if (lives > 0) {
					lines.add("§8  war " + war.getId() + ": " + lives + " wounded lives");
				}
			}
		}
		Instant now = CampaignClock.now();
		for (Guild guild : faction.getGuildHandler().getGuilds()) {
			MercenaryCompany company = guild.getCompany();
			if (company != null) {
				lines.add(companyLine(company, now));
			}
		}
		return lines;
	}

	public static List<String> wounds(String[] args, String by) {
		if (args.length >= 4 && args[2].equalsIgnoreCase("company")) {
			MercenaryCompany company = WarWounds.company(args[3]);
			if (company == null) {
				return List.of("§cNo mercenary company in guild " + args[3]);
			}
			if (args.length == 4) {
				return List.of(companyLine(company, CampaignClock.now()));
			}
			Integer lives = args.length == 6 && args[4].equalsIgnoreCase("set") ? parse(args[5]) : null;
			if (lives == null || lives < 0) {
				return List.of(WOUNDS_USAGE);
			}
			return WarWounds.setCompanyWounds(company, lives, by).lines();
		}
		if (args.length < 7) {
			return List.of(WOUNDS_USAGE);
		}
		War war = war(args[2]);
		if (war == null) {
			return List.of("§cNo active war with id " + args[2]);
		}
		Faction faction = FactionManager.getByString(args[3]);
		Regiment regiment = faction == null || faction.getMilitary() == null ? null
				: faction.getMilitary().getRegiment(args[4]);
		if (regiment == null || regiment.isLevy() || regiment.isEquipment()) {
			return List.of("§cUnknown faction regiment: " + args[3] + " " + args[4]);
		}
		String operation = args[5].toLowerCase(Locale.ROOT);
		Integer lives = parse(args[6]);
		if (lives == null || lives < 0 || !(operation.equals("add") || operation.equals("remove"))) {
			return List.of(WOUNDS_USAGE);
		}
		return WarWounds.adjust(war, faction, regiment, operation.equals("add") ? lives : -lives, by,
				reason(args, 7)).lines();
	}

	static List<String> ledgerLines(War war) {
		WarLedger ledger = war.getLedger();
		List<String> lines = new ArrayList<>();
		lines.add("§6War " + war.getId() + ": " + ledger.battles().size() + " recorded battles");
		for (WarLedger.BattleEntry entry : ledger.battles()) {
			lines.add("§e#" + entry.number() + " §7" + entry.resolution() + ", province " + entry.provinceId()
					+ ": attacker " + sideText(entry.attacker()) + "; defender " + sideText(entry.defender()));
		}
		for (WarLedger.Adjustment adjustment : ledger.adjustments()) {
			lines.add("§8Adjusted " + adjustment.factionId() + " " + adjustment.regimentId() + " by "
					+ adjustment.lives() + " lives (" + adjustment.by() + ")");
		}
		List<String> preview = WarWounds.settlementPreview(war);
		lines.add(preview.isEmpty() ? "§7At war end: no regiment slots are lost." : "§6At war end:");
		for (String line : preview) {
			lines.add("§7- " + line);
		}
		return lines;
	}

	private static String sideText(WarLedger.SideRecord side) {
		int mercenary = 0;
		for (BattleForce force : side.forces()) {
			if (force.kind() == ForceKind.MERCENARY) {
				mercenary += force.lives();
			}
		}
		return side.deaths() + " deaths of " + BattleForce.lives(side.forces()) + " lives"
				+ (mercenary > 0 ? " (" + mercenary + " mercenary)" : "");
	}

	private static String companyLine(MercenaryCompany company, Instant now) {
		int slots = company.getSlots();
		return "§bCompany " + company.getName() + " §7(guild " + company.getGuild().getId() + "): "
				+ company.getWounds().lives(now, slots) + " wounded lives, "
				+ company.getWounds().woundedSlots(now, slots) + " of " + slots + " slots wounded";
	}

	private static War war(String id) {
		War war = WarCommandHelper.parseWarId(id).map(WarManager::getById).orElse(null);
		return war != null && war.isActive() ? war : null;
	}

	private static Integer parse(String value) {
		try {
			return Integer.parseInt(value);
		} catch (NumberFormatException invalid) {
			return null;
		}
	}

	private static String reason(String[] args, int from) {
		return args.length > from ? String.join(" ", Arrays.copyOfRange(args, from, args.length)) : null;
	}
}
