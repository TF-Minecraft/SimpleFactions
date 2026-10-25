package net.tfminecraft.simplefactions.admin;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import net.tfminecraft.simplefactions.economy.TreasuryActions;
import net.tfminecraft.simplefactions.economy.TreasuryTopology;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.guild.MemberDepartures;
import net.tfminecraft.simplefactions.identity.Members;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.objects.Bank;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.settlement.Settlement;
import net.tfminecraft.simplefactions.settlement.handler.CapitalResult;
import net.tfminecraft.simplefactions.settlement.handler.SettlementHandler;
import net.tfminecraft.simplefactions.utils.Formatter;
import net.tfminecraft.simplefactions.utils.RandomRGB;
import net.tfminecraft.tlibs.objects.api.subapi.StringFormatter;

/**
 * Staff versions of the leader-only faction and guild commands. Each skips the
 * leader check and any price, and nothing else: the same naming, colour and
 * membership rules apply. Each returns the lines to show the staff member.
 */
public final class StaffAdminService {
	/** Faction fields staff set with {@code /faction admin set}. */
	public static final List<String> FIELDS = List.of("culture", "religion", "rulingsystem", "rulertitle");
	static final String NEEDS_CHARACTER = "§cPlay a character to move a capital: the treasury records who made the change.";

	private StaffAdminService() {}

	/**
	 * A move checked by {@link #previewCapital}, applied by {@link #moveCapital} once staff confirm.
	 * It holds the faction itself, so a faction deleted and refounded under the same id is not touched.
	 */
	public record CapitalMove(Faction faction, int fromCapital, int province, String name) {}

	/** Display name as the player commands store it: {@code _} for spaces, default colour, hex codes. */
	static String displayName(String input) {
		return StringFormatter.formatHex(Formatter.formatName(input));
	}

	static boolean validName(String input) {
		return !Formatter.formatId(input).isBlank();
	}

	/** Why {@code rgb} cannot be used, or null. {@code current} is the colour it replaces. */
	static String colourProblem(String rgb, String current) {
		return switch (FactionManager.validateRGB(rgb)) {
			case 1 -> "§cInvalid format. Use: R,G,B (e.g. 255,0,0)";
			case 2 -> "§cRGB values must be numbers (e.g. 128,128,128)";
			case 3 -> "§cEach RGB value must be between 0 and 255";
			case 4 -> "§c0,0,0 is reserved for unclaimed land; pick another colour";
			default -> !RandomRGB.sameRGB(rgb, current) && !RandomRGB.isFree(rgb) ? "§cThat colour is already used" : null;
		};
	}

	static boolean isBanner(ItemStack item) {
		// Not *_BANNER_PATTERN, which has no banner meta.
		return item != null && item.getType().toString().endsWith("_BANNER");
	}

	// ---- Faction ----

	public static List<String> info(Faction faction) {
		List<String> lines = new ArrayList<>();
		lines.add("§6=== §f" + faction.getName() + " §7(" + faction.getId() + ") §6===");
		lines.add("§7Leader: §f" + character(faction.getLeader()));
		lines.add("§7Members: §f" + faction.getMembers().size() + " §7Provinces: §f" + faction.getProvinces().size()
				+ " §7Colour: §f" + faction.getRGB());
		lines.add("§7Culture: §f" + orNone(faction.getCulture()) + " §7Religion: §f" + orNone(faction.getReligion())
				+ " §7Ruling system: §f" + orNone(faction.getGovernmentString())
				+ " §7Ruler title: §f" + orNone(faction.getRulerTitle()));
		Settlement capital = faction.hasCapital() ? faction.getSettlementHandler().getByProvince(faction.getCapital()) : null;
		lines.add("§7Capital: §f" + (!faction.hasCapital() ? "none"
				: "province " + faction.getCapital() + (capital == null ? "" : ", " + settlement(capital)))
				+ " §7Capital changes made: §f" + faction.getCapitalMoves());
		for (Settlement other : faction.getSettlementHandler().getAll()) {
			if (other != capital) lines.add("§7Settlement: §f" + settlement(other) + " §7in province §f" + other.getCenterProvince());
		}
		for (Guild guild : faction.getGuildHandler().getGuilds()) {
			if (guild.isBase()) continue;
			lines.add("§7Guild: §f" + guild.getName() + " §7(" + guild.getId() + ") led by §f" + character(guild.getLeader())
					+ " §7with §f" + guild.getMembers().size() + " §7members");
		}
		Bank bank = faction.getBank();
		lines.add("§7Bank: §f" + (bank == null ? "none" : Formatter.formatMoney(bank.getWealth()) + "d"));
		return lines;
	}

	private static String character(String key) {
		return key == null || key.isEmpty() ? "nobody" : Members.display(key) + " §7(" + key + ")§f";
	}

	private static String orNone(String value) {
		return value == null || value.isBlank() ? "none" : value;
	}

	private static String settlement(Settlement settlement) {
		return settlement.getName() + " §7(" + settlement.getId() + ")§f";
	}

	/** Where a character belongs, searched by name or character ID across every realm. */
	public static List<String> who(String input) {
		List<String> lines = new ArrayList<>();
		for (Faction faction : FactionManager.getCopy()) {
			String key = Members.find(faction.getMembers(), input);
			if (key == null) continue;
			StringBuilder line = new StringBuilder("§f" + character(key) + " §7is in §f" + faction.getName()
					+ " §7(" + faction.getId() + ")");
			if (key.equals(faction.getLeader())) line.append(" §7and leads it");
			for (Guild guild : faction.getGuildHandler().getGuilds()) {
				if (guild.isBase() || !guild.isMember(key)) continue;
				line.append("§7, guild §f").append(guild.getName()).append(" §7(").append(guild.getId()).append(")");
				if (guild.isLeader(key)) line.append(" §7as its leader");
			}
			lines.add(line.toString());
		}
		if (lines.isEmpty()) lines.add("§cNo faction member matches " + input);
		return lines;
	}

	public static List<String> rename(Faction faction, String input) {
		if (!validName(input)) return List.of("§cInvalid faction name");
		String old = faction.getName();
		faction.setName(displayName(input));
		FactionManager.getMap().enqueue("nation", faction.getRGB());
		return List.of("§aRenamed faction §f" + old + " §ato §f" + faction.getName());
	}

	/** {@code which} is a settlement id, or {@code capital} for the faction capital. */
	public static List<String> renameSettlement(Faction faction, String which, String input) {
		SettlementHandler settlements = faction.getSettlementHandler();
		Settlement settlement = null;
		if (which.equalsIgnoreCase("capital")) {
			if (faction.hasCapital()) settlement = settlements.getByProvince(faction.getCapital());
		} else {
			// Ids keep the founder's capitals, so staff need not match them.
			String id = Formatter.formatId(which);
			for (Settlement each : settlements.getAll()) {
				if (each.getId().equalsIgnoreCase(id)) settlement = each;
			}
		}
		if (settlement == null) {
			return List.of("§c" + faction.getName() + " §chas no settlement " + which
					+ "§c. §e/faction admin info " + faction.getId() + " §clists them.");
		}
		CapitalResult result = settlements.rename(settlement.getCenterProvince(), input, false);
		return List.of(result.getMessage());
	}

	/**
	 * Checks moving {@code faction}'s capital to {@code province}, where the staff
	 * member stands. Returns the move to confirm, or null with the refusal in {@code lines}.
	 */
	public static CapitalMove previewCapital(Player staff, Faction faction, int province, String name, List<String> lines) {
		if (province == -2) {
			lines.add("§a[SimpleFactions] §cError! could not resolve province");
			return null;
		}
		if (province == 0) {
			lines.add("§cThis location has no province!");
			return null;
		}
		if (!faction.ownsProvince(province)) {
			lines.add("§c" + faction.getName() + " §cdoes not own province " + province
					+ "§c. Give it to them with §e/faction forceclaim " + faction.getId() + " §cfirst.");
			return null;
		}
		SettlementHandler settlements = faction.getSettlementHandler();
		Settlement existing = settlements.getByProvince(province);
		boolean inCapital = faction.hasCapital() && faction.getCapital() == province;
		if (inCapital && existing != null) {
			lines.add("§cProvince " + province + " §cis already the capital of " + faction.getName()
					+ "§c. Rename it with §e/faction admin renamesettlement " + faction.getId() + " capital <name>");
			return null;
		}
		// Only a real move goes through the treasury, which needs the character making it.
		if (faction.hasCapital() && !inCapital && Members.session(staff).isEmpty()) {
			lines.add(NEEDS_CHARACTER);
			return null;
		}
		// Also refuses a missing or invalid name when a new settlement would be founded.
		CapitalResult check = settlements.validateFactionCapital(staff, province, name);
		if (!check.isSuccess()) {
			lines.add(check.getMessage());
			return null;
		}
		if (inCapital) {
			// A capital whose settlement is gone: found it again in place, as /faction setcapital <name> does.
			lines.add("§eFound " + displayName(name) + " §ein the capital of " + faction.getName() + " §e(province " + province + ")");
		} else {
			lines.add("§eMove the capital of " + faction.getName() + " §efrom "
					+ (faction.hasCapital() ? "province " + faction.getCapital() : "nowhere") + " §eto province " + province
					+ (existing != null ? " §e(" + existing.getName() + "§e)" : " §e(new settlement " + displayName(name) + "§e)"));
		}
		List<Integer> lost = faction.hasCapital() && !inCapital
				? faction.getProvinceHandler().previewProvincesLostIfCapitalMoved(province) : List.of();
		if (!lost.isEmpty()) lines.add("§c" + faction.getName() + " §cwould lose provinces cut off from the new capital: " + lost);
		lines.add("§7It is free and does not use up their free capital change. Run §e/faction admin confirm §7to apply it.");
		return new CapitalMove(faction, faction.getCapital(), province, existing != null ? null : name);
	}

	/** Applies a confirmed move if nothing changed since the preview. */
	public static List<String> moveCapital(Player staff, CapitalMove move, int standing) {
		Faction faction = move.faction();
		if (FactionManager.getByString(faction.getId()) != faction) return List.of("§cThat faction no longer exists. Nothing changed.");
		if (faction.getCapital() != move.fromCapital()) return List.of("§cTheir capital changed since the preview. Nothing changed.");
		if (standing != move.province()) return List.of("§cStand in province " + move.province() + " §cto confirm. Nothing changed.");
		if (!faction.hasCapital() || faction.getCapital() == move.province()) {
			// A first capital, or a capital without its settlement, is founded in place.
			CapitalResult result = faction.getSettlementHandler().applyFactionCapital(staff, move.province(), move.name());
			if (!result.isSuccess()) return List.of(result.getMessage());
			faction.setCapital(move.province());
			faction.getProvinceHandler().revalidateClaims();
			return List.of(result.getMessage(), "§aSet the capital of " + faction.getName() + " §ato province " + move.province());
		}
		// The treasury journal records the character that made the change.
		if (Members.session(staff).isEmpty()) return List.of(NEEDS_CHARACTER);
		List<Integer> before = new ArrayList<>(faction.getProvinces());
		if (!TreasuryTopology.capital(staff, faction, move.province(), move.name(), false, 0, TreasuryActions.newId(), false)) {
			return List.of("§cThe capital move failed. Nothing changed.");
		}
		before.removeAll(faction.getProvinces());
		List<String> lines = new ArrayList<>();
		lines.add("§aMoved the capital of " + faction.getName() + " §ato province " + move.province());
		if (!before.isEmpty()) lines.add("§e" + faction.getName() + " §elost provinces cut off from their realm: " + before);
		return lines;
	}

	public static List<String> setColour(Faction faction, String rgb) {
		String old = faction.getRGB();
		String problem = colourProblem(rgb, old);
		if (problem != null) return List.of(problem);
		FactionManager.getMap().enqueue("nation", old);
		faction.setRGB(rgb);
		FactionManager.getMap().enqueue("nation", faction.getRGB());
		return List.of("§aColour of " + faction.getName() + " §aset to §f" + rgb);
	}

	public static List<String> setBanner(Faction faction, ItemStack held) {
		if (!isBanner(held)) return List.of("§cHold the new banner in your main hand");
		faction.setBanner(held);
		return List.of("§aBanner of " + faction.getName() + " §achanged");
	}

	public static List<String> set(Faction faction, String field, String input) {
		String value = input.replace("_", " ");
		if (value.startsWith("<") && value.endsWith(">")) return List.of("§cEnter a value instead of " + value + "§c.");
		switch (field.toLowerCase()) {
			case "culture" -> faction.setCulture(value);
			case "religion" -> faction.setReligion(value);
			case "rulingsystem" -> faction.setGovernment(value);
			case "rulertitle" -> faction.setRulerTitle(value);
			default -> {
				return List.of("§cUnknown field " + field + "§c. Use one of: " + String.join(", ", FIELDS));
			}
		}
		return List.of("§aSet " + field.toLowerCase() + " of " + faction.getName() + " §ato §f" + value);
	}

	/** Removes a character from the faction and any guild in it; leaders must be replaced first. */
	public static List<String> kick(Faction faction, String input) {
		String key = Members.find(faction.getMembers(), input);
		if (key == null) return List.of("§cNo member of " + faction.getName() + " §cmatches " + input);
		if (key.equals(faction.getLeader())) {
			return List.of("§c" + Members.display(key) + " §cleads " + faction.getName()
					+ "§c. Replace them with §e/faction forceleader §cfirst.");
		}
		for (Guild guild : faction.getGuildHandler().getGuilds()) {
			if (!guild.isBase() && guild.isLeader(key)) {
				return List.of("§c" + Members.display(key) + " §cleads the guild " + guild.getName()
						+ "§c. Replace them with §e/guild forceleader §cor delete the guild first.");
			}
		}
		MemberDepartures.leaveRealm(faction, key, false);
		Members.online(key).ifPresent(kicked -> kicked.sendMessage("§cStaff removed you from " + faction.getName()));
		return List.of("§aRemoved " + Members.display(key) + " §afrom " + faction.getName());
	}

	// ---- Guild ----

	static String baseGuild(Guild guild, String instead) {
		return guild.isBase() ? "§c" + guild.getId() + " §cis the main guild of " + guild.getFaction().getName()
				+ "§c; use §e/faction admin " + instead + " " + guild.getFaction().getId() + " §cinstead" : null;
	}

	public static List<String> guildInfo(Guild guild) {
		List<String> lines = new ArrayList<>();
		Faction faction = guild.getFaction();
		lines.add("§6=== §f" + guild.getName() + " §7(" + guild.getId() + ") §6===");
		lines.add("§7Faction: §f" + faction.getName() + " §7(" + faction.getId() + ")" + (guild.isBase() ? " §7- its main guild" : ""));
		lines.add("§7Leader: §f" + character(guild.getLeader()));
		List<String> names = new ArrayList<>();
		for (String member : guild.getMembers()) names.add(Members.display(member));
		lines.add("§7Members (" + names.size() + "): §f" + String.join(", ", names));
		Settlement seat = guild.hasCapital() ? faction.getSettlementHandler().getByProvince(guild.getCapital()) : null;
		lines.add("§7Seat: §f" + (!guild.hasCapital() ? "none"
				: "province " + guild.getCapital() + (seat == null ? "" : ", " + settlement(seat))) + " §7Colour: §f" + guild.getRGB());
		Bank bank = guild.getBank();
		lines.add("§7Bank: §f" + (bank == null ? "none" : Formatter.formatMoney(bank.getWealth()) + "d"));
		return lines;
	}

	public static List<String> guildRename(Guild guild, String input) {
		String base = baseGuild(guild, "rename");
		if (base != null) return List.of(base);
		if (!validName(input)) return List.of("§cInvalid guild name");
		String old = guild.getName();
		guild.setName(displayName(input));
		return List.of("§aRenamed guild §f" + old + " §ato §f" + guild.getName());
	}

	public static List<String> guildSetColour(Guild guild, String rgb) {
		String base = baseGuild(guild, "setcolour");
		if (base != null) return List.of(base);
		String problem = colourProblem(rgb, guild.getRGB());
		if (problem != null) return List.of(problem);
		guild.setRGB(rgb);
		return List.of("§aColour of " + guild.getName() + " §aset to §f" + rgb);
	}

	public static List<String> guildSetBanner(Guild guild, ItemStack held) {
		String base = baseGuild(guild, "setbanner");
		if (base != null) return List.of(base);
		if (!isBanner(held)) return List.of("§cHold the new banner in your main hand");
		guild.setBanner(held);
		return List.of("§aBanner of " + guild.getName() + " §achanged");
	}

	/** Sends a member back to the faction's main guild, as a guild leader's kick does. */
	public static List<String> guildKick(Guild guild, String input) {
		String base = baseGuild(guild, "kick");
		if (base != null) return List.of(base);
		String key = Members.find(guild.getMembers(), input);
		if (key == null) return List.of("§cNo member of " + guild.getName() + " §cmatches " + input);
		if (guild.isLeader(key)) {
			return List.of("§c" + Members.display(key) + " §cleads " + guild.getName()
					+ "§c. Replace them with §e/guild forceleader §cfirst.");
		}
		MemberDepartures.leaveGuild(guild, key);
		Members.online(key).ifPresent(kicked -> kicked.sendMessage("§cStaff removed you from " + guild.getName()
				+ "§c. You are back in the main guild of " + guild.getFaction().getName()));
		return List.of("§aMoved " + Members.display(key) + " §afrom " + guild.getName() + " §ato the main guild of "
				+ guild.getFaction().getName());
	}
}
