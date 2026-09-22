package net.tfminecraft.simplefactions.war.civilwar;

import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.LogManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.utils.Formatter;
import net.tfminecraft.simplefactions.utils.RandomRGB;
import net.tfminecraft.tlibs.objects.api.subapi.StringFormatter;

public final class CivilWarTempRebelFactory {
	static final String MUTED_RED_RGB = "138,48,48";

	private CivilWarTempRebelFactory() {}

	public static Faction create(Faction host, String leaderName) {
		if (host == null || leaderName == null) {
			return null;
		}
		String id = uniqueId(host);
		Faction rebels = new Faction(id, leaderName);
		applyRebelIdentity(rebels, host);
		FactionManager.addFaction(rebels);
		return rebels;
	}

	public static Guild.RebelNation createFromMainGuild(Faction host, Guild main, String fallbackLeader) {
		if (main == null) {
			Faction rebels = create(host, fallbackLeader);
			return rebels == null ? null : new Guild.RebelNation(rebels, null);
		}
		Guild.RebelNation nation = main.rebel();
		if (nation == null || nation.faction() == null) {
			Faction rebels = create(host, fallbackLeader);
			return rebels == null ? null : new Guild.RebelNation(rebels, null);
		}
		applyRebelIdentity(nation.faction(), host);
		return nation;
	}

	static void applyRebelIdentity(Faction rebels, Faction host) {
		if (rebels == null) {
			return;
		}
		rebels.setName(StringFormatter.formatHex(Formatter.formatName(plainName(host) + " Rebels")));
		rebels.setRGB(uniqueMutedRed());
		LogManager.civilwar(
				"REBEL_FACTORY id=%s name=%s rgb=%s host=%s",
				rebels.getId(),
				Formatter.formatId(rebels.getName()),
				rebels.getRGB(),
				host != null ? host.getId() : "-");
	}

	static String uniqueMutedRed() {
		String rgb = MUTED_RED_RGB;
		int offset = 0;
		while (!RandomRGB.isFree(rgb) && offset < 80) {
			offset++;
			int green = Math.min(255, 48 + offset);
			rgb = "138," + green + ",48";
		}
		return rgb;
	}

	static String uniqueId(Faction host) {
		String base = Formatter.formatId(host.getId() + "_rebels");
		String id = base;
		int suffix = 2;
		while (FactionManager.getByString(id) != null) {
			id = base + suffix;
			suffix++;
		}
		return id;
	}

	static String plainName(Faction host) {
		if (host == null || host.getName() == null) {
			return "Rebel";
		}
		String plain = Formatter.formatId(host.getName()).trim();
		return plain.isEmpty() ? "Rebel" : plain;
	}
}
