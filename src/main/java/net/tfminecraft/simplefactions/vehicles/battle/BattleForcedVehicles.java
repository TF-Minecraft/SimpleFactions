package net.tfminecraft.simplefactions.vehicles.battle;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;

import net.tfminecraft.simplefactions.managers.LogManager;
import net.tfminecraft.simplefactions.war.battle.engine.core.Battle;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleManager;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleSide;
import net.tfminecraft.simplefactions.war.battle.persistence.BattlePersistenceService;
import net.tfminecraft.vehicleframework.events.VehiclePreInteractEvent;
import net.tfminecraft.vehicleframework.vehicles.ActiveVehicle;

/**
 * Staff click mode for {@code /warband forcevehicle}: while armed, each vehicle the staff member
 * right-clicks is assigned to (or released from) one side of a campaign battle, whatever its
 * berth or pool says.
 */
public final class BattleForcedVehicles {
	/** The battle side the next clicked vehicles go to. */
	public record Session(String battleId, String sideId) {}

	private static final Map<UUID, Session> SESSIONS = new HashMap<>();

	private BattleForcedVehicles() {}

	public static void arm(UUID staff, String battleId, String sideId) {
		SESSIONS.put(staff, new Session(battleId, sideId));
	}

	/** Ends the click mode; false when it was not armed. */
	public static boolean disarm(UUID staff) {
		return SESSIONS.remove(staff) != null;
	}

	public static Session get(UUID staff) {
		return SESSIONS.get(staff);
	}

	static void resetForTests() {
		SESSIONS.clear();
	}

	public static String armedMessage(Battle battle, String sideId) {
		return "§aForce-vehicle mode on: right-click vehicles to let " + sideId + " use them in "
				+ battle.getDisplayName() + ". Click one again to undo. §7/warband forcevehicle disable to stop.";
	}

	/** Assigns the vehicle to the side, moves it from the other side, or releases it when it is already there. */
	public static String toggle(Battle battle, String sideId, ActiveVehicle vehicle, String by) {
		String uuid = vehicle.getUUID();
		String current = battle.getForcedVehicleSide(uuid);
		boolean release = sideId.equalsIgnoreCase(current);
		battle.setForcedVehicleSide(uuid, release ? null : sideId);
		BattlePersistenceService.persistBattle(battle);
		LogManager.war("STAFF_BATTLE_FORCEVEHICLE battle=%s war=%s by=%s: vehicle=%s type=%s side=%s",
				battle.getId(), battle.getWarId(), by, uuid, vehicle.getId(), release ? "none" : sideId);
		return release
				? "§eVehicle " + vehicle.getId() + " is no longer forced to " + sideId + " in "
						+ battle.getDisplayName() + "; the normal vehicle rules apply."
				: "§aVehicle " + vehicle.getId() + " can now be used by " + sideId + " in "
						+ battle.getDisplayName() + ".";
	}

	/** Runs before the battle eligibility and transfer listeners, so the staff click only assigns. */
	public static final class Listener implements org.bukkit.event.Listener {
		@EventHandler(priority = EventPriority.LOW)
		public void onVehiclePreInteract(VehiclePreInteractEvent event) {
			Player player = event.getPlayer();
			Session session = player == null ? null : SESSIONS.get(player.getUniqueId());
			if (session == null) {
				return;
			}
			ActiveVehicle vehicle = event.getVehicle();
			if (vehicle == null || vehicle.getUUID() == null) {
				return;
			}
			event.setCancelled(true);
			Battle battle = BattleManager.getByString(session.battleId());
			BattleSide side = battle == null ? null : battle.getSideById(session.sideId());
			if (side == null) {
				SESSIONS.remove(player.getUniqueId());
				player.sendMessage("§cThat battle or side is gone. Force-vehicle mode off.");
				return;
			}
			player.sendMessage(toggle(battle, side.getId(), vehicle, player.getName()));
		}
	}
}
