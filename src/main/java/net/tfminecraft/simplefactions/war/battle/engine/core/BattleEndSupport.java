package net.tfminecraft.simplefactions.war.battle.engine.core;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;

import net.tfminecraft.simplefactions.managers.WarManager;
import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.war.battle.enums.BattleEndReason;
import net.tfminecraft.simplefactions.war.battle.events.BattleEndedEvent;
import net.tfminecraft.simplefactions.war.battle.engine.raid.RaidAttackerEliminationService;
import net.tfminecraft.simplefactions.war.battle.engine.win.SiegeContestService;
import net.tfminecraft.simplefactions.war.battle.military.BattleCasualtyLedger;
import net.tfminecraft.simplefactions.war.campaign.raid.fight.CampaignRaidBattleService;
import net.tfminecraft.simplefactions.war.core.War;

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
