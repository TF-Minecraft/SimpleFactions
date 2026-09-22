package me.Plugins.SimpleFactions.War.battle.engine.core;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;

import me.Plugins.SimpleFactions.Managers.WarManager;
import me.Plugins.SimpleFactions.SimpleFactions;
import me.Plugins.SimpleFactions.War.battle.enums.BattleEndReason;
import me.Plugins.SimpleFactions.War.battle.events.BattleEndedEvent;
import me.Plugins.SimpleFactions.War.battle.engine.raid.RaidAttackerEliminationService;
import me.Plugins.SimpleFactions.War.battle.engine.win.SiegeContestService;
import me.Plugins.SimpleFactions.War.battle.military.BattleCasualtyLedger;
import me.Plugins.SimpleFactions.War.campaign.raid.fight.CampaignRaidBattleService;
import me.Plugins.SimpleFactions.War.core.War;

public final class BattleEndSupport {
	private BattleEndSupport() {
	}

	public static void endBattle(Battle battle, String winningSideId) {
		BattleEndReason reason = winningSideId == null || winningSideId.isBlank()
				? BattleEndReason.TIMER
				: BattleEndReason.SIDE_WIN;
		endBattle(battle, winningSideId, reason);
	}

	public static void endBattle(Battle battle, String winningSideId, BattleEndReason endReason) {
		SiegeContestService.clearBattleState(battle);
		RaidAttackerEliminationService.clearBattleState(battle);
		battle.endTitle();
		Map<String, Integer> sideCasualties = BattleCasualtyLedger.getSideCasualties(battle);
		Set<UUID> participantIds = BattleParticipantCollector.collect(battle);
		boolean lootEnabled = battle.hasLootEnabled();
		battle.end();
		if (SimpleFactions.plugin != null) {
			boolean campaignRaid = battle.isCampaignRaid();
			if (!campaignRaid && battle.getWarId() != null) {
				War war = WarManager.getById(battle.getWarId());
				CampaignRaidBattleService.markAsCampaignRaidIfActive(war, battle);
				campaignRaid = battle.isCampaignRaid()
						|| CampaignRaidBattleService.isCampaignRaidBattle(war, battle);
			}
			Bukkit.getPluginManager().callEvent(
					new BattleEndedEvent(
							battle.getId(),
							battle.getBattleType(),
							battle.getWarId(),
							winningSideId,
							sideCasualties,
							participantIds,
							endReason,
							campaignRaid,
							lootEnabled));
		}
	}
}
