package net.tfminecraft.simplefactions.map;

import java.util.ArrayList;
import java.util.List;

import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.installation.InstallationTransferService;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.map.provinces.Province;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.war.civilwar.CivilWarCopy;
import net.tfminecraft.simplefactions.war.civilwar.wartime.CivilWarBorderLock;

/**
 * Staff overrides for land: hand a province to a realm, or unseat a guild capital.
 * Both skip prestige, caps and border rules, but still leave every realm's claims valid.
 */
public final class AdminLandService {
	private AdminLandService() {
	}

	/** Gives {@code province} to {@code to}, taking it from its current owner if needed. */
	public static List<String> forceClaim(Faction to, int province) {
		List<String> lines = new ArrayList<>();
		if (province <= 0) {
			lines.add("§cThis location has no province!");
			return lines;
		}
		Province site = SimpleFactions.getInstance().getProvinceManager().get(province);
		if (site == null || !site.isValid() || site.isSea()) {
			lines.add("§cThis location has no province!");
			return lines;
		}
		if (to.ownsProvince(province)) {
			lines.add("§c" + to.getName() + " §calready owns province " + province);
			return lines;
		}
		if (CivilWarBorderLock.isLocked(to)) {
			lines.add(CivilWarCopy.CANNOT_CLAIM);
			return lines;
		}
		Faction owner = FactionManager.getByProvince(province);
		if (owner != null) {
			if (owner.getCapital() == province) {
				lines.add("§cProvince " + province + " §cis the capital of " + owner.getName()
						+ "§c. Move their capital first.");
				return lines;
			}
			if (CivilWarBorderLock.isLocked(owner)) {
				lines.add(CivilWarCopy.CANNOT_STEAL);
				return lines;
			}
			InstallationTransferService.transfer(owner, to, province);
			FactionManager.getMap().unclaim(null, owner, province);
			lines.add("§aTook province " + province + " §afrom " + owner.getName());
		}
		to.addProvince(province);
		lines.add("§aGave province " + province + " §ato " + to.getName());
		if (!to.getProvinceHandler().previewLegalProvinces(to.getProvinces()).contains(province)) {
			lines.add("§eWarning: this province is not connected to " + to.getName()
					+ "§e's realm, so it will be dropped the next time their claims are checked.");
		}
		return lines;
	}

	/** Unseats a guild's capital and drops any land that only that capital was holding. */
	public static List<String> forceRemoveCapital(Guild guild) {
		List<String> lines = new ArrayList<>();
		Faction faction = guild.getFaction();
		if (!guild.hasCapital()) {
			lines.add("§c" + guild.getName() + " §chas no capital");
			return lines;
		}
		int capital = guild.getCapital();
		if (guild.isBase() || capital == faction.getCapital()) {
			lines.add("§cProvince " + capital + " §cis the faction capital of " + faction.getName()
					+ "§c. Move it with §e/faction setcapital §cinstead.");
			return lines;
		}
		if (CivilWarBorderLock.isLocked(faction)) {
			lines.add(CivilWarCopy.CANNOT_UNCLAIM);
			return lines;
		}
		List<Integer> before = new ArrayList<>(faction.getProvinces());
		guild.setCapital(-1);
		faction.getProvinceHandler().revalidateClaims();
		before.removeAll(faction.getProvinces());
		lines.add("§aRemoved the capital of " + guild.getName() + " §a(province " + capital + ")");
		if (!before.isEmpty()) {
			lines.add("§e" + faction.getName() + " §elost provinces cut off from their realm: " + before);
		}
		return lines;
	}
}
