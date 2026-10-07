package net.tfminecraft.simplefactions.war.campaign.raid;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.war.campaign.raid.CampaignRaidResults.LaunchResult;
import net.tfminecraft.simplefactions.war.campaign.raid.CampaignRaidResults.ValidateLaunchOutcome;
import net.tfminecraft.simplefactions.war.campaign.raid.CampaignRaidResults.ValidateLaunchResult;
import net.tfminecraft.simplefactions.war.campaign.runtime.BattleScheduleService;
import net.tfminecraft.simplefactions.war.campaign.runtime.BattleSideMembers;
import net.tfminecraft.simplefactions.war.campaign.raid.RaidTargetService.RaidKind;
import net.tfminecraft.simplefactions.war.campaign.raid.RaidTargetService.RaidTargetCandidate;
import net.tfminecraft.simplefactions.war.core.Side;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.war.core.WarDevMode;
import net.tfminecraft.simplefactions.installation.Installation;
import net.tfminecraft.simplefactions.installation.InstallationKind;
import net.tfminecraft.simplefactions.installation.handler.InstallationHandler;

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
		List<RaidTargetCandidate> candidates = new ArrayList<>();
		for (Faction enemy : BattleSideMembers.collectParticipatingFactions(enemySide)) {
			InstallationHandler handler = enemy.getInstallationHandler();
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
		Faction owner = findOwnerFaction(war, attacker, targetInstallationId);
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
		Faction targetOwner = findOwnerFaction(war, launcher, targetInstallationId);
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
		Faction owner = findOwnerFaction(war, attacker, installationId);
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
		return handler.getById(installationId);
	}

	public static Installation resolveSourceInstallation(CampaignRaid raid) {
		if (raid == null) return null;
		return resolveOwnedInstallation(FactionManager.getByString(raid.getLauncherFactionId()), raid.getSourceInstallationId());
	}

	public static Installation resolveTargetInstallation(War war, CampaignRaid raid) {
		if (raid == null) return null;
		return resolveTargetInstallation(war, raid.getLauncherFactionId(), raid.getTargetInstallationId());
	}

	public static Installation resolveTargetInstallation(War war, String launcherId, String targetId) {
		Faction owner = findOwnerFaction(war, FactionManager.getByString(launcherId), targetId);
		return resolveOwnedInstallation(owner, targetId);
	}

	private static Faction findOwnerFaction(War war, Faction attacker, String installationId) {
		if (war == null || attacker == null || installationId == null || installationId.isBlank()) return null;
		Side opposing = war.getOppositeSide(attacker);
		Faction found = null;
		for (Faction faction : BattleSideMembers.collectParticipatingFactions(opposing)) {
			if (resolveOwnedInstallation(faction, installationId) != null) {
				// The public raid command supplies a local id. Never choose between
				// distinct enemy installations that have the same id.
				if (found != null) return null;
				found = faction;
			}
		}
		return found;
	}

	private static ValidateLaunchResult mapLaunchResult(LaunchResult gate) {
		// canLaunch returns STARTED or the shared eligibility rejection statuses.
		return gate == LaunchResult.STARTED ? ValidateLaunchResult.OK : ValidateLaunchResult.valueOf(gate.name());
	}
}
