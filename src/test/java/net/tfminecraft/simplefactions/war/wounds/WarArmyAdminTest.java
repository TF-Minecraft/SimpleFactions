package net.tfminecraft.simplefactions.war.wounds;

import static net.tfminecraft.simplefactions.war.wounds.TestForces.faction;
import static net.tfminecraft.simplefactions.war.wounds.TestForces.mercenary;
import static net.tfminecraft.simplefactions.war.wounds.TestForces.paid;
import static net.tfminecraft.simplefactions.war.wounds.WoundFixture.regiment;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
import net.tfminecraft.simplefactions.objects.handler.GuildHandler;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.war.enums.WarEndReason;

class WarArmyAdminTest {
	private WoundFixture fixture;
	private Faction atk;
	private War war;
	private MercenaryCompany company;

	@BeforeEach
	void setUp() {
		fixture = new WoundFixture();
		Regiment levy = regiment("levy", 3, 0);
		when(levy.isLevy()).thenReturn(true);
		atk = fixture.faction("atk", regiment("professional", 8, 2), levy);
		war = fixture.war(1, atk, fixture.faction("def"));
		company = mock(MercenaryCompany.class);
		Guild guild = mock(Guild.class);
		when(guild.getId()).thenReturn("guild");
		when(guild.getCompany()).thenReturn(company);
		when(company.getGuild()).thenReturn(guild);
		when(company.getName()).thenReturn("Blades");
		when(company.getSlots()).thenReturn(4);
		when(company.getWounds()).thenReturn(new CompanyWounds());
		fixture.guilds.put("guild", guild);
		GuildHandler guilds = mock(GuildHandler.class);
		Guild plain = mock(Guild.class);
		when(guilds.getGuilds()).thenReturn(List.of(guild, plain));
		when(atk.getGuildHandler()).thenReturn(guilds);
		WarWounds.recordBattle(war, "b1", "fought", 7,
				new WarLedger.SideRecord(20, List.of(faction("atk", paid("professional", 6)), mercenary("guild", 2))),
				new WarLedger.SideRecord(3, List.of()));
	}

	@AfterEach
	void tearDown() {
		fixture.close();
	}

	@Test
	void lossesListsEveryBattleAndWhatTheWarsEndWillTake() {
		war.getLedger().addAdjustment(new WarLedger.Adjustment("atk", "professional", 5, "Mod", null, "t"));

		assertEquals(List.of("§6War 1: 1 recorded battles",
				"§e#1 §7fought, province 7: attacker 20 deaths of 40 lives (10 mercenary); defender 3 deaths of 0 lives",
				"§8Adjusted atk professional by 5 lives (Mod)",
				"§6At war end:",
				"§7- atk professional: 20 lives = 4 slots, loses 4"),
				WarArmyAdmin.losses(args("losses", "1"), "Mod"));
		fixture.war(2, atk, fixture.faction("x"));
		assertEquals(List.of("§6War 2: 0 recorded battles", "§7At war end: no regiment slots are lost."),
				WarArmyAdmin.losses(args("losses", "2"), "Mod"));
	}

	@Test
	void lossesFixesABattlesDeathsWithAReason() {
		List<String> lines = WarArmyAdmin.losses(args("losses", "1", "1", "ATTACKER", "SET", "10", "a", "glitch"), "Mod");

		assertEquals("§aWar 1: battle 1 attacker deaths 20 -> 10.", lines.get(0));
		assertEquals("battle 1 attacker deaths 20 -> 10 (a glitch)", war.getLedger().notes().get(0).text());
	}

	@Test
	void lossesExplainsBadInput() {
		assertEquals(List.of(WarArmyAdmin.LOSSES_USAGE), WarArmyAdmin.losses(args("losses"), "Mod"));
		assertEquals(List.of("§cNo active war with id 9"), WarArmyAdmin.losses(args("losses", "9"), "Mod"));
		assertEquals(List.of("§cNo active war with id x"), WarArmyAdmin.losses(args("losses", "x"), "Mod"));
		assertEquals(List.of(WarArmyAdmin.LOSSES_USAGE), WarArmyAdmin.losses(args("losses", "1", "1"), "Mod"));
		assertEquals(List.of(WarArmyAdmin.LOSSES_USAGE),
				WarArmyAdmin.losses(args("losses", "1", "one", "attacker", "set", "1"), "Mod"));
		assertEquals(List.of(WarArmyAdmin.LOSSES_USAGE),
				WarArmyAdmin.losses(args("losses", "1", "1", "attacker", "set", "lots"), "Mod"));
		assertEquals(List.of(WarArmyAdmin.LOSSES_USAGE),
				WarArmyAdmin.losses(args("losses", "1", "1", "middle", "set", "1"), "Mod"));
		assertEquals(List.of("§cThe amount must be 0 or more."),
				WarArmyAdmin.losses(args("losses", "1", "1", "defender", "set", "-1"), "Mod"));
		war.end(WarEndReason.ADMIN_END);
		assertEquals(List.of("§cNo active war with id 1"), WarArmyAdmin.losses(args("losses", "1"), "Mod"));
	}

	@Test
	void armyShowsSlotsWoundsPerWarAndCompanies() {
		War settled = fixture.war(3, atk, fixture.faction("y"));
		settled.getLedger().addAdjustment(new WarLedger.Adjustment("atk", "professional", 50, "Mod", null, "t"));
		settled.getLedger().markSettled();

		assertEquals(List.of("§6ATK §7(atk) army",
				"§eprofessional§7: 8 slots (2 law, 6 paid), 3 wounded, §a5 can fight",
				"§8  war 1: 15 wounded lives",
				"§bCompany Blades §7(guild guild): 5 wounded lives, 1 of 4 slots wounded"),
				WarArmyAdmin.army(args("army", "ATK")));
		assertEquals(List.of(WarArmyAdmin.ARMY_USAGE), WarArmyAdmin.army(args("army")));
		assertEquals(List.of("§cUnknown faction: nope"), WarArmyAdmin.army(args("army", "nope")));
		Faction bare = mock(Faction.class);
		fixture.factions.put("bare", bare);
		assertEquals(List.of("§cUnknown faction: bare"), WarArmyAdmin.army(args("army", "bare")));
	}

	@Test
	void woundsAdjustsARegimentOrACompany() {
		assertEquals("§aWar 1: added 10 wounded lives on atk professional.",
				WarArmyAdmin.wounds(args("wounds", "1", "atk", "professional", "add", "10", "fix"), "Mod").get(0));
		assertEquals("§aWar 1: removed 5 wounded lives on atk professional.",
				WarArmyAdmin.wounds(args("wounds", "1", "atk", "PROFESSIONAL", "REMOVE", "5"), "Mod").get(0));
		assertEquals(List.of("§bCompany Blades §7(guild guild): 5 wounded lives, 1 of 4 slots wounded"),
				WarArmyAdmin.wounds(args("wounds", "company", "guild"), "Mod"));
		assertEquals("§aCompany Blades: wounded lives 5 -> 0 (0 of 4 slots wounded).",
				WarArmyAdmin.wounds(args("wounds", "company", "guild", "set", "0"), "Mod").get(0));
	}

	@Test
	void woundsExplainsBadInput() {
		String usage = WarArmyAdmin.WOUNDS_USAGE;
		assertEquals(List.of("§cNo mercenary company in guild none"), WarArmyAdmin.wounds(args("wounds", "company", "none"), "Mod"));
		assertEquals(List.of(usage), WarArmyAdmin.wounds(args("wounds", "company", "guild", "set"), "Mod"));
		assertEquals(List.of(usage), WarArmyAdmin.wounds(args("wounds", "company", "guild", "add", "1"), "Mod"));
		assertEquals(List.of(usage), WarArmyAdmin.wounds(args("wounds", "company", "guild", "set", "-1"), "Mod"));
		assertEquals(List.of(usage), WarArmyAdmin.wounds(args("wounds", "1", "atk"), "Mod"));
		assertEquals(List.of("§cNo active war with id 9"),
				WarArmyAdmin.wounds(args("wounds", "9", "atk", "professional", "add", "1"), "Mod"));
		assertEquals(List.of("§cUnknown faction regiment: nope professional"),
				WarArmyAdmin.wounds(args("wounds", "1", "nope", "professional", "add", "1"), "Mod"));
		assertEquals(List.of("§cUnknown faction regiment: atk ghost"),
				WarArmyAdmin.wounds(args("wounds", "1", "atk", "ghost", "add", "1"), "Mod"));
		assertEquals(List.of("§cUnknown faction regiment: atk levy"),
				WarArmyAdmin.wounds(args("wounds", "1", "atk", "levy", "add", "1"), "Mod"));
		Regiment artillery = regiment("artillery", 1, 0);
		when(artillery.isEquipment()).thenReturn(true);
		atk.getMilitary().getRegiments().add(artillery);
		assertEquals(List.of("§cUnknown faction regiment: atk artillery"),
				WarArmyAdmin.wounds(args("wounds", "1", "atk", "artillery", "add", "1"), "Mod"));
		Faction bare = mock(Faction.class);
		fixture.factions.put("bare", bare);
		assertEquals(List.of("§cUnknown faction regiment: bare professional"),
				WarArmyAdmin.wounds(args("wounds", "1", "bare", "professional", "add", "1"), "Mod"));
		assertEquals(List.of(usage), WarArmyAdmin.wounds(args("wounds", "1", "atk", "professional", "set", "1"), "Mod"));
		assertEquals(List.of(usage), WarArmyAdmin.wounds(args("wounds", "1", "atk", "professional", "add", "x"), "Mod"));
		assertEquals(List.of(usage), WarArmyAdmin.wounds(args("wounds", "1", "atk", "professional", "add", "-2"), "Mod"));
	}

	private static String[] args(String... rest) {
		String[] args = new String[rest.length + 1];
		args[0] = "admin";
		System.arraycopy(rest, 0, args, 1, rest.length);
		return args;
	}
}
