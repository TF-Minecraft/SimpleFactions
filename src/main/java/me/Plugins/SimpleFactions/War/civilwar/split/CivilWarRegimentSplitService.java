package me.Plugins.SimpleFactions.War.civilwar.split;

import java.util.LinkedHashMap;
import java.util.Map;

import me.Plugins.SimpleFactions.Army.Military;
import me.Plugins.SimpleFactions.Army.Regiment;
import me.Plugins.SimpleFactions.Managers.LogManager;
import me.Plugins.SimpleFactions.Objects.Faction;

public final class CivilWarRegimentSplitService {
	private CivilWarRegimentSplitService() {}

	public static Map<String, Integer> split(Faction host, Faction rebels, double powerPercent) {
		Map<String, Integer> moved = new LinkedHashMap<>();
		if (host == null || rebels == null) {
			return moved;
		}
		Military hostMilitary = host.getMilitary();
		Military rebelMilitary = rebels.getMilitary();
		if (hostMilitary == null || rebelMilitary == null) {
			return moved;
		}
		double percent = Math.max(0, Math.min(100, powerPercent));
		if (percent <= 0) {
			return moved;
		}
		if (hostMilitary.getRegiments() == null) {
			return moved;
		}
		zeroRebelGrant(rebelMilitary);
		for (Regiment hostRegiment : hostMilitary.getRegiments()) {
			if (hostRegiment == null || hostRegiment.isLevy() || hostRegiment.getId() == null) {
				continue;
			}
			Regiment rebelRegiment = rebelMilitary.getRegiment(hostRegiment.getId());
			if (rebelRegiment == null) {
				continue;
			}
			int current = Math.max(0, hostRegiment.getCurrentSlots());
			int free = Math.max(0, hostRegiment.getFreeSlots());
			int paid = Math.max(0, current - free);
			int transfer = Math.min(paid, (int) Math.round(paid * percent / 100.0));
			LogManager.civilwar(
					"REGIMENT host=%s rebels=%s type=%s current=%d free=%d paid=%d percent=%.1f transfer=%d",
					host.getId(),
					rebels.getId(),
					hostRegiment.getId(),
					current,
					free,
					paid,
					percent,
					transfer);
			if (transfer <= 0) {
				continue;
			}
			hostRegiment.setCurrentSlots(current - transfer);
			rebelRegiment.setCurrentSlots(transfer);
			rebelRegiment.setFreeSlots(0);
			moved.put(hostRegiment.getId(), transfer);
			LogManager.civilwar(
					"REGIMENT_AFTER type=%s hostCurrent=%d rebelCurrent=%d rebelFree=%d",
					hostRegiment.getId(),
					hostRegiment.getCurrentSlots(),
					rebelRegiment.getCurrentSlots(),
					rebelRegiment.getFreeSlots());
		}
		return moved;
	}

	private static void zeroRebelGrant(Military rebelMilitary) {
		if (rebelMilitary.getRegiments() == null) {
			return;
		}
		for (Regiment rebelRegiment : rebelMilitary.getRegiments()) {
			if (rebelRegiment == null || rebelRegiment.isLevy()) {
				continue;
			}
			rebelRegiment.setCurrentSlots(0);
			rebelRegiment.setFreeSlots(0);
		}
	}

	public static void rollback(Faction host, Faction rebels, Map<String, Integer> moved) {
		if (host == null || rebels == null || moved == null || moved.isEmpty()) {
			return;
		}
		Military hostMilitary = host.getMilitary();
		Military rebelMilitary = rebels.getMilitary();
		if (hostMilitary == null || rebelMilitary == null) {
			return;
		}
		for (Map.Entry<String, Integer> entry : moved.entrySet()) {
			if (entry.getKey() == null || entry.getValue() == null || entry.getValue() <= 0) {
				continue;
			}
			int amount = entry.getValue();
			Regiment hostRegiment = hostMilitary.getRegiment(entry.getKey());
			Regiment rebelRegiment = rebelMilitary.getRegiment(entry.getKey());
			if (hostRegiment != null) {
				hostRegiment.setCurrentSlots(Math.max(0, hostRegiment.getCurrentSlots()) + amount);
			}
			if (rebelRegiment != null) {
				rebelRegiment.setCurrentSlots(Math.max(0, rebelRegiment.getCurrentSlots() - amount));
			}
		}
	}

	public static void mergeRemaining(Faction from, Faction to) {
		if (from == null || to == null) {
			return;
		}
		Military fromMilitary = from.getMilitary();
		Military toMilitary = to.getMilitary();
		if (fromMilitary == null || toMilitary == null || fromMilitary.getRegiments() == null) {
			return;
		}
		for (Regiment fromRegiment : fromMilitary.getRegiments()) {
			if (fromRegiment == null || fromRegiment.isLevy() || fromRegiment.getId() == null) {
				continue;
			}
			Regiment toRegiment = toMilitary.getRegiment(fromRegiment.getId());
			if (toRegiment == null) {
				continue;
			}
			int slots = Math.max(0, fromRegiment.getCurrentSlots());
			if (slots <= 0) {
				continue;
			}
			toRegiment.setCurrentSlots(Math.max(0, toRegiment.getCurrentSlots()) + slots);
			fromRegiment.setCurrentSlots(0);
		}
	}
}
