package me.Plugins.SimpleFactions.War.battle.engine.raid;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import me.Plugins.SimpleFactions.War.battle.engine.core.Battle;
import me.Plugins.SimpleFactions.War.battle.engine.core.BattleSide;
import me.Plugins.SimpleFactions.War.battle.enums.BattleType;
import me.Plugins.SimpleFactions.War.battle.enums.DefenderRespawnMode;
import me.Plugins.SimpleFactions.War.battle.template.BattleTemplate;

public final class RaidRespawnService {
	private RaidRespawnService() {
	}

	public static boolean applyRespawn(Battle battle, Player player, BattleSide side) {
		if (battle == null || player == null || side == null || battle.getBattleType() != BattleType.RAID) {
			return false;
		}
		if (BattleTemplate.ATTACKER_SIDE.equalsIgnoreCase(side.getId())) {
			RaidAttackerEliminationService.markOut(battle, player.getUniqueId());
			Location destination = side.getJail() != null ? side.getJail() : side.getSpawn();
			if (destination != null) {
				player.teleport(destination);
			}
			return true;
		}
		if (BattleRaidSetup.getEffectiveDefenderRespawnMode(battle) == DefenderRespawnMode.INFINITE) {
			if (side.getSpawn() != null) {
				player.teleport(side.getSpawn());
			}
			return true;
		}
		return false;
	}
}
