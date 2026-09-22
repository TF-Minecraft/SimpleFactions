package me.Plugins.SimpleFactions.War.campaign.raid;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import me.Plugins.SimpleFactions.Managers.FactionManager;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.War.campaign.raid.CampaignRaidResults.LaunchResult;
import me.Plugins.SimpleFactions.War.campaign.raid.CampaignRaidResults.ValidateLaunchOutcome;
import me.Plugins.SimpleFactions.War.campaign.raid.CampaignRaidResults.ValidateLaunchResult;
import me.Plugins.SimpleFactions.War.campaign.runtime.BattleScheduleService;
import me.Plugins.SimpleFactions.War.campaign.runtime.BattleSideMembers;
import me.Plugins.SimpleFactions.War.campaign.raid.RaidTargetService.RaidKind;
import me.Plugins.SimpleFactions.War.campaign.raid.RaidTargetService.RaidTargetCandidate;
import me.Plugins.SimpleFactions.War.core.Side;
import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.War.core.WarDevMode;
import me.Plugins.SimpleFactions.installation.Installation;
import me.Plugins.SimpleFactions.installation.InstallationKind;
import me.Plugins.SimpleFactions.installation.handler.InstallationHandler;

public final class CampaignRaidEligibilityService {
	private static final Set<InstallationKind> SOURCE_KINDS = EnumSet.of(
			InstallationKind.PORT,
			InstallationKind.AIRPORT);
	private static final Set<InstallationKind> TARGET_KINDS = EnumSet.of(
			InstallationKind.PORT,
			InstallationKind.AIRPORT,
			InstallationKind.FORT);

	private CampaignRaidEligibilityService() {}

	public static boolean isValidSourceKind(InstallationKind kind) {
		return kind != null && SOURCE_KINDS.contains(kind);
	}

	public static boolean isValidTargetKind(InstallationKind kind) {
		return kind != null && TARGET_KINDS.contains(kind);
	}

	public static RaidKind inferRaidKind(InstallationKind sourceKind, InstallationKind targetKind) {
		if (sourceKind == null || targetKind == null) {
			return null;
		}
		if (sourceKind == InstallationKind.PORT && targetKind == InstallationKind.PORT) {
			return RaidKind.NAVAL;
		}
		if (sourceKind == InstallationKind.AIRPORT && targetKind == InstallationKind.AIRPORT) {
			return RaidKind.AIR;
		}
		if (isValidSourceKind(sourceKind) && targetKind == InstallationKind.FORT) {
			return RaidKind.FORT;
		}
		return null;
	}

	public static List<Installation> listValidSources(War war, String factionId, Instant now) {
		if (!isRaidListingAllowed(war, factionId, now)) {
			return List.of();
		}
		Faction faction = FactionManager.getByString(factionId);
		if (faction == null || war.getSide(faction) == null) {
			return List.of();
		}
		InstallationHandler handler = faction.getInstallationHandler();
		if (handler == null) {
			return List.of();
		}
		List<Installation> sources = new ArrayList<>();
		for (Installation installation : handler.getAll()) {
			if (installation == null || installation.getId() == null) {
				continue;
			}
			if (isValidSourceKind(installation.getKind())) {
				sources.add(installation);
			}
		}
		sources.sort(Comparator
				.comparing((Installation installation) -> installation.getKind().name())
				.thenComparing(Installation::getId, String.CASE_INSENSITIVE_ORDER));
		return List.copyOf(sources);
	}

	public static List<RaidTargetCandidate> listValidTargets(
			War war,
			String attackerFactionId,
			String sourceInstallationId,
			Instant now) {
		if (!isRaidListingAllowed(war, attackerFactionId, now)) {
			return List.of();
		}
		Faction attacker = FactionManager.getByString(attackerFactionId);
		if (attacker == null || war.getSide(attacker) == null) {
			return List.of();
		}
		Installation source = resolveOwnedInstallation(attacker, sourceInstallationId);
		if (source == null || !isValidSourceKind(source.getKind())) {
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
				RaidKind kind = inferRaidKind(source.getKind(), installation.getKind());
				if (kind != null && isValidTarget(war, attackerFactionId, sourceInstallationId, installation.getId(), now)) {
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

	public static boolean isValidSource(War war, String factionId, String installationId, Instant now) {
		if (!isRaidListingAllowed(war, factionId, now)) {
			return false;
		}
		Faction faction = FactionManager.getByString(factionId);
		if (faction == null || war.getSide(faction) == null) {
			return false;
		}
		Installation installation = resolveOwnedInstallation(faction, installationId);
		return installation != null && isValidSourceKind(installation.getKind());
	}

	public static boolean isValidTarget(
			War war,
			String attackerFactionId,
			String sourceInstallationId,
			String targetInstallationId,
			Instant now) {
		if (!isRaidListingAllowed(war, attackerFactionId, now)) {
			return false;
		}
		Faction attacker = FactionManager.getByString(attackerFactionId);
		if (attacker == null || war.getSide(attacker) == null) {
			return false;
		}
		Installation source = resolveOwnedInstallation(attacker, sourceInstallationId);
		if (source == null || !isValidSourceKind(source.getKind())) {
			return false;
		}
		Faction owner = findOwnerFaction(targetInstallationId);
		if (owner == null || !war.isParticipating(owner)) {
			return false;
		}
		Side attackerSide = war.getSide(attacker);
		Side ownerSide = war.getSide(owner);
		if (attackerSide == null || ownerSide == null || attackerSide == ownerSide) {
			return false;
		}
		Installation target = owner.getInstallationHandler().getById(targetInstallationId);
		if (target == null || !isValidTargetKind(target.getKind())) {
			return false;
		}
		return inferRaidKind(source.getKind(), target.getKind()) != null;
	}

	public static ValidateLaunchOutcome validateLaunch(
			War war,
			String launcherFactionId,
			String sourceInstallationId,
			String targetInstallationId,
			Instant now) {
		Faction launcher = FactionManager.getByString(launcherFactionId);
		LaunchResult gate = CampaignRaidService.canLaunch(war, launcher, now);
		ValidateLaunchResult mapped = mapLaunchResult(gate);
		if (mapped != ValidateLaunchResult.OK) {
			return ValidateLaunchOutcome.of(mapped);
		}
		if (sourceInstallationId == null || sourceInstallationId.isBlank()
				|| targetInstallationId == null || targetInstallationId.isBlank()) {
			return ValidateLaunchOutcome.of(ValidateLaunchResult.REJECTED_INVALID_SOURCE);
		}
		if (!isValidSource(war, launcherFactionId, sourceInstallationId, now)) {
			return ValidateLaunchOutcome.of(ValidateLaunchResult.REJECTED_INVALID_SOURCE);
		}
		Installation source = resolveOwnedInstallation(launcher, sourceInstallationId);
		Faction targetOwner = findOwnerFaction(targetInstallationId);
		if (targetOwner == null) {
			return ValidateLaunchOutcome.of(ValidateLaunchResult.REJECTED_INVALID_TARGET);
		}
		Installation target = targetOwner.getInstallationHandler().getById(targetInstallationId);
		if (target == null || !isValidTargetKind(target.getKind())) {
			return ValidateLaunchOutcome.of(ValidateLaunchResult.REJECTED_INVALID_TARGET);
		}
		if (!isValidTarget(war, launcherFactionId, sourceInstallationId, targetInstallationId, now)) {
			RaidKind kind = inferRaidKind(source.getKind(), target.getKind());
			if (kind == null) {
				return ValidateLaunchOutcome.of(ValidateLaunchResult.REJECTED_KIND_MISMATCH);
			}
			return ValidateLaunchOutcome.of(ValidateLaunchResult.REJECTED_INVALID_TARGET);
		}
		RaidKind raidKind = inferRaidKind(source.getKind(), target.getKind());
		if (raidKind == null) {
			return ValidateLaunchOutcome.of(ValidateLaunchResult.REJECTED_KIND_MISMATCH);
		}
		return ValidateLaunchOutcome.ok(raidKind);
	}

	public static boolean isValidTargetForRaidKind(
			War war,
			String attackerFactionId,
			String installationId,
			RaidKind raidKind,
			Instant now) {
		if (war == null
				|| !war.isActive()
				|| attackerFactionId == null
				|| attackerFactionId.isBlank()
				|| installationId == null
				|| installationId.isBlank()
				|| raidKind == null
				|| now == null) {
			return false;
		}
		if (!BattleScheduleService.isRaidWindowOpen(war, now)) {
			return false;
		}
		Faction attacker = FactionManager.getByString(attackerFactionId);
		if (attacker == null || !war.isParticipating(attacker)) {
			return false;
		}
		Faction owner = findOwnerFaction(installationId);
		if (owner == null || !war.isParticipating(owner)) {
			return false;
		}
		Side attackerSide = war.getSide(attacker);
		Side ownerSide = war.getSide(owner);
		if (attackerSide == null || ownerSide == null || attackerSide == ownerSide) {
			return false;
		}
		Installation installation = owner.getInstallationHandler().getById(installationId);
		return installation != null
				&& isValidTargetKind(installation.getKind())
				&& raidKind.matches(installation.getKind());
	}

	private static boolean isRaidListingAllowed(War war, String factionId, Instant now) {
		if (war == null
				|| !war.isActive()
				|| factionId == null
				|| factionId.isBlank()
				|| now == null) {
			return false;
		}
		if (WarDevMode.isEnabled()) {
			return true;
		}
		return war.getBattleDay() != null
				&& BattleScheduleService.isRaidWindowOpen(war, now);
	}

	private static Installation resolveOwnedInstallation(Faction faction, String installationId) {
		if (faction == null || installationId == null || installationId.isBlank()) {
			return null;
		}
		InstallationHandler handler = faction.getInstallationHandler();
		if (handler == null) {
			return null;
		}
		return handler.getById(installationId);
	}

	private static Faction findOwnerFaction(String installationId) {
		for (Faction faction : FactionManager.factions) {
			InstallationHandler handler = faction.getInstallationHandler();
			if (handler == null) {
				continue;
			}
			if (handler.getById(installationId) != null) {
				return faction;
			}
		}
		return null;
	}

	private static ValidateLaunchResult mapLaunchResult(LaunchResult gate) {
		if (gate == null) {
			return ValidateLaunchResult.REJECTED_WAR_INACTIVE;
		}
		return switch (gate) {
			case STARTED -> ValidateLaunchResult.OK;
			case REJECTED_WAR_INACTIVE -> ValidateLaunchResult.REJECTED_WAR_INACTIVE;
			case REJECTED_NOT_PARTICIPANT -> ValidateLaunchResult.REJECTED_NOT_PARTICIPANT;
			case REJECTED_OUTSIDE_WINDOW -> ValidateLaunchResult.REJECTED_OUTSIDE_WINDOW;
			case REJECTED_QUOTA_SPENT -> ValidateLaunchResult.REJECTED_QUOTA_SPENT;
			case REJECTED_RAID_IN_PROGRESS -> ValidateLaunchResult.REJECTED_RAID_IN_PROGRESS;
			case REJECTED_INVALID_INPUT -> ValidateLaunchResult.REJECTED_INVALID_SOURCE;
		};
	}
}
