package net.tfminecraft.simplefactions.vehicles.battle;

import net.tfminecraft.simplefactions.identity.Members;


import net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRegistry;
import net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRecord;
import net.tfminecraft.simplefactions.vehicles.registry.OwnershipMode;
import net.tfminecraft.simplefactions.vehicles.pool.FactionVehiclePoolService;
import java.util.Optional;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;

import net.tfminecraft.vehicleframework.events.VehiclePreInteractEvent;

import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.WarManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.war.campaign.runtime.pick.BattleInstallationInPlayService;
import net.tfminecraft.simplefactions.war.battle.engine.core.Battle;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleManager;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleSide;
import net.tfminecraft.simplefactions.war.battle.warband.WarbandVehicleRules;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.vehicleframework.vehicles.ActiveVehicle;

public final class BattleVehicleEligibilityService {
	private BattleVehicleEligibilityService() {}

	public static boolean isEligible(War war, String factionId, PlayerVehicleRecord record) {
		if (war == null || factionId == null || factionId.isBlank()) {
			return true;
		}
		if (record == null) {
			return false;
		}
		return isEligible(war, factionId, record.getVehicleTypeId(), record);
	}

	public static boolean isEligible(
			War war, String factionId, String vehicleTypeId, PlayerVehicleRecord berthRecord) {
		if (war == null || factionId == null || factionId.isBlank()) {
			return true;
		}
		return decide(war, factionId, vehicleTypeId, berthRecord) == BattleVehicleEligibilityResult.ALLOWED;
	}

	static BattleVehicleEligibilityResult decide(
			War war, String factionId, String vehicleTypeId, PlayerVehicleRecord record) {
		if (record != null && record.getMode() == OwnershipMode.POOL) {
			return factionOnPlayerSide(war, factionId, record.getFactionId())
					? BattleVehicleEligibilityResult.ALLOWED
					: BattleVehicleEligibilityResult.DENIED_POOL_SIDE;
		}
		if (record != null && record.getMode() == OwnershipMode.INSTALLATION) {
			String installationId = record.getInstallationId();
			if (installationId == null || installationId.isBlank()) {
				return BattleVehicleEligibilityResult.DENIED_NOT_BERTHED;
			}
			Faction holder = FactionVehiclePoolService.payingFaction(record);
			if (holder == null || !factionOnPlayerSide(war, factionId, holder.getId())
					|| !BattleInstallationInPlayService.isInPlay(war, holder.getId(), installationId)) {
				return BattleVehicleEligibilityResult.DENIED_NOT_COMMITTED;
			}
			return BattleVehicleEligibilityResult.ALLOWED;
		}
		return BattleVehicleEligibilityResult.DENIED_NOT_FACTION_VEHICLE;
	}

	private static boolean factionOnPlayerSide(War war, String playerFactionId, String vehicleFactionId) {
		if (vehicleFactionId == null || vehicleFactionId.isBlank()) {
			return false;
		}
		Faction playerFaction = FactionManager.getByString(playerFactionId);
		Faction vehicleFaction = FactionManager.getByString(vehicleFactionId);
		if (playerFaction == null || vehicleFaction == null) {
			return false;
		}
		var playerSide = war.getSide(playerFaction);
		var vehicleSide = war.getSide(vehicleFaction);
		return playerSide != null && playerSide.equals(vehicleSide);
	}

	public static BattleVehicleEligibilityResult check(
			Player player,
			ActiveVehicle vehicle,
			PlayerVehicleRegistry registry) {
		if (player == null || vehicle == null || registry == null) {
			return BattleVehicleEligibilityResult.ALLOWED;
		}

		if (WarbandVehicleRules.blocksVehicleEntry(player)) {
			return BattleVehicleEligibilityResult.DENIED_PRE_BATTLE_WARBAND;
		}

		Battle battle = BattleManager.getBattleByMemberId(player.getUniqueId());
		if (battle == null || battle.getWarId() == null) {
			return BattleVehicleEligibilityResult.NOT_CAMPAIGN_BATTLE;
		}
		// Staff overrides come before the berth and pool rules.
		if (battle.isAllVehiclesUsable()) {
			return BattleVehicleEligibilityResult.ALLOWED;
		}
		String forcedSide = battle.getForcedVehicleSide(vehicle.getUUID());
		if (forcedSide != null) {
			BattleSide side = battle.getSideByMemberId(player.getUniqueId());
			return side != null && side.getId().equalsIgnoreCase(forcedSide)
					? BattleVehicleEligibilityResult.ALLOWED
					: BattleVehicleEligibilityResult.DENIED_FORCED_OTHER_SIDE;
		}

		War war = WarManager.getById(battle.getWarId());
		if (war == null || !war.isActive()) {
			return BattleVehicleEligibilityResult.NOT_CAMPAIGN_BATTLE;
		}

		Faction faction = FactionManager.getByMember(Members.key(player));
		if (faction == null) {
			faction = FactionManager.getByLeader(Members.key(player));
		}
		if (faction == null || !war.isParticipating(faction)) {
			return BattleVehicleEligibilityResult.NOT_CAMPAIGN_BATTLE;
		}

		Optional<PlayerVehicleRecord> recordOpt = registry.getByVehicleUuid(vehicle.getUUID());
		return decide(war, faction.getId(), vehicle.getId(), recordOpt.orElse(null));
	}

		public enum BattleVehicleEligibilityResult {
		ALLOWED,
		NOT_CAMPAIGN_BATTLE,
		DENIED_PRE_BATTLE_WARBAND,
		DENIED_NOT_BERTHED,
		DENIED_NOT_COMMITTED,
		DENIED_NOT_FACTION_VEHICLE,
		DENIED_POOL_SIDE,
		DENIED_FORCED_OTHER_SIDE;

		public boolean isDenied() {
			return this == DENIED_PRE_BATTLE_WARBAND
					|| this == DENIED_NOT_BERTHED
					|| this == DENIED_NOT_COMMITTED
					|| this == DENIED_NOT_FACTION_VEHICLE
					|| this == DENIED_POOL_SIDE
					|| this == DENIED_FORCED_OTHER_SIDE;
		}
	}

	public static final class Messages {
		private Messages() {}

		public static String forResult(BattleVehicleEligibilityResult result) {
			if (result == null) {
				return null;
			}
			return switch (result) {
				case DENIED_PRE_BATTLE_WARBAND ->
						"§c" + WarbandVehicleRules.VEHICLE_BLOCKED_PRE_BATTLE;
				case DENIED_NOT_BERTHED ->
						"§cThis vehicle must be berthed at a committed installation for this battle.";
				case DENIED_NOT_COMMITTED ->
						"§cThis vehicle is berthed at an installation not committed for this battle.";
				case DENIED_NOT_FACTION_VEHICLE ->
						"§cOnly faction pool and installation vehicles can be used in a campaign battle.";
				case DENIED_POOL_SIDE ->
						"§cThis pool vehicle belongs to a faction that is not on your side.";
				case DENIED_FORCED_OTHER_SIDE ->
						"§cStaff assigned this vehicle to the other side for this battle.";
				case ALLOWED, NOT_CAMPAIGN_BATTLE -> null;
			};
		}
	}

	/**
	 * Blocks boarding only. It never removes a vehicle, because a removed vehicle loses its
	 * faction or installation record and drops out of its faction.
	 */
	public static final class Listener implements org.bukkit.event.Listener {
		private final PlayerVehicleRegistry registry;

		public Listener(PlayerVehicleRegistry registry) {
			this.registry = registry;
		}

		@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
		public void onVehiclePreInteract(VehiclePreInteractEvent event) {
			Player player = event.getPlayer();
			ActiveVehicle vehicle = event.getVehicle();
			if (player == null || vehicle == null) {
				return;
			}

			BattleVehicleEligibilityResult result = BattleVehicleEligibilityService.check(player, vehicle, registry);
			if (!result.isDenied()) {
				return;
			}

			String message = Messages.forResult(result);
			if (message != null) {
				player.sendMessage(message);
			}
			event.setCancelled(true);
		}
	}
}
