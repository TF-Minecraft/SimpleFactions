package me.Plugins.SimpleFactions.War.campaign.raid;


import me.Plugins.SimpleFactions.War.campaign.runtime.CampaignClock;
import me.Plugins.SimpleFactions.War.campaign.runtime.BattleScheduleService;
import me.Plugins.SimpleFactions.War.campaign.runtime.BattleSideMembers;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import me.Plugins.SimpleFactions.Managers.FactionManager;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.War.campaign.raid.CampaignRaidEligibilityService;
import me.Plugins.SimpleFactions.War.core.Side;
import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.installation.Installation;
import me.Plugins.SimpleFactions.installation.InstallationKind;
import me.Plugins.SimpleFactions.installation.handler.InstallationHandler;

/**
 * Legacy raid-kind target listing. Campaign raids should use
 * {@link CampaignRaidEligibilityService} for source/target validation.
 */
public final class RaidTargetService {
	private RaidTargetService() {}

	public static boolean isValidTarget(
			War war,
			String attackerFactionId,
			String installationId,
			RaidKind raidKind) {
		return isValidTarget(war, attackerFactionId, installationId, raidKind, CampaignClock.now());
	}

	public static boolean isValidTarget(
			War war,
			String attackerFactionId,
			String installationId,
			RaidKind raidKind,
			Instant now) {
		return CampaignRaidEligibilityService.isValidTargetForRaidKind(
				war, attackerFactionId, installationId, raidKind, now);
	}

	public static List<RaidTargetCandidate> listValidTargets(
			War war,
			String attackerFactionId,
			RaidKind raidKind) {
		return listValidTargets(war, attackerFactionId, raidKind, CampaignClock.now());
	}

	public static List<RaidTargetCandidate> listValidTargets(
			War war,
			String attackerFactionId,
			RaidKind raidKind,
			Instant now) {
		if (war == null
				|| !war.isActive()
				|| attackerFactionId == null
				|| attackerFactionId.isBlank()
				|| raidKind == null
				|| now == null) {
			return List.of();
		}
		if (!BattleScheduleService.isRaidWindowOpen(war, now)) {
			return List.of();
		}

		Faction attacker = FactionManager.getByString(attackerFactionId);
		if (attacker == null || !war.isParticipating(attacker)) {
			return List.of();
		}

		Side enemySide = war.getOppositeSide(attacker);
		if (enemySide == null) {
			return List.of();
		}

		List<RaidTargetCandidate> candidates = new ArrayList<>();
		for (Faction enemy : BattleSideMembers.collectParticipatingFactions(enemySide)) {
			if (enemy == null || enemy.getId() == null) {
				continue;
			}
			InstallationHandler handler = enemy.getInstallationHandler();
			if (handler == null) {
				continue;
			}
			for (Installation installation : handler.getAll()) {
				if (installation == null || installation.getId() == null) {
					continue;
				}
				if (isValidTarget(war, attackerFactionId, installation.getId(), raidKind, now)) {
					candidates.add(new RaidTargetCandidate(
							enemy.getId(),
							installation.getId(),
							installation));
				}
			}
		}

		candidates.sort(Comparator
				.comparing(RaidTargetCandidate::ownerFactionId, String.CASE_INSENSITIVE_ORDER)
				.thenComparing(RaidTargetCandidate::installationId, String.CASE_INSENSITIVE_ORDER));
		return List.copyOf(candidates);
	}

	public enum RaidKind {
		NAVAL(InstallationKind.PORT),
		AIR(InstallationKind.AIRPORT),
		FORT(InstallationKind.FORT);

		private final InstallationKind installationKind;

		RaidKind(InstallationKind installationKind) {
			this.installationKind = installationKind;
		}

		public InstallationKind getInstallationKind() {
			return installationKind;
		}

		public boolean matches(InstallationKind kind) {
			return kind != null && installationKind == kind;
		}
	}

	public record RaidTargetCandidate(
			String ownerFactionId,
			String installationId,
			Installation installation) {}
}
