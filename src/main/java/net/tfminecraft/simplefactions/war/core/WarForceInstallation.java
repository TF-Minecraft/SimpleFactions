package net.tfminecraft.simplefactions.war.core;

import net.tfminecraft.simplefactions.installation.Installation;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.LogManager;
import net.tfminecraft.simplefactions.managers.WarManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.war.battle.engine.core.Battle;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleManager;
import net.tfminecraft.simplefactions.war.campaign.runtime.pick.BattleInstallationPickService;

/**
 * {@code /war admin forceinstallation <battle> <faction> <installation>}: staff commit one of a
 * war faction's installations to its campaign battle, past the pick rules (forts, occupied
 * installations, other kinds).
 */
final class WarForceInstallation {
	static final String USAGE = "Usage: /war admin forceinstallation <battle> <faction> <installation>";

	record Target(Battle battle, War war, Faction faction, Installation installation) {}

	/** Either a target or the reason there is none. */
	record Resolved(Target target, String error) {}

	private WarForceInstallation() {}

	/** {@code args} start at {@code admin} and carry no trailing {@code confirm}. */
	static Resolved resolve(String[] args) {
		if (args.length != 5) {
			return new Resolved(null, USAGE);
		}
		Battle battle = BattleManager.getByString(args[2]);
		if (battle == null) {
			return new Resolved(null, "There is no battle with the id " + args[2]);
		}
		War war = battle.getWarId() == null ? null : WarManager.getById(battle.getWarId());
		if (war == null || !war.isActive()) {
			return new Resolved(null, battle.getDisplayName() + " is not a battle of an active war.");
		}
		Faction faction = FactionManager.getByString(args[3]);
		if (faction == null) {
			return new Resolved(null, "Unknown faction: " + args[3]);
		}
		if (war.getSide(faction) == null) {
			return new Resolved(null, faction.getName() + " is not on a side of war #" + war.getId() + ".");
		}
		Installation installation = faction.getInstallationHandler().getById(args[4]);
		if (installation == null) {
			return new Resolved(null, faction.getName() + " has no installation with the id " + args[4] + ".");
		}
		return new Resolved(new Target(battle, war, faction, installation), null);
	}

	/** The confirm prompt, or null when the command would only refuse. */
	static String risk(String[] args) {
		Target target = resolve(args).target();
		return target == null ? null : "commits " + target.installation().getName() + " to this battle: vehicles"
				+ " berthed there can be used, it can't berth or unberth, and it's open to attack until the battle day ends.";
	}

	static String run(String[] args, String by) {
		Resolved resolved = resolve(args);
		if (resolved.error() != null) {
			return "§c" + resolved.error();
		}
		Target target = resolved.target();
		BattleInstallationPickService.forceAdd(target.war(), target.faction(), target.installation().getId());
		WarManager.persist(target.war());
		LogManager.war("ADMIN_FORCE_INSTALLATION warId=%d battle=%s by=%s faction=%s installation=%s",
				target.war().getId(), target.battle().getId(), by, target.faction().getId(),
				target.installation().getId());
		return "§aCommitted §e" + target.installation().getName() + " §a(" + target.faction().getName() + ") to "
				+ target.battle().getDisplayName() + " until the battle day ends.";
	}
}
