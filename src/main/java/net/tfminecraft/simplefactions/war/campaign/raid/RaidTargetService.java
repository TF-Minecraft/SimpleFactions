package net.tfminecraft.simplefactions.war.campaign.raid;


import net.tfminecraft.simplefactions.war.campaign.runtime.CampaignClock;
import net.tfminecraft.simplefactions.war.campaign.runtime.BattleScheduleService;
import net.tfminecraft.simplefactions.war.campaign.runtime.BattleSideMembers;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.war.campaign.raid.CampaignRaidEligibilityService;
import net.tfminecraft.simplefactions.war.core.Side;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.installation.Installation;
import net.tfminecraft.simplefactions.installation.InstallationKind;
import net.tfminecraft.simplefactions.installation.handler.InstallationHandler;

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
