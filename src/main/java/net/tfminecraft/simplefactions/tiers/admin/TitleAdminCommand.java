package net.tfminecraft.simplefactions.tiers.admin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.loaders.TierLoader;
import net.tfminecraft.simplefactions.loaders.TitleLoader;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.TitleManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.tiers.Tier;
import net.tfminecraft.simplefactions.tiers.Title;
import net.tfminecraft.simplefactions.utils.Permissions;

/** {@code /faction title ...}: staff-only editing of title names, colours and de jure contents. */
public final class TitleAdminCommand {
	public static final String SUBCOMMAND = "title";
	public static final List<String> SUBCOMMANDS = List.of("list", "info", "where", "rename", "setcolour",
			"addprovince", "removeprovince", "addtitle", "removetitle", "setcomplete");
	private static final String[] USAGE = {
			"§6/faction title list [tier] §7- list titles",
			"§6/faction title info <title> §7- show a title's parts, parent and holder",
			"§6/faction title where <province> §7- show which titles a province belongs to",
			"§6/faction title rename <title> <name...> §7- change the display name",
			"§6/faction title setcolour <title> <R,G,B> §7- change the map colour",
			"§6/faction title addprovince <title> <province...> §7- add or move provinces into a title",
			"§6/faction title removeprovince <title> <province...> §7- leave provinces untitled",
			"§6/faction title addtitle <title> <lower title> §7- add or move a lower title into a title",
			"§6/faction title removetitle <title> <lower title> §7- take a lower title out",
			"§6/faction title setcomplete <title> <true|false> §7- require every part to form it",
	};

	private TitleAdminCommand() {
	}

	public static boolean handle(CommandSender sender, String[] args) {
		if (!Permissions.isAdmin(sender)) {
			sender.sendMessage("§a[SimpleFactions]§c You do not have access to this command");
			return true;
		}
		if (!Cache.requireProvinces(sender)) {
			return true;
		}
		if (args.length < 2) {
			sender.sendMessage(USAGE);
			return true;
		}
		String sub = args[1].toLowerCase();
		switch (sub) {
			case "list" -> list(sender, args);
			case "info" -> {
				if (args.length != 3) sender.sendMessage(usage("info"));
				else info(sender, args[2]);
			}
			case "where" -> {
				if (args.length != 3) sender.sendMessage(usage("where"));
				else where(sender, args[2]);
			}
			case "rename" -> {
				if (args.length < 4) sender.sendMessage(usage("rename"));
				else apply(sender, TitleAdminService.rename(args[2], String.join(" ", Arrays.copyOfRange(args, 3, args.length))));
			}
			case "setcolour", "setcolor" -> {
				if (args.length < 4) sender.sendMessage(usage("setcolour"));
				else apply(sender, TitleAdminService.setColour(args[2], String.join("", Arrays.copyOfRange(args, 3, args.length))));
			}
			case "addprovince" -> {
				if (args.length < 4) sender.sendMessage(usage("addprovince"));
				else apply(sender, TitleAdminService.addProvinces(args[2], Arrays.asList(args).subList(3, args.length),
						TitleAdminCommand::provinceExists));
			}
			case "removeprovince" -> {
				if (args.length < 4) sender.sendMessage(usage("removeprovince"));
				else apply(sender, TitleAdminService.removeProvinces(args[2], Arrays.asList(args).subList(3, args.length)));
			}
			case "addtitle" -> {
				if (args.length != 4) sender.sendMessage(usage("addtitle"));
				else apply(sender, TitleAdminService.addTitle(args[2], args[3]));
			}
			case "removetitle" -> {
				if (args.length != 4) sender.sendMessage(usage("removetitle"));
				else apply(sender, TitleAdminService.removeTitle(args[2], args[3]));
			}
			case "setcomplete" -> {
				if (args.length != 4) sender.sendMessage(usage("setcomplete"));
				else apply(sender, TitleAdminService.setComplete(args[2], args[3]));
			}
			default -> sender.sendMessage(USAGE);
		}
		return true;
	}

	private static String usage(String sub) {
		for (String line : USAGE) {
			if (line.startsWith("§6/faction title " + sub + " ")) return line;
		}
		return USAGE[0];
	}

	private static boolean provinceExists(int id) {
		return SimpleFactions.getInstance().getProvinceManager().contains(id);
	}

	private static void apply(CommandSender sender, TitleAdminService.Result result) {
		for (String line : result.lines()) {
			sender.sendMessage(line);
		}
		if (!result.ok()) return;

		for (Title title : result.changed()) {
			if (!TitleLoader.saveTitle(title)) {
				sender.sendMessage("§a[SimpleFactions]§c Could not save " + title.getId() + " to Input/"
						+ title.getTier().getId().toLowerCase() + ".json. The change is live but will be lost on restart; check the console.");
			}
		}
		for (TitleAdminService.MapKey key : result.regenerate()) {
			FactionManager.getMap().enqueue(key.tier(), key.rgb());
		}
		if (!result.regenerate().isEmpty()) {
			sender.sendMessage("§7The web map updates on its next cycle.");
		}
		// Holders keep their titles; staff decide whether to use destroytitle/granttitle.
		for (Title title : result.changed()) {
			Faction owner = TitleManager.getOwner(title);
			if (owner == null) continue;
			int held = title.getCurrentAmount(owner, TitleManager.getProvinces(owner), TitleManager.getTitles(owner));
			String colour = held > 0 ? "§7" : "§e";
			sender.sendMessage(colour + title.getName() + " is held by " + owner.getName() + colour + ", who controls §f"
					+ held + "/" + title.getNeededAmount() + colour + " of its " + (title.isComposite() ? "titles" : "provinces") + ".");
		}
		SimpleFactions.getInstance().getLogger().info("[TitleAdmin] " + sender.getName() + ": "
				+ ChatColor.stripColor(String.join(" | ", result.lines())));
	}

	private static void list(CommandSender sender, String[] args) {
		Tier only = null;
		if (args.length >= 3) {
			only = TierLoader.getByString(args[2]);
			if (only == null) {
				sender.sendMessage("§a[SimpleFactions]§c No tier with the id " + args[2]);
				return;
			}
		}
		for (Tier tier : TierLoader.get()) {
			if (only != null && !tier.getId().equalsIgnoreCase(only.getId())) continue;
			List<Title> titles = TitleLoader.getByTier(tier);
			if (titles.isEmpty()) continue;
			sender.sendMessage(tier.getName() + " §7(" + titles.size() + ")");
			for (Title title : titles) {
				Faction owner = TitleManager.getOwner(title);
				String parts = title.isComposite()
						? title.getTitles().size() + " titles"
						: title.getProvinces().size() + " provinces";
				sender.sendMessage(" §7" + title.getId() + " §f" + title.getName() + " §8(" + parts + ")"
						+ (owner != null ? " §7held by " + owner.getName() : ""));
			}
		}
	}

	private static void info(CommandSender sender, String id) {
		Title title = TitleLoader.getById(id);
		if (title == null) {
			sender.sendMessage("§a[SimpleFactions]§c No title with the id " + id);
			return;
		}
		sender.sendMessage("§6=== §f" + title.getName() + " §7(" + title.getId() + ") §6===");
		sender.sendMessage("§7Tier: " + title.getTier().getName() + " §7Colour: §f" + title.getRgb()
				+ " §7Title-complete: §f" + title.isTitleComplete());
		if (!title.getProvinces().isEmpty()) {
			sender.sendMessage("§7Provinces: §f" + title.getProvinces());
		}
		if (!title.getTitles().isEmpty()) {
			List<String> parts = new ArrayList<>();
			for (String childId : title.getTitles()) {
				Title child = TitleLoader.getById(childId);
				parts.add(child == null ? "§c" + childId + " (missing)§f" : childId + " (" + child.getName() + ")");
			}
			sender.sendMessage("§7Titles: §f" + String.join(", ", parts));
			sender.sendMessage("§7All provinces: §f" + TitleManager.getProvinces(title));
		}
		Title parent = TitleLoader.getByTitle(title);
		sender.sendMessage("§7Part of: §f" + (parent == null ? "none" : parent.getId() + " (" + parent.getName() + ")"));
		Faction owner = TitleManager.getOwner(title);
		sender.sendMessage("§7Holder: §f" + (owner == null ? "none" : owner.getName()));
	}

	private static void where(CommandSender sender, String raw) {
		int province;
		try {
			province = Integer.parseInt(raw);
		} catch (NumberFormatException e) {
			sender.sendMessage("§a[SimpleFactions]§c Province ids must be numbers");
			return;
		}
		if (!provinceExists(province)) {
			sender.sendMessage("§a[SimpleFactions]§c No province with the id " + province);
			return;
		}
		List<String> chain = new ArrayList<>();
		Set<Title> seen = new LinkedHashSet<>();
		for (Title t = TitleLoader.getByProvince(province); t != null && seen.add(t); t = TitleLoader.getByTitle(t)) {
			chain.add(t.getId() + " (" + t.getName() + ")");
		}
		Faction owner = FactionManager.getByProvince(province);
		sender.sendMessage("§7Province §f" + province + "§7: " + (chain.isEmpty() ? "§funtitled" : "§f" + String.join(" §7→ §f", chain)));
		sender.sendMessage("§7Controlled by: §f" + (owner == null ? "nobody" : owner.getName()));
	}

	public static List<String> complete(String[] args) {
		List<String> completions = new ArrayList<>();
		if (args.length == 2) {
			completions.addAll(SUBCOMMANDS);
			return filtered(completions, args[1]);
		}
		String sub = args[1].toLowerCase();
		if (args.length == 3) {
			if (sub.equals("list")) {
				for (Tier tier : TierLoader.get()) {
					if (!TitleLoader.getByTier(tier).isEmpty()) completions.add(tier.getId());
				}
			} else if (sub.equals("where")) {
				completions.add("<province>");
			} else if (SUBCOMMANDS.contains(sub) || sub.equals("setcolor")) {
				for (Title title : TitleLoader.getTitles()) {
					if (sub.equals("addprovince") && title.isComposite()) continue;
					if ((sub.equals("addtitle") || sub.equals("removetitle")) && !title.isComposite()) continue;
					completions.add(title.getId());
				}
			}
			return filtered(completions, args[2]);
		}
		Title title = TitleLoader.getById(args[2]);
		String last = args[args.length - 1];
		switch (sub) {
			case "rename" -> {
				if (args.length == 4) completions.add("<name>");
			}
			case "setcolour", "setcolor" -> {
				if (args.length == 4) completions.add(title != null && title.getRgb() != null ? title.getRgb() : "R,G,B");
			}
			case "addprovince" -> completions.add("<province>");
			case "removeprovince" -> {
				if (title != null) {
					for (int province : title.getProvinces()) completions.add(String.valueOf(province));
				}
			}
			case "addtitle" -> {
				if (args.length == 4 && title != null) {
					for (Title child : TitleLoader.getTitles()) {
						if (child.getTier().getTier() == title.getTier().getTier() - 1 && !title.getTitles().contains(child.getId())) {
							completions.add(child.getId());
						}
					}
				}
			}
			case "removetitle" -> {
				if (args.length == 4 && title != null) completions.addAll(title.getTitles());
			}
			case "setcomplete" -> {
				if (args.length == 4) {
					completions.add("true");
					completions.add("false");
				}
			}
			default -> {
			}
		}
		return filtered(completions, last);
	}

	private static List<String> filtered(List<String> completions, String prefix) {
		String lower = prefix == null ? "" : prefix.toLowerCase();
		completions.removeIf(s -> !s.toLowerCase().startsWith(lower));
		return completions;
	}
}
