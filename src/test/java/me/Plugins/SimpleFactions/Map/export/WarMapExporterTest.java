package me.Plugins.SimpleFactions.Map.export;


import me.Plugins.SimpleFactions.War.battle.engine.core.Battle;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.War.enums.CampaignBattleKind;
import me.Plugins.SimpleFactions.War.enums.WarGoalType;
import me.Plugins.SimpleFactions.War.enums.WarType;
import me.Plugins.SimpleFactions.War.campaign.progression.CampaignCoalitionService.CampaignCoalition;
import me.Plugins.SimpleFactions.War.campaign.ui.CampaignPushTarget;
import me.Plugins.SimpleFactions.War.campaign.schedule.CampaignScheduleService.ScheduleLeg;
import me.Plugins.SimpleFactions.War.campaign.schedule.ScheduledCampaignBattle;
import me.Plugins.SimpleFactions.settlement.Settlement;
import me.Plugins.SimpleFactions.settlement.handler.SettlementHandler;

class WarMapExporterTest {
	private Faction attacker;
	private Faction defender;

	@BeforeEach
	void setUp() {
		attacker = mock(Faction.class);
		defender = mock(Faction.class);
		when(attacker.getId()).thenReturn("atk");
		when(defender.getId()).thenReturn("def");
		when(attacker.getName()).thenReturn("Attacker");
		when(defender.getName()).thenReturn("Defender");
		when(attacker.getCapital()).thenReturn(5);
		when(defender.getCapital()).thenReturn(30);
	}

	@Test
	void exportWars_noWars_returnsEmptyArray() {
		JsonArray wars = WarMapExporter.exportWars(List.of());

		assertEquals(0, wars.size());
	}

	@Test
	void exportWars_campaignWar_emitsSnakeCaseFields() {
		War war = campaignWar();
		war.setCampaignBattleSchedule(List.of(
				new ScheduledCampaignBattle(20, CampaignBattleKind.FIELD, false, null),
				new ScheduledCampaignBattle(18, CampaignBattleKind.SIEGE, false, "Greenfort")));
		war.setCampaignCounterSchedule(List.of(
				new ScheduledCampaignBattle(10, CampaignBattleKind.FIELD, false, null)));
		war.setCampaignScheduleIndex(0);
		war.setCampaignCounterScheduleIndex(0);
		war.setPushTarget(CampaignPushTarget.TOWARD_OBJECTIVE);
		war.setInitiativeHolderCoalition(CampaignCoalition.AGGRESSOR);

		JsonArray wars = WarMapExporter.exportWars(List.of(war));
		assertEquals(1, wars.size());

		JsonObject row = wars.get(0).getAsJsonObject();
		assertEquals("1", row.get("id").getAsString());
		assertTrue(row.has("war_type"));
		assertTrue(row.has("campaign_battle_schedule"));
		assertTrue(row.has("campaign_counter_schedule"));
		assertEquals("toward_objective", row.get("push_target").getAsString());
		assertTrue(row.has("occupied_by_attacker"));
		assertEquals(0, row.getAsJsonArray("occupied_by_attacker").size());
		assertEquals(0, row.getAsJsonArray("occupied_by_defender").size());
		assertFalse(row.has("initiative_attacker"));

		JsonObject invasionSlot = row.getAsJsonArray("campaign_battle_schedule").get(0).getAsJsonObject();
		assertEquals("invasion", invasionSlot.get("leg").getAsString());
		assertEquals("Field Battle", invasionSlot.get("kind_label").getAsString());
		assertEquals("next", invasionSlot.get("status").getAsString());

		JsonObject siegeSlot = row.getAsJsonArray("campaign_battle_schedule").get(1).getAsJsonObject();
		assertEquals("Siege", siegeSlot.get("kind_label").getAsString());
		assertEquals("upcoming", siegeSlot.get("status").getAsString());
		assertEquals("Greenfort", siegeSlot.get("fort_installation_id").getAsString());
	}

	@Test
	void exportWars_counterPush_marksCounterSlotNext() {
		War war = campaignWar();
		war.setCampaignBattleSchedule(List.of(
				new ScheduledCampaignBattle(20, CampaignBattleKind.FIELD, false, null),
				new ScheduledCampaignBattle(30, CampaignBattleKind.FIELD, true, null)));
		war.setCampaignCounterSchedule(List.of(
				new ScheduledCampaignBattle(10, CampaignBattleKind.FIELD, false, null),
				new ScheduledCampaignBattle(5, CampaignBattleKind.FIELD, true, null)));
		war.setCampaignScheduleIndex(1);
		war.setCampaignCounterScheduleIndex(1);
		war.setPushTarget(CampaignPushTarget.TOWARD_AGGRESSOR_CAPITAL);
		war.setInitiativeHolderCoalition(CampaignCoalition.DEFENDER);

		JsonObject row = WarMapExporter.exportWars(List.of(war)).get(0).getAsJsonObject();

		JsonObject invasionFought = row.getAsJsonArray("campaign_battle_schedule").get(0).getAsJsonObject();
		JsonObject invasionUpcoming = row.getAsJsonArray("campaign_battle_schedule").get(1).getAsJsonObject();
		JsonObject counterFought = row.getAsJsonArray("campaign_counter_schedule").get(0).getAsJsonObject();
		JsonObject counterNext = row.getAsJsonArray("campaign_counter_schedule").get(1).getAsJsonObject();

		assertEquals("fought", invasionFought.get("status").getAsString());
		assertEquals("upcoming", invasionUpcoming.get("status").getAsString());
		assertEquals("fought", counterFought.get("status").getAsString());
		assertEquals("next", counterNext.get("status").getAsString());
		assertEquals("toward_aggressor_capital", row.get("push_target").getAsString());
	}

	@Test
	void exportWars_raidWar_excluded() {
		War war = campaignWar();
		war.setWarType(WarType.RAID);

		assertEquals(0, WarMapExporter.exportWars(List.of(war)).size());
		assertFalse(WarMapExporter.shouldExport(war));
	}

	@Test
	void exportWars_emptyAxis_excluded() {
		War war = new War(2, attacker, defender);
		war.setGoal(WarGoalType.SUBJUGATE);
		war.setCampaignProvinces(List.of());

		assertEquals(0, WarMapExporter.exportWars(List.of(war)).size());
		assertFalse(WarMapExporter.shouldExport(war));

		war.setCampaignProvinces(null);
		assertFalse(WarMapExporter.shouldExport(war));
	}

	@Test
	void exportCapitalCoords_includesSettlementBlockCoords() {
		SettlementHandler handler = mock(SettlementHandler.class);
		when(attacker.getSettlementHandler()).thenReturn(handler);
		when(handler.getByProvince(5)).thenReturn(new Settlement("cap", "Capital", 5, 100, 200));

		JsonObject capital = WarMapExporter.exportCapitalCoords(attacker);

		assertNotNull(capital);
		assertEquals(5, capital.get("province_id").getAsInt());
		assertEquals(100, capital.get("center_x").getAsInt());
		assertEquals(200, capital.get("center_z").getAsInt());
	}

	@Test
	void exportCapitalCoords_withoutSettlement_exportsProvinceOnly() {
		SettlementHandler handler = mock(SettlementHandler.class);
		when(defender.getSettlementHandler()).thenReturn(handler);
		when(handler.getByProvince(30)).thenReturn(null);

		JsonObject capital = WarMapExporter.exportCapitalCoords(defender);

		assertNotNull(capital);
		assertEquals(30, capital.get("province_id").getAsInt());
		assertFalse(capital.has("center_x"));
		assertFalse(capital.has("center_z"));
	}

	@Test
	void resolveSlotStatus_matchesActiveLeg() {
		assertEquals("fought", WarMapExporter.resolveSlotStatus(0, 1, ScheduleLeg.INVASION, ScheduleLeg.INVASION));
		assertEquals("next", WarMapExporter.resolveSlotStatus(1, 1, ScheduleLeg.INVASION, ScheduleLeg.INVASION));
		assertEquals("upcoming", WarMapExporter.resolveSlotStatus(2, 1, ScheduleLeg.INVASION, ScheduleLeg.INVASION));
		assertEquals("upcoming", WarMapExporter.resolveSlotStatus(1, 1, ScheduleLeg.INVASION, ScheduleLeg.COUNTER));
	}

	@Test
	void exportWar_includesBelligerentLeaderIds() {
		JsonObject row = WarMapExporter.exportWar(campaignWar());
		JsonArray belligerents = row.getAsJsonArray("belligerents");

		assertTrue(belligerents.contains(com.google.gson.JsonParser.parseString("\"atk\"")));
		assertTrue(belligerents.contains(com.google.gson.JsonParser.parseString("\"def\"")));
	}

	@Test
	void exportWar_brumeShaped_counterSchedule() {
		War war = campaignWar();
		war.setCampaignProvinces(List.of(452, 782, 758, 757, 672, 709, 713, 705));
		war.setObjectiveProvinceId(705);
		war.setCampaignBattleSchedule(List.of(
				new ScheduledCampaignBattle(795, CampaignBattleKind.NAVAL, false, null, "port"),
				new ScheduledCampaignBattle(705, CampaignBattleKind.SIEGE, false, "Greenfort"),
				new ScheduledCampaignBattle(705, CampaignBattleKind.FIELD, true, null)));
		war.setCampaignCounterSchedule(List.of(
				new ScheduledCampaignBattle(672, CampaignBattleKind.FIELD, false, null),
				new ScheduledCampaignBattle(782, CampaignBattleKind.FIELD, false, null),
				new ScheduledCampaignBattle(452, CampaignBattleKind.FIELD, true, null)));

		JsonObject row = WarMapExporter.exportWar(war);

		assertTrue(row.has("campaign_counter_schedule"));
		JsonArray counter = row.getAsJsonArray("campaign_counter_schedule");
		assertTrue(counter.size() >= 2);
		assertEquals(672, counter.get(0).getAsJsonObject().get("province_id").getAsInt());
		assertEquals(782, counter.get(1).getAsJsonObject().get("province_id").getAsInt());
		assertEquals(3, row.getAsJsonArray("campaign_battle_schedule").size());
	}

	@Test
	void exportWar_includesDisplayNameOnSlots() {
		War war = campaignWar();
		war.setCampaignBattleSchedule(List.of(
				new ScheduledCampaignBattle(20, CampaignBattleKind.FIELD, false, null),
				new ScheduledCampaignBattle(20, CampaignBattleKind.FIELD, true, null),
				new ScheduledCampaignBattle(18, CampaignBattleKind.SIEGE, false, "Greenfort")));

		JsonObject row = WarMapExporter.exportWar(war);
		JsonArray schedule = row.getAsJsonArray("campaign_battle_schedule");

		assertEquals("Battle of Wilderness", schedule.get(0).getAsJsonObject().get("display_name").getAsString());
		assertEquals(
				"Second Battle of Wilderness",
				schedule.get(1).getAsJsonObject().get("display_name").getAsString());
		assertEquals(
				"Siege of Greenfort",
				schedule.get(2).getAsJsonObject().get("display_name").getAsString());
	}

	@Test
	void exportWar_includesOccupationProvinceLists() {
		War war = campaignWar();
		war.setOccupiedByAttacker(List.of(20, 18));
		war.setOccupiedByDefender(List.of(10));

		JsonObject row = WarMapExporter.exportWar(war);

		assertEquals(20, row.getAsJsonArray("occupied_by_attacker").get(0).getAsInt());
		assertEquals(18, row.getAsJsonArray("occupied_by_attacker").get(1).getAsInt());
		assertEquals(10, row.getAsJsonArray("occupied_by_defender").get(0).getAsInt());
	}

	@Test
	void exportCapitalCoords_nullLeader_returnsNull() {
		assertNull(WarMapExporter.exportCapitalCoords(null));
	}

	private War campaignWar() {
		War war = new War(1, attacker, defender);
		war.setGoal(WarGoalType.SUBJUGATE);
		war.setWarType(WarType.SUBJUGATE);
		war.setObjectiveProvinceId(30);
		war.setCampaignProvinces(List.of(5, 10, 20, 30));
		war.setCursorIndex(2);
		return war;
	}
}
