package me.Plugins.SimpleFactions.War.battle.persistence;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import me.Plugins.SimpleFactions.Database.BattleData;
import me.Plugins.SimpleFactions.Database.BattleSideData;
import me.Plugins.SimpleFactions.Database.Database;
import me.Plugins.SimpleFactions.Database.JsonUtil;
import me.Plugins.SimpleFactions.Managers.WarManager;
import me.Plugins.SimpleFactions.SimpleFactions;
import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.War.battle.campaign.BattleNamingService;
import me.Plugins.SimpleFactions.War.battle.campaign.CampaignBattleRosterService;
import me.Plugins.SimpleFactions.War.battle.engine.core.Battle;
import me.Plugins.SimpleFactions.War.battle.engine.capture.BattleCapturePoints;
import me.Plugins.SimpleFactions.War.battle.engine.core.BattleManager;
import me.Plugins.SimpleFactions.War.battle.engine.core.BattleSide;
import me.Plugins.SimpleFactions.War.battle.template.BattleTemplate;
import me.Plugins.SimpleFactions.War.battle.warband.Warband;
import me.Plugins.SimpleFactions.War.battle.warband.WarbandManager;
import me.Plugins.SimpleFactions.War.campaign.raid.fight.CampaignRaidBattleService;
import me.Plugins.SimpleFactions.War.campaign.raid.fight.CampaignRaidBossBarService;
import me.Plugins.SimpleFactions.War.campaign.raid.fight.CampaignRaidResumeService;

public final class BattlePersistenceService {
	private static final Database DATABASE = new Database();
	private static BukkitTask autosaveTask;

	private BattlePersistenceService() {
	}

	public static void startAutosave() {
		stopAutosave();
		autosaveTask = Bukkit.getScheduler().runTaskTimer(
				SimpleFactions.plugin,
				BattlePersistenceService::saveAll,
				1200L,
				1200L);
	}

	public static void stopAutosave() {
		if (autosaveTask != null) {
			autosaveTask.cancel();
			autosaveTask = null;
		}
	}

	public static void loadAll() {
		WarbandManager.resetForTests();
		BattleManager.resetForTests();

		for (Warband warband : DATABASE.loadWarbands()) {
			if (WarbandManager.getByString(warband.getId()) == null) {
				WarbandManager.addWarband(warband);
			}
		}

		List<Battle> manualBattles = new ArrayList<>();
		for (File file : listBattleFiles()) {
			try {
				BattleData data = JsonUtil.readJson(file, BattleData.class);
				if (data == null || data.id == null) {
					continue;
				}
				if (!isCampaignBattleValid(data)) {
					DATABASE.deleteBattleFile(data.id);
					continue;
				}
				Battle battle = BattleMapper.fromData(data);
				if (battle == null) {
					continue;
				}
				normalizeCapturePointsAfterLoad(battle);
				linkWarbands(battle, data);
				if (battle.getWarId() != null) {
					CampaignRaidResumeService.applyLoadedBattle(battle);
				}
				if (battle.getWarId() == null) {
					manualBattles.add(battle);
					continue;
				}
				if (BattleManager.getByString(battle.getId()) != null) {
					DATABASE.deleteBattleFile(battle.getId());
					continue;
				}
				if (!battle.isCampaignRaid() && BattleManager.getByWarId(battle.getWarId()) != null) {
					DATABASE.deleteBattleFile(battle.getId());
					continue;
				}
				BattleManager.addBattle(battle);
				War war = WarManager.getById(battle.getWarId());
				if (war != null && war.isActive() && !CampaignRaidBattleService.isCampaignRaidBattle(war, battle)) {
					CampaignBattleRosterService.ensureEnrolled(war, battle);
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		Battle keptManual = chooseManualBattle(manualBattles);
		if (manualBattles.size() > 1 && SimpleFactions.plugin != null) {
			SimpleFactions.plugin.getLogger().warning(
					"Multiple manual battle files found; keeping "
							+ (keptManual != null ? keptManual.getId() : "none")
							+ " and removing duplicates.");
		}
		for (Battle battle : manualBattles) {
			if (battle == keptManual) {
				if (BattleManager.getByString(battle.getId()) == null) {
					BattleManager.addBattle(battle);
				}
			} else {
				DATABASE.deleteBattleFile(battle.getId());
			}
		}

		CampaignRaidResumeService.resumeAll();
	}

	public static void saveAll() {
		purgeOrphanManualWarbands();
		Set<String> battleIds = new HashSet<>();
		Set<String> referencedWarbandIds = collectReferencedWarbandIds();
		for (Battle battle : BattleManager.get()) {
			battleIds.add(battle.getId());
			DATABASE.saveBattle(battle);
		}
		for (Warband warband : WarbandManager.get()) {
			if (referencedWarbandIds.contains(warband.getId())) {
				DATABASE.saveWarband(warband);
			}
		}
		pruneStaleFiles(battleIds, referencedWarbandIds);
	}

	public static void persistBattle(Battle battle) {
		if (battle == null) {
			return;
		}
		DATABASE.saveBattle(battle);
	}

	public static void persistWarband(Warband warband) {
		if (warband == null) {
			return;
		}
		if (collectReferencedWarbandIds().contains(warband.getId())) {
			DATABASE.saveWarband(warband);
		}
	}

	public static void deleteManualBattle(Battle battle) {
		if (battle == null || battle.getWarId() != null) {
			return;
		}
		Set<String> remainingReferences = new HashSet<>(collectReferencedWarbandIds());
		remainingReferences.removeAll(warbandIdsOnBattle(battle));
		removeBattleWarbands(battle, remainingReferences);
		BattleManager.deleteBattle(battle);
		DATABASE.deleteBattleFile(battle.getId());
	}

	public static void deleteCampaignBattle(Battle battle) {
		if (battle == null || battle.getWarId() == null) {
			return;
		}
		removeAutoBattleWarbands(battle);
		purgeCampaignWarbandsForBattle(battle);
		purgeLegacyCampaignWarbandsForWar(battle.getWarId());
		BattleManager.deleteBattle(battle);
		DATABASE.deleteBattleFile(battle.getId());
	}

	public static void deleteRaidBattle(Battle battle) {
		if (battle == null) {
			return;
		}
		CampaignRaidBossBarService.clear(battle);
		removeAutoBattleWarbands(battle);
		BattleManager.deleteBattle(battle);
		DATABASE.deleteBattleFile(battle.getId());
	}

	public static void purgeCampaignWarbandsForBattle(Battle battle) {
		if (battle == null) {
			return;
		}
		purgeCampaignWarband(BattleNamingService.campaignWarbandId(
				battle.getDisplayName(), BattleTemplate.ATTACKER_SIDE));
		purgeCampaignWarband(BattleNamingService.campaignWarbandId(
				battle.getDisplayName(), BattleTemplate.DEFENDER_SIDE));
	}

	public static void purgeCampaignWarbandsForWar(int warId) {
		Battle battle = BattleManager.getByWarId(warId);
		if (battle != null) {
			purgeCampaignWarbandsForBattle(battle);
		}
		purgeLegacyCampaignWarbandsForWar(warId);
	}

	private static void purgeLegacyCampaignWarbandsForWar(int warId) {
		purgeCampaignWarband(Warband.campaignSideWarbandId(warId, BattleTemplate.ATTACKER_SIDE));
		purgeCampaignWarband(Warband.campaignSideWarbandId(warId, BattleTemplate.DEFENDER_SIDE));
	}

	private static void purgeCampaignWarband(String warbandId) {
		if (warbandId == null) {
			return;
		}
		Warband warband = WarbandManager.getByString(warbandId);
		if (warband != null) {
			WarbandManager.deleteWarband(warband);
		}
		DATABASE.deleteWarbandFile(warbandId);
	}

	public static Set<String> collectReferencedWarbandIds() {
		Set<String> ids = new LinkedHashSet<>();
		for (Battle battle : BattleManager.get()) {
			ids.addAll(warbandIdsOnBattle(battle));
		}
		return ids;
	}

	public static void deleteWarband(Warband warband) {
		if (warband == null) {
			return;
		}
		String warbandId = warband.getId();
		WarbandManager.deleteWarband(warband);
		DATABASE.deleteWarbandFile(warbandId);
	}

	public static void purgeOrphanManualWarbands() {
		Set<String> referenced = collectReferencedWarbandIds();
		List<Warband> toRemove = new ArrayList<>();
		for (Warband warband : WarbandManager.get()) {
			if (!warband.isFaction() && !referenced.contains(warband.getId())) {
				toRemove.add(warband);
			}
		}
		for (Warband warband : toRemove) {
			WarbandManager.deleteWarband(warband);
			DATABASE.deleteWarbandFile(warband.getId());
		}
	}

	private static void removeAutoBattleWarbands(Battle battle) {
		for (String warbandId : warbandIdsOnBattle(battle)) {
			purgeCampaignWarband(warbandId);
		}
	}

	private static void removeBattleWarbands(Battle battle, Set<String> stillReferencedElsewhere) {
		for (String warbandId : warbandIdsOnBattle(battle)) {
			if (stillReferencedElsewhere.contains(warbandId)) {
				continue;
			}
			Warband warband = WarbandManager.getByString(warbandId);
			if (warband != null && !warband.isFaction()) {
				WarbandManager.deleteWarband(warband);
				DATABASE.deleteWarbandFile(warbandId);
			}
		}
	}

	private static Set<String> warbandIdsOnBattle(Battle battle) {
		Set<String> ids = new LinkedHashSet<>();
		if (battle == null) {
			return ids;
		}
		for (BattleSide side : battle.getSides()) {
			for (Warband warband : side.getBands()) {
				if (warband != null) {
					ids.add(warband.getId());
				}
			}
		}
		return ids;
	}

	private static void linkWarbands(Battle battle, BattleData data) {
		if (battle == null || data == null || data.sides == null) {
			return;
		}
		for (BattleSideData sideData : data.sides) {
			if (sideData == null || sideData.id == null) {
				continue;
			}
			BattleSide side = battle.getSideById(sideData.id);
			if (side == null) {
				continue;
			}
			for (String warbandId : sideData.warbandIds) {
				Warband warband = WarbandManager.getByString(warbandId);
				if (warband != null) {
					side.addBand(warband);
				}
			}
		}
	}

	private static boolean isCampaignBattleValid(BattleData data) {
		if (data.warId == null) {
			return true;
		}
		War war = WarManager.getById(data.warId);
		return war != null && war.isActive();
	}

	private static Battle chooseManualBattle(List<Battle> manualBattles) {
		if (manualBattles.isEmpty()) {
			return null;
		}
		for (Battle battle : manualBattles) {
			if (battle.hasStarted()) {
				return battle;
			}
		}
		return manualBattles.get(manualBattles.size() - 1);
	}

	private static List<File> listBattleFiles() {
		List<File> files = new ArrayList<>();
		File folder = new File("plugins/SimpleFactions/Battles");
		if (!folder.exists() || !folder.isDirectory()) {
			return files;
		}
		File[] listed = folder.listFiles();
		if (listed == null) {
			return files;
		}
		for (File file : listed) {
			if (file.getName().endsWith(".json")) {
				files.add(file);
			}
		}
		return files;
	}

	private static void normalizeCapturePointsAfterLoad(Battle battle) {
		if (battle != null && battle.isSequentialCapture() && !battle.getPoints().isEmpty()) {
			BattleCapturePoints.syncLinearChain(battle);
		}
	}

	private static void pruneStaleFiles(Set<String> battleIds, Set<String> warbandIds) {
		File battleFolder = new File("plugins/SimpleFactions/Battles");
		if (battleFolder.exists() && battleFolder.isDirectory()) {
			for (File file : Objects.requireNonNullElse(battleFolder.listFiles(), new File[0])) {
				if (!file.getName().endsWith(".json")) {
					continue;
				}
				String id = file.getName().substring("battle_".length(), file.getName().length() - ".json".length());
				if (!battleIds.contains(id)) {
					file.delete();
				}
			}
		}
		File warbandFolder = new File("plugins/SimpleFactions/Warbands");
		if (warbandFolder.exists() && warbandFolder.isDirectory()) {
			for (File file : Objects.requireNonNullElse(warbandFolder.listFiles(), new File[0])) {
				if (!file.getName().endsWith(".json")) {
					continue;
				}
				String id = file.getName().substring("warband_".length(), file.getName().length() - ".json".length());
				if (!warbandIds.contains(id)) {
					file.delete();
				}
			}
		}
	}
}
