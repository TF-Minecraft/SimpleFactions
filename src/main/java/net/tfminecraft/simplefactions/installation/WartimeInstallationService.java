package net.tfminecraft.simplefactions.installation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.RelationManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.war.campaign.progression.BelligerentRole;
import net.tfminecraft.simplefactions.war.campaign.schedule.ScheduledCampaignBattle;
import net.tfminecraft.simplefactions.war.core.Side;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.war.enums.CampaignBattleKind;
import net.tfminecraft.simplefactions.installation.handler.InstallationHandler;

public final class WartimeInstallationService {
	private WartimeInstallationService() {}

	public static void occupyLastBattle(War war, BelligerentRole winner) {
		if (war == null || winner == null) {
			return;
		}
		Faction occupyingLeader = occupyingLeader(war, winner);
		List<Integer> occupied = war.getLastBattleOccupied();
		if (occupyingLeader == null || occupied == null || occupied.isEmpty()) {
			return;
		}
		for (int province : occupied) {
			occupy(war, occupyingLeader, province);
		}
	}

	public static void occupySiegeFort(War war, BelligerentRole winner, ScheduledCampaignBattle slot) {
		if (war == null || winner == null || slot == null) {
			return;
		}
		if (slot.kind() != CampaignBattleKind.SIEGE || slot.fortInstallationId() == null) {
			return;
		}
		Installation fort = InstallationLookup.all().stream()
				.filter(i -> i.getKind() == InstallationKind.FORT && i.getProvince() == slot.provinceId()
						&& slot.fortInstallationId().equals(i.getId()))
				.findFirst().orElse(null);
		if (fort == null) {
			return;
		}
		int province = fort.getProvince();
		List<Integer> occupied = war.getLastBattleOccupied();
		if (occupied != null && occupied.contains(province)) {
			return;
		}
		Faction occupyingLeader = occupyingLeader(war, winner);
		occupyFrom(war, occupyingLeader, InstallationOwners.ownerOf(fort), province);
	}

	public static void occupy(War war, Faction occupyingLeader, int province) {
		if (war == null || occupyingLeader == null || province <= 0) {
			return;
		}
		Faction holder = InstallationLookup.findHolderOnProvince(province);
		if (holder == null) {
			return;
		}
		occupyFrom(war, occupyingLeader, holder, province);
	}

	private static void occupyFrom(War war, Faction occupyingLeader, Faction holder, int province) {
		snapshotProvince(war, holder, province);
		Faction original = snapshotOriginal(war, holder, province);
		Faction target = occupyingLeader;
		if (original != null && onOccupyingSide(war, occupyingLeader, original)) {
			target = original;
		}
		InstallationTransferService.transfer(holder, target, province);
	}

	public static void revert(War war) {
		if (war == null) {
			return;
		}
		Map<String, String> snapshot = war.getWartimeInstallationOwners();
		if (snapshot == null || snapshot.isEmpty()) {
			return;
		}
		for (Map.Entry<String, String> entry : new ArrayList<>(snapshot.entrySet())) {
			if (entry == null || entry.getKey() == null || entry.getValue() == null) {
				continue;
			}
			Faction original = FactionManager.getByString(entry.getValue());
			Installation installation = snapshotInstallation(entry.getKey());
			Faction holder = InstallationOwners.ownerOf(installation);
			if (original == null || holder == null) {
				continue;
			}
			InstallationTransferService.transfer(holder, original, installation.getProvince());
		}
		war.clearWartimeInstallationOwners();
	}

	private static void snapshotProvince(War war, Faction holder, int province) {
		for (Installation installation : installationsOnProvince(holder, province)) {
			war.putWartimeInstallationOwner(installation.getStableKey(), holder.getId());
		}
	}

	private static Faction snapshotOriginal(War war, Faction holder, int province) {
		Map<String, String> snapshot = war.getWartimeInstallationOwners();
		for (Installation installation : installationsOnProvince(holder, province)) {
			String originalId = snapshot.get(installation.getStableKey());
			if (originalId != null) {
				Faction original = FactionManager.getByString(originalId);
				if (original != null) {
					return original;
				}
			}
		}
		return holder;
	}

	private static Installation snapshotInstallation(String key) {
		Installation found = null;
		for (Installation installation : InstallationLookup.all()) {
			if (key.equals(installation.getStableKey())) {
				if (found != null) return null;
				found = installation;
			}
		}
		return found;
	}

	private static boolean onOccupyingSide(War war, Faction occupyingLeader, Faction original) {
		if (occupyingLeader.getId() != null
				&& occupyingLeader.getId().equalsIgnoreCase(original.getId())) {
			return true;
		}
		Side occupyingSide = war.getSide(occupyingLeader);
		Side originalSide = war.getSide(original);
		if (occupyingSide != null && occupyingSide == originalSide) {
			return true;
		}
		if (RelationManager.sameRealm(occupyingLeader, original)) {
			return true;
		}
		List<Faction> subjects = RelationManager.getSubjects(occupyingLeader);
		return subjects != null && subjects.contains(original);
	}

	private static List<Installation> installationsOnProvince(Faction holder, int province) {
		List<Installation> found = new ArrayList<>();
		InstallationHandler handler = holder.getInstallationHandler();
		for (Installation installation : handler.getAll()) {
			if (installation.getProvince() == province) {
				found.add(installation);
			}
		}
		return found;
	}

	private static Faction occupyingLeader(War war, BelligerentRole winner) {
		if (winner == BelligerentRole.ATTACKER) {
			return war.getAttackers() != null ? war.getAttackers().getLeader() : null;
		}
		return war.getDefenders() != null ? war.getDefenders().getLeader() : null;
	}
}
