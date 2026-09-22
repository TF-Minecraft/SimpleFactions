package me.Plugins.SimpleFactions.War.battle.campaign;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import me.Plugins.SimpleFactions.Tiers.Title;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.War.battle.engine.core.Battle;
import me.Plugins.SimpleFactions.War.battle.enums.BattleType;
import me.Plugins.SimpleFactions.War.enums.CampaignBattleKind;
import me.Plugins.SimpleFactions.War.campaign.schedule.CampaignScheduleService.ScheduleLeg;
import me.Plugins.SimpleFactions.War.campaign.schedule.ScheduledCampaignBattle;
import me.Plugins.SimpleFactions.settlement.Settlement;

class BattleNamingServiceTest {
	@Test
	void slugifyDisplayName_normalizesText() {
		assertEquals("battle_of_lanbury", BattleNamingService.slugifyDisplayName("Battle of Lanbury"));
		assertEquals("second_harbor_raid", BattleNamingService.slugifyDisplayName("Second Harbor Raid"));
		assertEquals("st_marys_harbor", BattleNamingService.slugifyDisplayName("St. Mary's Harbor"));
		assertEquals("wilderness", BattleNamingService.slugifyDisplayName("   "));
	}

	@Test
	void slugifyDisplayName_stripsColorCodes() {
		assertEquals("battle_of_lanbury", BattleNamingService.slugifyDisplayName("§aBattle of Lanbury"));
		assertEquals("battle_of_lanbury", BattleNamingService.slugifyDisplayName("&#a3a184Battle of Lanbury"));
		assertEquals("second_harbor_raid", BattleNamingService.slugifyDisplayName("§l§eSecond Harbor Raid"));
		assertEquals("harbor_raid", BattleNamingService.slugifyDisplayName("&#a3a184§lHarbor Raid"));
	}

	@Test
	void campaignWarbandId_stripsColorCodesFromDisplayName() {
		assertEquals(
				"battle_of_lanbury_attacker",
				BattleNamingService.campaignWarbandId("§aBattle of Lanbury", "attacker"));
		assertEquals(
				"second_harbor_raid_defender",
				BattleNamingService.campaignWarbandId("&#a3a184§lSecond Harbor Raid", "defender"));
	}

	@Test
	void slugifyDisplayName_capsLength() {
		String longName = "A".repeat(80);
		assertEquals(48, BattleNamingService.slugifyDisplayName(longName).length());
	}

	@Test
	void campaignWarbandId_usesDisplaySlugAndSide() {
		assertEquals(
				"battle_of_lanbury_attacker",
				BattleNamingService.campaignWarbandId("Battle of Lanbury", "attacker"));
		assertEquals(
				"battle_of_lanbury_defender",
				BattleNamingService.campaignWarbandId("Battle of Lanbury", "defender"));
	}

	@Test
	void buildRaidDisplayName_usesInstallationOrdinal() {
		War war = new War(1, mock(Faction.class), mock(Faction.class));
		me.Plugins.SimpleFactions.installation.Installation target =
				mock(me.Plugins.SimpleFactions.installation.Installation.class);
		when(target.getId()).thenReturn("harbor-1");
		when(target.getName()).thenReturn("Harbor");

		assertEquals("Harbor Raid", BattleNamingService.buildRaidDisplayName(war, target));

		war.recordLocationBattle(BattleNamingService.raidLocationKey(target));
		assertEquals("Second Harbor Raid", BattleNamingService.buildRaidDisplayName(war, target));
	}

	@Test
	void buildDisplayName_fieldOrdinals() {
		assertEquals("Battle of Lanbury", BattleNamingService.buildDisplayName(
				BattleType.FIELD, "Lanbury", 1));
		assertEquals("Second Battle of Lanbury", BattleNamingService.buildDisplayName(
				BattleType.FIELD, "Lanbury", 2));
	}

	@Test
	void buildDisplayName_nonFieldVariants() {
		record Case(BattleType type, String location, int ordinal, String expected) {}
		Case[] cases = {
				new Case(BattleType.SIEGE, "Fort Redoubt", 1, "Siege of Fort Redoubt"),
				new Case(BattleType.RAID, "Lanbury", 2, "Second Lanbury Raid"),
				new Case(BattleType.FIELD, BattleNamingService.WILDERNESS, 1, "Battle of Wilderness"),
		};
		for (Case c : cases) {
			assertEquals(c.expected, BattleNamingService.buildDisplayName(c.type, c.location, c.ordinal));
		}
	}

	@Test
	void ordinalPrefix_values() {
		assertEquals("", BattleNamingService.ordinalPrefix(1));
		assertEquals("Second ", BattleNamingService.ordinalPrefix(2));
		assertEquals("Third ", BattleNamingService.ordinalPrefix(3));
		assertEquals("4th ", BattleNamingService.ordinalPrefix(4));
	}

	@Test
	void resolveLocation_usesCountyTitleWhenExactProvinceMisses() {
		Settlement city = mock(Settlement.class);
		when(city.getName()).thenReturn("Lanbury");
		when(city.getCenterProvince()).thenReturn(41);

		Title county = mock(Title.class);
		when(county.isComposite()).thenReturn(false);
		when(county.getProvinces()).thenReturn(List.of(41, 44));
		when(county.getName()).thenReturn("county_44");
		when(county.getId()).thenReturn("county_44");

		me.Plugins.SimpleFactions.settlement.handler.SettlementHandler handler =
				mock(me.Plugins.SimpleFactions.settlement.handler.SettlementHandler.class);
		when(handler.getByProvince(44)).thenReturn(null);
		when(handler.getAll()).thenReturn(List.of(city));

		Faction faction = mock(Faction.class);
		when(faction.getSettlementHandler()).thenReturn(handler);
		when(faction.getInstallationHandler()).thenReturn(null);

		try (org.mockito.MockedStatic<me.Plugins.SimpleFactions.Managers.FactionManager> factions =
						org.mockito.Mockito.mockStatic(me.Plugins.SimpleFactions.Managers.FactionManager.class);
				org.mockito.MockedStatic<me.Plugins.SimpleFactions.Loaders.TitleLoader> titles =
						org.mockito.Mockito.mockStatic(me.Plugins.SimpleFactions.Loaders.TitleLoader.class)) {
			factions.when(me.Plugins.SimpleFactions.Managers.FactionManager::getCopy).thenReturn(List.of(faction));
			titles.when(() -> me.Plugins.SimpleFactions.Loaders.TitleLoader.getByProvince(44)).thenReturn(county);

			BattleNamingService.LocationInfo info = BattleNamingService.resolveLocation(44);
			assertEquals("county_44", info.displayName());
			assertEquals("county:county_44", info.key());
		}
	}

	@Test
	void applyCampaignName_usesWarLocationCount() {
		War war = new War(1, mock(Faction.class), mock(Faction.class));
		war.recordLocationBattle("settlement:Lanbury");

		Battle battle = new Battle("campaign_w1_p100");
		BattleNamingService.applyCampaignName(battle, war, 100, BattleType.FIELD);

		// Ordinal derived from war count; location resolution needs live world data in integration.
		// With no settlement data, falls back to wilderness key.
		assertEquals("Battle of Wilderness", battle.getDisplayName());
	}

	@Test
	void recordLocationBattle_incrementsWarCounter() {
		War war = new War(1, mock(Faction.class), mock(Faction.class));
		assertEquals(0, war.getLocationBattleCount("county:Shire"));

		BattleNamingService.recordLocationBattle(war, 999);
		String key = BattleNamingService.resolveLocationKey(999);
		assertEquals(1, war.getLocationBattleCount(key));
	}

	@Test
	void resolveScheduledOrdinal_twoFieldSlotsSameProvince() {
		War war = new War(1, mock(Faction.class), mock(Faction.class));
		ScheduledCampaignBattle first = new ScheduledCampaignBattle(100, CampaignBattleKind.FIELD, false, null);
		ScheduledCampaignBattle second = new ScheduledCampaignBattle(100, CampaignBattleKind.FIELD, true, null);
		war.setCampaignBattleSchedule(List.of(first, second));

		assertEquals(1, BattleNamingService.resolveScheduledOrdinal(war, ScheduleLeg.INVASION, 0, first));
		assertEquals(2, BattleNamingService.resolveScheduledOrdinal(war, ScheduleLeg.INVASION, 1, second));
	}

	@Test
	void resolveScheduledOrdinal_respectsFoughtCount() {
		War war = new War(1, mock(Faction.class), mock(Faction.class));
		war.recordLocationBattle("wilderness:100");
		ScheduledCampaignBattle slot = new ScheduledCampaignBattle(100, CampaignBattleKind.FIELD, false, null);
		war.setCampaignBattleSchedule(List.of(slot));

		assertEquals(2, BattleNamingService.resolveScheduledOrdinal(war, ScheduleLeg.INVASION, 0, slot));
	}

	@Test
	void resolveScheduledDisplayName_siegeThenFieldAtCapital() {
		War war = new War(1, mock(Faction.class), mock(Faction.class));
		ScheduledCampaignBattle naval = new ScheduledCampaignBattle(
				795, CampaignBattleKind.NAVAL, false, null, "port");
		ScheduledCampaignBattle siege = new ScheduledCampaignBattle(705, CampaignBattleKind.SIEGE, false, "Greenfort");
		ScheduledCampaignBattle field = new ScheduledCampaignBattle(705, CampaignBattleKind.FIELD, true, null);
		war.setCampaignBattleSchedule(List.of(naval, siege, field));

		assertEquals(
				"Siege of Greenfort",
				BattleNamingService.resolveScheduledDisplayName(
						war, ScheduleLeg.INVASION, 1, siege, 705));
		String fieldName = BattleNamingService.resolveScheduledDisplayName(
				war, ScheduleLeg.INVASION, 2, field, 705);
		assertTrue(fieldName.startsWith("Battle of "));
		assertEquals(1, BattleNamingService.resolveScheduledOrdinal(war, ScheduleLeg.INVASION, 1, siege));
		assertEquals(1, BattleNamingService.resolveScheduledOrdinal(war, ScheduleLeg.INVASION, 2, field));
	}

	@Test
	void applyCampaignName_siegeUsesFortKey() {
		War war = new War(1, mock(Faction.class), mock(Faction.class));
		ScheduledCampaignBattle siege = new ScheduledCampaignBattle(705, CampaignBattleKind.SIEGE, false, "Greenfort");
		Battle battle = new Battle("campaign_w1_p705");

		BattleNamingService.applyCampaignName(battle, war, 705, BattleType.SIEGE, siege);

		assertEquals("Siege of Greenfort", battle.getDisplayName());
	}
}
