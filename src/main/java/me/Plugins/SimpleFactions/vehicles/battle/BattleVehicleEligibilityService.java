package me.Plugins.SimpleFactions.vehicles.battle;


import me.Plugins.SimpleFactions.vehicles.registry.PlayerVehicleRegistry;
import me.Plugins.SimpleFactions.vehicles.registry.PlayerVehicleRecord;
import me.Plugins.SimpleFactions.vehicles.registry.OwnershipMode;
import me.Plugins.SimpleFactions.vehicles.registry.VehicleOwnershipQueries;
import me.Plugins.SimpleFactions.vehicles.berth.VehicleCategoryRules;
import java.util.Optional;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;

import net.tfminecraft.VehicleFramework.Enums.VehicleRemoveReason;
import net.tfminecraft.VehicleFramework.Events.VehiclePreInteractEvent;
import net.tfminecraft.VehicleFramework.Events.VehicleSpawnEvent;

import me.Plugins.SimpleFactions.Managers.FactionManager;
import me.Plugins.SimpleFactions.Managers.WarManager;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.War.campaign.runtime.pick.BattleInstallationInPlayService;
import me.Plugins.SimpleFactions.War.battle.engine.core.Battle;
import me.Plugins.SimpleFactions.War.battle.engine.core.BattleManager;
import me.Plugins.SimpleFactions.War.battle.warband.WarbandVehicleRules;
import me.Plugins.SimpleFactions.War.core.War;
import net.tfminecraft.VehicleFramework.Vehicles.ActiveVehicle;

public final class BattleVehicleEligibilityService {
	private BattleVehicleEligibilityService() {}

	public static boolean isEligible(War war, String factionId, PlayerVehicleRecord record) {
		if (war == null || factionId == null || factionId.isBlank() || record == null) {
			return true;
		}
		return isEligible(war, factionId, record.getVehicleTypeId(), record);
	}

	public static boolean isEligible(
			War war, String factionId, String vehicleTypeId, PlayerVehicleRecord berthRecord) {
		if (war == null || factionId == null || factionId.isBlank()) {
			return true;
		}
		if (!VehicleCategoryRules.isBerthableType(vehicleTypeId)) {
			return true;
		}
		if (berthRecord == null || berthRecord.getMode() != OwnershipMode.INSTALLATION) {
			return false;
		}
		String installationId = berthRecord.getInstallationId();
		if (installationId == null || installationId.isBlank()) {
			return false;
		}
		return BattleInstallationInPlayService.isInPlay(war, factionId, installationId);
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

		War war = WarManager.getById(battle.getWarId());
		if (war == null || !war.isActive()) {
			return BattleVehicleEligibilityResult.NOT_CAMPAIGN_BATTLE;
		}

		Faction faction = FactionManager.getByMember(player.getName());
		if (faction == null) {
			faction = FactionManager.getByLeader(player.getName());
		}
		if (faction == null || !war.isParticipating(faction)) {
			return BattleVehicleEligibilityResult.NOT_CAMPAIGN_BATTLE;
		}

		String vehicleTypeId = vehicle.getId();
		if (!VehicleCategoryRules.isBerthableType(vehicleTypeId)) {
			return BattleVehicleEligibilityResult.ALLOWED;
		}

		Optional<PlayerVehicleRecord> recordOpt = registry.getByVehicleUuid(vehicle.getUUID());
		PlayerVehicleRecord record = recordOpt.orElse(null);
		if (record == null || record.getMode() != OwnershipMode.INSTALLATION) {
			return BattleVehicleEligibilityResult.DENIED_NOT_BERTHED;
		}
		String installationId = record.getInstallationId();
		if (installationId == null || installationId.isBlank()) {
			return BattleVehicleEligibilityResult.DENIED_NOT_BERTHED;
		}
		if (!BattleInstallationInPlayService.isInPlay(war, faction.getId(), installationId)) {
			return BattleVehicleEligibilityResult.DENIED_NOT_COMMITTED;
		}
		return BattleVehicleEligibilityResult.ALLOWED;
	}

	public static Player resolveNotifyPlayer(ActiveVehicle vehicle, PlayerVehicleRegistry registry) {
		if (vehicle == null) {
			return null;
		}
		String ownerName = VehicleOwnershipQueries.playerNameFromOwner(
				vehicle.getOwnerData() == null ? null : vehicle.getOwnerData().getOwner());
		if (ownerName != null) {
			Player owner = Bukkit.getPlayerExact(ownerName);
			if (owner != null) {
				return owner;
			}
		}
		if (registry == null || vehicle.getUUID() == null) {
			return null;
		}
		return registry.getByVehicleUuid(vehicle.getUUID())
				.map(PlayerVehicleRecord::getPlayerUuid)
				.map(Bukkit::getPlayer)
				.orElse(null);
	}

		public enum BattleVehicleEligibilityResult {
		ALLOWED,
		NOT_CAMPAIGN_BATTLE,
		DENIED_PRE_BATTLE_WARBAND,
		DENIED_NOT_BERTHED,
		DENIED_NOT_COMMITTED;

		public boolean isDenied() {
			return this == DENIED_PRE_BATTLE_WARBAND
					|| this == DENIED_NOT_BERTHED
					|| this == DENIED_NOT_COMMITTED;
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
				case ALLOWED, NOT_CAMPAIGN_BATTLE -> null;
			};
		}
	}

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

		@EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
		public void onVehicleSpawn(VehicleSpawnEvent event) {
			ActiveVehicle vehicle = event.getVehicle();
			if (vehicle == null || vehicle.getUUID() == null) {
				return;
			}

			Player player = BattleVehicleEligibilityService.resolveNotifyPlayer(vehicle, registry);
			if (player == null) {
				return;
			}

			BattleVehicleEligibilityResult result = BattleVehicleEligibilityService.check(player, vehicle, registry);
			if (!result.isDenied()) {
				return;
			}

			vehicle.remove(VehicleRemoveReason.ADMIN_KILL);
			String message = Messages.forResult(result);
			if (message != null) {
				player.sendMessage(message);
			}
		}
	}
}
