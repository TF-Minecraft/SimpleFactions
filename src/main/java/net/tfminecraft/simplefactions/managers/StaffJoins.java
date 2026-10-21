package net.tfminecraft.simplefactions.managers;

import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.guild.HeldRealms;
import net.tfminecraft.simplefactions.identity.Members;
import net.tfminecraft.simplefactions.mercenary.MercenaryResult;
import net.tfminecraft.simplefactions.mercenary.company.MercenaryCompany;
import net.tfminecraft.simplefactions.mercenary.company.MercenaryCompanyService;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.war.battle.campaign.warband.CampaignWarbandSignupService;
import net.tfminecraft.simplefactions.war.battle.warband.Warband;

/**
 * Staff tools that put a character straight into a guild, mercenary company or
 * warband. They skip the invite and nothing else: every other joining rule
 * still applies. Each returns the line to show the staff member.
 */
public final class StaffJoins {
	private StaffJoins() {}

	static String noMatch(String input) {
		return "§cNo online character or character ID matches " + input;
	}

	/** {@code /guild forcejoin}: a stranger or a member of the base guild joins a guild of the same realm. */
	public static String guild(Guild guild, String input) {
		if (guild.isBase()) return "§cThis is the base guild, use /faction forcejoin instead";
		Faction realm = guild.getFaction();
		if (HeldRealms.isClosing(realm)) return HeldRealms.CLOSING;
		String key = Members.findOnline(input);
		if (key == null) return noMatch(input);
		if (guild.isMember(key)) return "§c" + Members.display(key) + " §cis already in " + guild.getName();
		Guild current = FactionManager.getGuildByMember(key);
		if (current != null && current.getFaction() != realm) {
			return "§c" + Members.display(key) + " §cbelongs to " + current.getFaction().getName()
					+ "§c, another faction";
		}
		if (current != null && !current.isBase()) {
			return "§c" + Members.display(key) + " §cis already in the guild " + current.getName();
		}
		if (current != null && current.isLeader(key)) {
			return "§c" + Members.display(key) + " §cleads the faction and cannot join a guild";
		}
		guild.addMember(key);
		realm.updatePrestige();
		Members.online(key).ifPresent(joiner -> joiner.sendMessage("§aStaff added you to the guild " + guild.getName()));
		for (Player online : Bukkit.getOnlinePlayers()) {
			String member = Members.key(online);
			if (member != null && !member.equals(key) && guild.isMember(member)) {
				online.sendMessage("§a" + Members.display(key) + " joined the guild " + guild.getName() + "§a!");
			}
		}
		return "§aAdded " + Members.display(key) + " §ato " + guild.getName();
	}

	/** {@code /company admin add}: enlists a character in a free slot. */
	public static String company(MercenaryCompany company, String input) {
		String key = Members.findOnline(input);
		if (key == null) return noMatch(input);
		MercenaryResult result = MercenaryCompanyService.join(company, key);
		if (!result.ok()) return "§cCannot enlist " + Members.display(key) + ": " + result.message();
		Members.online(key).ifPresent(joiner -> joiner.sendMessage("§aStaff enlisted you in " + company.getName() + "."));
		Members.online(company.getLeader()).ifPresent(leader ->
				leader.sendMessage("§a" + Members.display(key) + " signed on with the company."));
		return "§aEnlisted " + Members.display(key) + " §ain " + company.getName();
	}

	/** {@code /warband forcejoin}: signs a character up as if the warband were open to them. */
	public static String warband(Warband warband, String input) {
		String key = Members.findOnline(input);
		if (key == null) return noMatch(input);
		UUID account = Members.accountId(key).orElse(null);
		if (account == null) return noMatch(input);
		if (warband.hasMember(account)) return "§c" + Members.display(key) + " §cis already in " + warband.getId();
		Faction faction = FactionManager.getByMember(key);
		if (warband.isFaction() && faction == null) {
			return "§cThis is a faction warband, " + Members.display(key) + " §cneeds a faction to join";
		}
		Player online = Members.player(key);
		String failure = CampaignWarbandSignupService.signupMember(account, key, warband, faction, online);
		if (failure != null) return "§cCannot add " + Members.display(key) + ": " + failure;
		if (online != null) online.sendMessage("§aStaff added you to the warband " + warband.getId());
		return "§aAdded " + Members.display(key) + " §ato " + warband.getId();
	}
}
