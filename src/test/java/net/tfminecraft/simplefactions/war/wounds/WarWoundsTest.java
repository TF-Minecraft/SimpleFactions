package net.tfminecraft.simplefactions.war.wounds;

import static net.tfminecraft.simplefactions.war.wounds.TestForces.faction;
import static net.tfminecraft.simplefactions.war.wounds.TestForces.mercenary;
import static net.tfminecraft.simplefactions.war.wounds.TestForces.militia;
import static net.tfminecraft.simplefactions.war.wounds.TestForces.paid;
import static net.tfminecraft.simplefactions.war.wounds.WoundFixture.regiment;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.tfminecraft.simplefactions.army.Regiment;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.mercenary.company.MercenaryCompany;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.war.battle.military.BattleCasualtyTally;
import net.tfminecraft.simplefactions.war.campaign.runtime.CampaignClock;
import net.tfminecraft.simplefactions.war.core.War;

class WarWoundsTest {
	private WoundFixture fixture;

	@BeforeEach
	void setUp() {
		fixture = new WoundFixture();
	}

	@AfterEach
	void tearDown() {
		fixture.close();
	}

	@Test
	void woundedSlotsAddUpOverActiveWarsAndNeverExceedPaidSlots() {
		Regiment professional = regiment("professional", 10, 4);
		Faction atk = fixture.faction("atk", professional);
		War first = fixture.war(1, atk, fixture.faction("def"));
		War second = fixture.war(2, atk, fixture.faction("other"));
		first.getLedger().addAdjustment(adjustment("atk", "professional", 12));
		second.getLedger().addAdjustment(adjustment("ATK", "PROFESSIONAL", 9));

		assertEquals(first.getId(), first.getWarId());
		assertEquals(21, WarWounds.woundLives(atk, professional));
		assertEquals(4, WarWounds.woundedSlots(atk, professional));
		assertEquals(2, WarWounds.availablePaidSlots(atk, professional));
		assertEquals(6, WarWounds.availableSlots(atk, professional));

		second.getLedger().addAdjustment(adjustment("atk", "professional", 50));
		assertEquals(6, WarWounds.woundedSlots(atk, professional), "never more than the 6 paid slots");
		second.getLedger().markSettled();
		assertEquals(2, WarWounds.woundedSlots(atk, professional), "a settled war's wounds are already applied");
	}

	@Test
	void onlySoldierRegimentsCarryWounds() {
		Regiment levy = regiment("levy", 5, 0);
		when(levy.isLevy()).thenReturn(true);
		Regiment artillery = regiment("artillery", 5, 0);
		when(artillery.isEquipment()).thenReturn(true);
		Regiment company = regiment("mercenary", 5, 0);
		when(company.isMercenary()).thenReturn(true);
		Faction atk = fixture.faction("atk", levy, artillery, company);
		War war = fixture.war(3, atk, fixture.faction("def"));
		for (String id : List.of("levy", "artillery", "mercenary")) {
			war.getLedger().addAdjustment(adjustment("atk", id, 25));
		}

		assertEquals(0, WarWounds.woundLives(atk, levy));
		assertEquals(0, WarWounds.woundedSlots(atk, artillery));
		assertEquals(0, WarWounds.woundedSlots(atk, company));
		assertEquals(0, WarWounds.woundLives(null, levy));
		assertEquals(0, WarWounds.woundLives(atk, null));
		assertEquals(0, WarWounds.woundedSlots(null, levy));
		assertEquals(0, WarWounds.woundedSlots(atk, null));
		assertEquals(0, WarWounds.availablePaidSlots(atk, null));
		assertEquals(0, WarWounds.availableSlots(atk, null));
	}

	@Test
	void recordingABattleTalliesNewlyWoundedSlotsAndWoundsTheCompany() {
		Faction atk = fixture.faction("atk", regiment("militia", 10, 6));
		Faction def = fixture.faction("def", regiment("professional", 5, 0));
		War war = fixture.war(4, atk, def);
		MercenaryCompany company = company("guild", 4, fixture.faction("host"));

		BattleCasualtyTally tally = WarWounds.recordBattle(war, "b1", "fought", 7,
				new WarLedger.SideRecord(30, List.of(faction("atk", militia(6, 4)), mercenary("guild", 2))),
				new WarLedger.SideRecord(14, List.of(faction("def", paid("professional", 5)),
						faction("gone", paid("professional", 1)))));

		assertEquals(List.of("atk attacker militia 2", "def defender professional 2"), lines(tally));
		assertEquals(5, company.getWounds().storedLives());
		assertEquals(1, war.getLedger().battles().size());
		assertEquals("b1", war.getLedger().battle(1).battleId());

		// A second battle only tallies what it adds: 10 more paid lives on militia is 2 more slots.
		BattleCasualtyTally second = WarWounds.recordBattle(war, "b2", "fought", 7,
				new WarLedger.SideRecord(25, List.of(faction("atk", militia(6, 4)))),
				new WarLedger.SideRecord(1, List.of(faction("def", paid("professional", 5)))));
		assertEquals(List.of("atk attacker militia 2"), lines(second));
	}

	@Test
	void editingDeathsMovesWoundsBothWaysAndTellsStaffWhatChanged() {
		Faction atk = fixture.faction("atk", regiment("professional", 8, 0));
		War war = fixture.war(5, atk, fixture.faction("def"));
		MercenaryCompany company = company("guild", 4, null);
		WarWounds.recordBattle(war, "b1", "fought", 7,
				new WarLedger.SideRecord(20, List.of(faction("atk", paid("professional", 6)), mercenary("guild", 2))),
				new WarLedger.SideRecord(0, List.of()));
		assertEquals(5, company.getWounds().storedLives());

		WarWounds.Result raised = WarWounds.editDeaths(war, 1, WarLedger.ATTACKER, "add", 20, "staff", "glitch");
		assertTrue(raised.ok());
		assertEquals(List.of("§aWar 5: battle 1 attacker deaths 20 -> 40.", "§7atk professional: 3 -> 6 wounded slots",
				"§7Company guild: +5 wounded lives"), raised.lines());
		assertEquals(10, company.getWounds().storedLives());
		assertEquals("battle 1 attacker deaths 20 -> 40 (glitch)", war.getLedger().notes().get(0).text());

		WarWounds.Result lowered = WarWounds.editDeaths(war, 1, WarLedger.ATTACKER, "remove", 100, "staff", null);
		assertEquals("§aWar 5: battle 1 attacker deaths 40 -> 0.", lowered.lines().get(0));
		assertEquals(0, company.getWounds().storedLives());
		assertEquals(0, war.getLedger().factionWoundLives("atk", "professional"));

		WarWounds.Result same = WarWounds.editDeaths(war, 1, WarLedger.DEFENDER, "set", 0, "staff", null);
		assertEquals(List.of("§aWar 5: battle 1 defender deaths 0 -> 0.", "§7No wounded slots changed."), same.lines());
	}

	@Test
	void editsAreRefusedWhenTheyCannotApply() {
		War war = fixture.war(6, fixture.faction("atk"), fixture.faction("def"));
		WarWounds.recordBattle(war, "b1", "fought", 7, new WarLedger.SideRecord(0, List.of()),
				new WarLedger.SideRecord(0, List.of()));

		assertFalse(WarWounds.editDeaths(war, 2, WarLedger.ATTACKER, "set", 1, "s", null).ok());
		assertEquals(List.of("§cThe amount must be 0 or more."),
				WarWounds.editDeaths(war, 1, WarLedger.ATTACKER, "set", -1, "s", null).lines());
		assertEquals(List.of("§cUse set, add or remove."),
				WarWounds.editDeaths(war, 1, WarLedger.ATTACKER, "double", 1, "s", null).lines());
		war.getLedger().markSettled();
		assertEquals(List.of("§cThat war's wounds are already settled."),
				WarWounds.editDeaths(war, 1, WarLedger.ATTACKER, "set", 1, "s", null).lines());
		Faction atk = fixture.factions.get("atk");
		assertFalse(WarWounds.adjust(war, atk, regiment("p", 1, 0), 5, "s", null).ok());
	}

	@Test
	void staffCanWoundOrHealARegimentDirectly() {
		Regiment professional = regiment("professional", 8, 0);
		Faction atk = fixture.faction("atk", professional);
		War war = fixture.war(7, atk, fixture.faction("def"));

		WarWounds.Result added = WarWounds.adjust(war, atk, professional, 10, "staff", "bug");
		assertEquals(List.of("§aWar 7: added 10 wounded lives on atk professional.", "§7atk professional: 0 -> 2 wounded slots"),
				added.lines());
		WarWounds.Result removed = WarWounds.adjust(war, atk, professional, -10, "staff", null);
		assertEquals("§aWar 7: removed 10 wounded lives on atk professional.", removed.lines().get(0));
		assertEquals(0, WarWounds.woundedSlots(atk, professional));
	}

	@Test
	void settlementTakesWoundedPaidSlotsOnceAndNeverLawSlots() {
		Regiment militia = regiment("militia", 10, 6);
		Regiment professional = regiment("professional", 3, 0);
		Faction atk = fixture.faction("atk", militia, professional);
		Faction untouched = fixture.faction("calm", regiment("professional", 3, 0));
		War war = fixture.war(8, atk, fixture.faction("def"));
		war.getLedger().addAdjustment(adjustment("atk", "militia", 100));
		war.getLedger().addAdjustment(adjustment("atk", "professional", 9));
		war.getLedger().addAdjustment(adjustment("atk", "missing", 10));
		war.getLedger().addAdjustment(adjustment("calm", "professional", 4));
		war.getLedger().addAdjustment(adjustment("ghost", "professional", 10));

		assertEquals(List.of("atk militia: 100 lives = 20 slots, loses 4", "atk professional: 9 lives = 1 slots, loses 1",
				"atk missing: 10 lives = 2 slots, loses 0", "calm professional: 4 lives = 0 slots, loses 0",
				"ghost professional: 10 lives = 2 slots, loses 0"), WarWounds.settlementPreview(war));

		List<String> lost = WarWounds.settle(war);

		assertEquals(List.of("atk militia -4", "atk professional -1"), lost);
		assertEquals(6, militia.getCurrentSlots(), "the 6 law slots stay");
		assertEquals(2, professional.getCurrentSlots());
		assertEquals(3, untouched.getMilitary().getRegiment("professional").getCurrentSlots());
		assertTrue(war.getLedger().isSettled());
		assertTrue(WarWounds.settle(war).isEmpty(), "a war is settled once");
		assertEquals(6, militia.getCurrentSlots());
	}

	@Test
	void settlementSkipsFactionsWithoutAMilitary() {
		Faction bare = mock(Faction.class);
		when(bare.getId()).thenReturn("bare");
		fixture.factions.put("bare", bare);
		War war = fixture.war(9, fixture.faction("atk"), fixture.faction("def"));
		war.getLedger().addAdjustment(adjustment("bare", "professional", 10));

		assertTrue(WarWounds.settle(war).isEmpty());
		assertEquals(List.of("bare professional: 10 lives = 2 slots, loses 0"), WarWounds.settlementPreview(war));
	}

	@Test
	void reportedSlotsStopAtWhatTheRegimentCanLose() {
		fixture.faction("atk", regiment("professional", 2, 0));
		assertEquals(2, WarWounds.slots("atk", "professional", 50));
		assertEquals(10, WarWounds.slots("atk", "nothing", 50));
		assertEquals(10, WarWounds.slots("nobody", "professional", 50));
		Faction bare = mock(Faction.class);
		fixture.factions.put("bare", bare);
		assertEquals(10, WarWounds.slots("bare", "professional", 50));
	}

	@Test
	void staffCanSetACompanysWounds() {
		Faction host = fixture.faction("host");
		MercenaryCompany company = company("guild", 4, host);
		assertEquals(company, WarWounds.company("guild"));
		assertNull(WarWounds.company("nope"));
		assertNull(WarWounds.company(null));
		Guild empty = mock(Guild.class);
		fixture.guilds.put("empty", empty);
		assertNull(WarWounds.company("empty"));

		WarWounds.Result result = WarWounds.setCompanyWounds(company, 12, "staff");

		assertEquals(List.of("§aCompany Company guild: wounded lives 0 -> 12 (2 of 4 slots wounded)."), result.lines());
		assertEquals(12, company.getWounds().lives(CampaignClock.now(), 4));
	}

	@Test
	void anUnsetDeathRateCountsFive() {
		int saved = net.tfminecraft.simplefactions.Cache.warBattleDeathsPerRegimentLoss;
		net.tfminecraft.simplefactions.Cache.warBattleDeathsPerRegimentLoss = 0;
		assertEquals(5, WarWounds.deathsPerSlot());
		net.tfminecraft.simplefactions.Cache.warBattleDeathsPerRegimentLoss = saved;
	}

	private MercenaryCompany company(String guildId, int slots, Faction host) {
		MercenaryCompany company = mock(MercenaryCompany.class);
		Guild guild = mock(Guild.class);
		when(guild.getId()).thenReturn(guildId);
		when(guild.getCompany()).thenReturn(company);
		when(guild.getFaction()).thenReturn(host);
		when(company.getGuild()).thenReturn(guild);
		when(company.getName()).thenReturn("Company " + guildId);
		when(company.getSlots()).thenReturn(slots);
		CompanyWounds wounds = new CompanyWounds();
		when(company.getWounds()).thenReturn(wounds);
		fixture.guilds.put(guildId, guild);
		return company;
	}

	private static WarLedger.Adjustment adjustment(String faction, String regiment, int lives) {
		return new WarLedger.Adjustment(faction, regiment, lives, "staff", null, "t");
	}

	private static List<String> lines(BattleCasualtyTally tally) {
		return tally.losses().stream()
				.map(loss -> loss.faction().getId() + " " + loss.side() + " " + loss.regimentId() + " " + loss.count())
				.toList();
	}
}
