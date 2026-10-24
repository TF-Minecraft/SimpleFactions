package net.tfminecraft.simplefactions.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import net.tfminecraft.simplefactions.economy.TreasuryTopology;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.identity.Members;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.map.MapSystem;
import net.tfminecraft.simplefactions.objects.Bank;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.objects.handler.GuildHandler;
import net.tfminecraft.simplefactions.objects.handler.ProvinceHandler;
import net.tfminecraft.simplefactions.settlement.Settlement;
import net.tfminecraft.simplefactions.settlement.handler.CapitalResult;
import net.tfminecraft.simplefactions.settlement.handler.SettlementHandler;
import net.tfminecraft.simplefactions.utils.RandomRGB;

class StaffAdminServiceTest {
	private MockedStatic<Members> members;
	private MockedStatic<FactionManager> factions;
	private MockedStatic<RandomRGB> colours;
	private Faction faction;
	private GuildHandler guilds;
	private Guild base;
	private Guild smiths;
	private SettlementHandler settlements;
	private ProvinceHandler provinces;
	private MapSystem map;
	private Player staff;

	@BeforeEach
	void setUp() {
		members = mockStatic(Members.class, CALLS_REAL_METHODS);
		factions = mockStatic(FactionManager.class, CALLS_REAL_METHODS);
		colours = mockStatic(RandomRGB.class);
		members.when(() -> Members.display(anyString())).thenAnswer(call -> call.getArgument(0));
		members.when(() -> Members.online(anyString())).thenReturn(Optional.empty());
		faction = mock(Faction.class);
		when(faction.getId()).thenReturn("sunsora");
		when(faction.getName()).thenReturn("Sunsora");
		when(faction.getLeader()).thenReturn("Verena");
		when(faction.getRGB()).thenReturn("10,20,30");
		when(faction.getMembers()).thenReturn(List.of("Verena", "Bob", "Carl"));
		guilds = mock(GuildHandler.class);
		when(faction.getGuildHandler()).thenReturn(guilds);
		base = mock(Guild.class);
		when(base.isBase()).thenReturn(true);
		when(base.getId()).thenReturn("sunsora");
		when(base.getFaction()).thenReturn(faction);
		smiths = mock(Guild.class);
		when(smiths.getId()).thenReturn("smiths");
		when(smiths.getName()).thenReturn("Smiths");
		when(smiths.getFaction()).thenReturn(faction);
		when(smiths.getLeader()).thenReturn("Carl");
		when(smiths.isLeader("Carl")).thenReturn(true);
		when(smiths.getMembers()).thenReturn(List.of("Carl", "Bob"));
		when(smiths.isMember("Carl")).thenReturn(true);
		when(smiths.isMember("Bob")).thenReturn(true);
		when(guilds.getGuilds()).thenReturn(List.of(base, smiths));
		settlements = mock(SettlementHandler.class);
		when(faction.getSettlementHandler()).thenReturn(settlements);
		provinces = mock(ProvinceHandler.class);
		when(faction.getProvinceHandler()).thenReturn(provinces);
		map = mock(MapSystem.class);
		factions.when(FactionManager::getMap).thenReturn(map);
		factions.when(() -> FactionManager.getByString("sunsora")).thenReturn(faction);
		factions.when(FactionManager::getCopy).thenReturn(List.of(faction));
		colours.when(() -> RandomRGB.isFree(anyString())).thenReturn(true);
		staff = mock(Player.class);
	}

	@AfterEach
	void tearDown() {
		colours.close();
		factions.close();
		members.close();
	}

	private static Settlement settlement(String id, String name, int province) {
		Settlement settlement = mock(Settlement.class);
		when(settlement.getId()).thenReturn(id);
		when(settlement.getName()).thenReturn(name);
		when(settlement.getCenterProvince()).thenReturn(province);
		return settlement;
	}

	private static ItemStack item(Material type) {
		ItemStack item = mock(ItemStack.class);
		when(item.getType()).thenReturn(type);
		return item;
	}

	@Test
	void namesFollowThePlayerCommandsFormat() {
		assertTrue(StaffAdminService.displayName("Sunsora_Castle").endsWith("Sunsora Castle"));
		assertTrue(StaffAdminService.validName("Sunsora Castle"));
		assertEquals(false, StaffAdminService.validName("!!!"));
	}

	@Test
	void coloursAreCheckedLikeThePlayerCommands() {
		assertEquals("§cInvalid format. Use: R,G,B (e.g. 255,0,0)", StaffAdminService.colourProblem("1,2", "1,1,1"));
		assertEquals("§cRGB values must be numbers (e.g. 128,128,128)", StaffAdminService.colourProblem("a,b,c", "1,1,1"));
		assertEquals("§cEach RGB value must be between 0 and 255", StaffAdminService.colourProblem("1,2,300", "1,1,1"));
		assertEquals("§c0,0,0 is reserved for unclaimed land; pick another colour", StaffAdminService.colourProblem("0,0,0", "1,1,1"));
		colours.when(() -> RandomRGB.sameRGB("5,5,5", "1,1,1")).thenReturn(false);
		colours.when(() -> RandomRGB.isFree("5,5,5")).thenReturn(false);
		assertEquals("§cThat colour is already used", StaffAdminService.colourProblem("5,5,5", "1,1,1"));
		colours.when(() -> RandomRGB.sameRGB("5,5,5", "5,5,5")).thenReturn(true);
		assertNull(StaffAdminService.colourProblem("5,5,5", "5,5,5"));
		colours.when(() -> RandomRGB.isFree("6,6,6")).thenReturn(true);
		assertNull(StaffAdminService.colourProblem("6,6,6", "1,1,1"));
	}

	@Test
	void onlyBannersCount() {
		assertEquals(false, StaffAdminService.isBanner(null));
		assertEquals(false, StaffAdminService.isBanner(item(Material.STONE)));
		assertTrue(StaffAdminService.isBanner(item(Material.RED_BANNER)));
	}

	@Test
	void infoShowsCapitalSettlementsGuildsAndBank() {
		Settlement capital = settlement("research_tower", "Research Tower", 395);
		Settlement port = settlement("port", "Port", 393);
		when(faction.hasCapital()).thenReturn(true);
		when(faction.getCapital()).thenReturn(395);
		when(faction.getCapitalMoves()).thenReturn(2);
		when(faction.getProvinces()).thenReturn(List.of(395, 393));
		when(faction.getCulture()).thenReturn("Elven");
		when(settlements.getByProvince(395)).thenReturn(capital);
		when(settlements.getAll()).thenReturn(List.of(capital, port));
		Bank bank = mock(Bank.class);
		when(bank.getWealth()).thenReturn(12.5);
		when(faction.getBank()).thenReturn(bank);
		List<String> lines = StaffAdminService.info(faction);
		assertEquals("§6=== §fSunsora §7(sunsora) §6===", lines.get(0));
		assertEquals("§7Leader: §fVerena §7(Verena)§f", lines.get(1));
		assertEquals("§7Members: §f3 §7Provinces: §f2 §7Colour: §f10,20,30", lines.get(2));
		assertEquals("§7Culture: §fElven §7Religion: §fnone §7Ruling system: §fnone §7Ruler title: §fnone", lines.get(3));
		assertEquals("§7Capital: §fprovince 395, Research Tower §7(research_tower)§f §7Capital changes made: §f2", lines.get(4));
		assertEquals("§7Settlement: §fPort §7(port)§f §7in province §f393", lines.get(5));
		assertEquals("§7Guild: §fSmiths §7(smiths) led by §fCarl §7(Carl)§f §7with §f2 §7members", lines.get(6));
		assertEquals("§7Bank: §f12.50d", lines.get(7));
	}

	@Test
	void infoCopesWithNoCapitalNoLeaderAndNoBank() {
		when(faction.getLeader()).thenReturn("");
		when(faction.getReligion()).thenReturn(" ");
		when(faction.getProvinces()).thenReturn(List.of());
		when(settlements.getAll()).thenReturn(List.of());
		List<String> lines = StaffAdminService.info(faction);
		assertEquals("§7Leader: §fnobody", lines.get(1));
		assertEquals("§7Capital: §fnone §7Capital changes made: §f0", lines.get(4));
		assertEquals("§7Bank: §fnone", lines.get(lines.size() - 1));
		when(faction.hasCapital()).thenReturn(true);
		when(faction.getCapital()).thenReturn(7);
		when(faction.getLeader()).thenReturn(null);
		lines = StaffAdminService.info(faction);
		assertEquals("§7Leader: §fnobody", lines.get(1));
		assertEquals("§7Capital: §fprovince 7 §7Capital changes made: §f0", lines.get(4));
	}

	@Test
	void whoFindsTheRealmAndGuildOfACharacter() {
		members.when(() -> Members.find(List.of("Verena", "Bob", "Carl"), "carl")).thenReturn("Carl");
		assertEquals(List.of("§fCarl §7(Carl)§f §7is in §fSunsora §7(sunsora)§7, guild §fSmiths §7(smiths) §7as its leader"),
				StaffAdminService.who("carl"));
		members.when(() -> Members.find(List.of("Verena", "Bob", "Carl"), "bob")).thenReturn("Bob");
		assertEquals(List.of("§fBob §7(Bob)§f §7is in §fSunsora §7(sunsora)§7, guild §fSmiths §7(smiths)"),
				StaffAdminService.who("bob"));
		members.when(() -> Members.find(List.of("Verena", "Bob", "Carl"), "verena")).thenReturn("Verena");
		assertEquals(List.of("§fVerena §7(Verena)§f §7is in §fSunsora §7(sunsora) §7and leads it"),
				StaffAdminService.who("verena"));
		members.when(() -> Members.find(List.of("Verena", "Bob", "Carl"), "nobody")).thenReturn(null);
		assertEquals(List.of("§cNo faction member matches nobody"), StaffAdminService.who("nobody"));
	}

	@Test
	void renameKeepsTheIdAndRedrawsTheMap() {
		assertEquals(List.of("§cInvalid faction name"), StaffAdminService.rename(faction, "???"));
		verify(faction, never()).setName(anyString());
		when(faction.getName()).thenReturn("Sunsora", "New Sunsora");
		assertEquals(List.of("§aRenamed faction §fSunsora §ato §fNew Sunsora"), StaffAdminService.rename(faction, "New_Sunsora"));
		verify(faction).setName(StaffAdminService.displayName("New_Sunsora"));
		verify(map).enqueue("nation", "10,20,30");
	}

	@Test
	void renameSettlementFindsTheCapitalOrAnId() {
		assertEquals(List.of("§cSunsora §chas no settlement capital§c. §e/faction admin info sunsora §clists them."),
				StaffAdminService.renameSettlement(faction, "capital", "Sunsora Castle"));
		Settlement capital = settlement("research_tower", "Research Tower", 395);
		when(faction.hasCapital()).thenReturn(true);
		when(faction.getCapital()).thenReturn(395);
		when(settlements.getByProvince(395)).thenReturn(capital);
		when(settlements.rename(395, "Sunsora Castle", false)).thenReturn(CapitalResult.ok("§aRenamed"));
		assertEquals(List.of("§aRenamed"), StaffAdminService.renameSettlement(faction, "CAPITAL", "Sunsora Castle"));
		Settlement port = settlement("Port", "Port", 393);
		when(settlements.getAll()).thenReturn(List.of(port, capital));
		assertEquals(List.of("§aRenamed"), StaffAdminService.renameSettlement(faction, "Research_Tower", "Sunsora Castle"));
		verify(settlements, never()).rename(393, "Sunsora Castle", false);
		when(settlements.getAll()).thenReturn(List.of(capital));
		assertEquals(List.of("§cSunsora §chas no settlement port§c. §e/faction admin info sunsora §clists them."),
				StaffAdminService.renameSettlement(faction, "port", "Harbour"));
	}

	@Test
	void capitalPreviewRefusesBadProvinces() {
		List<String> lines = new ArrayList<>();
		assertNull(StaffAdminService.previewCapital(staff, faction, -2, null, lines));
		assertEquals(List.of("§a[SimpleFactions] §cError! could not resolve province"), lines);
		lines.clear();
		assertNull(StaffAdminService.previewCapital(staff, faction, 9, null, lines));
		assertEquals(List.of("§cSunsora §cdoes not own province 9§c. Give it to them with §e/faction forceclaim sunsora §cfirst."), lines);
		lines.clear();
		when(faction.ownsProvince(395)).thenReturn(true);
		when(faction.hasCapital()).thenReturn(true);
		when(faction.getCapital()).thenReturn(395);
		assertNull(StaffAdminService.previewCapital(staff, faction, 395, null, lines));
		assertEquals(List.of("§cProvince 395 §cis already the capital of Sunsora§c. Rename it with §e/faction admin renamesettlement sunsora capital <name>"), lines);
		lines.clear();
		when(faction.ownsProvince(393)).thenReturn(true);
		when(settlements.validateFactionCapital(staff, 393, null)).thenReturn(CapitalResult.fail("§cName required"));
		assertNull(StaffAdminService.previewCapital(staff, faction, 393, null, lines));
		assertEquals(List.of("§cName required"), lines);
	}

	@Test
	void capitalPreviewDescribesTheMoveAndAnyLostLand() {
		List<String> lines = new ArrayList<>();
		when(faction.ownsProvince(393)).thenReturn(true);
		when(faction.hasCapital()).thenReturn(true);
		when(faction.getCapital()).thenReturn(395);
		when(settlements.validateFactionCapital(staff, 393, "Sunsora_Castle")).thenReturn(CapitalResult.ok("ok"));
		when(provinces.previewProvincesLostIfCapitalMoved(393)).thenReturn(List.of(12));
		StaffAdminService.CapitalMove move = StaffAdminService.previewCapital(staff, faction, 393, "Sunsora_Castle", lines);
		assertEquals(new StaffAdminService.CapitalMove("sunsora", 395, 393, "Sunsora_Castle"), move);
		assertEquals("§eMove the capital of Sunsora §efrom province 395 §eto province 393 §e(new settlement "
				+ StaffAdminService.displayName("Sunsora_Castle") + "§e)", lines.get(0));
		assertEquals("§cSunsora §cwould lose provinces cut off from the new capital: [12]", lines.get(1));
		assertEquals(3, lines.size());

		lines.clear();
		Settlement port = settlement("port", "Port", 393);
		when(settlements.getByProvince(393)).thenReturn(port);
		when(settlements.validateFactionCapital(staff, 393, null)).thenReturn(CapitalResult.ok("ok"));
		when(provinces.previewProvincesLostIfCapitalMoved(393)).thenReturn(List.of());
		assertEquals(new StaffAdminService.CapitalMove("sunsora", 395, 393, null),
				StaffAdminService.previewCapital(staff, faction, 393, null, lines));
		assertEquals("§eMove the capital of Sunsora §efrom province 395 §eto province 393 §e(Port§e)", lines.get(0));
		assertEquals(2, lines.size());

		lines.clear();
		when(faction.hasCapital()).thenReturn(false);
		when(faction.getCapital()).thenReturn(-1);
		when(settlements.validateFactionCapital(staff, 393, "Ignored")).thenReturn(CapitalResult.ok("ok"));
		assertEquals(new StaffAdminService.CapitalMove("sunsora", -1, 393, null),
				StaffAdminService.previewCapital(staff, faction, 393, "Ignored", lines));
		assertEquals("§eMove the capital of Sunsora §efrom nowhere §eto province 393 §e(Port§e)", lines.get(0));
	}

	@Test
	void capitalMoveChecksNothingChangedSincePreview() {
		StaffAdminService.CapitalMove move = new StaffAdminService.CapitalMove("gone", 395, 393, null);
		assertEquals(List.of("§cThat faction no longer exists. Nothing changed."), StaffAdminService.moveCapital(staff, move, 393));
		move = new StaffAdminService.CapitalMove("sunsora", 395, 393, null);
		when(faction.getCapital()).thenReturn(1);
		assertEquals(List.of("§cTheir capital changed since the preview. Nothing changed."), StaffAdminService.moveCapital(staff, move, 393));
		when(faction.getCapital()).thenReturn(395);
		assertEquals(List.of("§cStand in province 393 §cto confirm. Nothing changed."), StaffAdminService.moveCapital(staff, move, 1));
	}

	@Test
	void aFirstCapitalIsFoundedInPlace() {
		when(faction.getCapital()).thenReturn(-1);
		StaffAdminService.CapitalMove move = new StaffAdminService.CapitalMove("sunsora", -1, 393, "Sunsora_Castle");
		when(settlements.applyFactionCapital(staff, 393, "Sunsora_Castle")).thenReturn(CapitalResult.fail("§cNo"));
		assertEquals(List.of("§cNo"), StaffAdminService.moveCapital(staff, move, 393));
		verify(faction, never()).setCapital(393);
		when(settlements.applyFactionCapital(staff, 393, "Sunsora_Castle")).thenReturn(CapitalResult.ok("§aFounded"));
		assertEquals(List.of("§aFounded", "§aSet the capital of Sunsora §ato province 393"), StaffAdminService.moveCapital(staff, move, 393));
		verify(faction).setCapital(393);
		verify(provinces).revalidateClaims();
	}

	@Test
	void anExistingCapitalMovesFreeThroughTheTreasuryWithoutCountingAMove() {
		when(faction.hasCapital()).thenReturn(true);
		when(faction.getCapital()).thenReturn(395);
		StaffAdminService.CapitalMove move = new StaffAdminService.CapitalMove("sunsora", 395, 393, null);
		try (MockedStatic<TreasuryTopology> topology = mockStatic(TreasuryTopology.class)) {
			topology.when(() -> TreasuryTopology.capital(eq(staff), eq(faction), eq(393), isNull(), eq(false), eq(0.0),
					anyString(), eq(false))).thenReturn(false);
			when(faction.getProvinces()).thenReturn(List.of(395, 393, 12));
			assertEquals(List.of("§cThe capital move failed. Nothing changed."), StaffAdminService.moveCapital(staff, move, 393));
			topology.when(() -> TreasuryTopology.capital(eq(staff), eq(faction), eq(393), isNull(), eq(false), eq(0.0),
					anyString(), eq(false))).thenReturn(true);
			when(faction.getProvinces()).thenReturn(List.of(395, 393, 12), List.of(395, 393));
			assertEquals(List.of("§aMoved the capital of Sunsora §ato province 393",
					"§eSunsora §elost provinces cut off from their realm: [12]"), StaffAdminService.moveCapital(staff, move, 393));
			when(faction.getProvinces()).thenReturn(List.of(395, 393));
			assertEquals(List.of("§aMoved the capital of Sunsora §ato province 393"), StaffAdminService.moveCapital(staff, move, 393));
		}
	}

	@Test
	void colourBannerAndDetailsChange() {
		assertEquals(List.of("§cInvalid format. Use: R,G,B (e.g. 255,0,0)"), StaffAdminService.setColour(faction, "x"));
		verify(faction, never()).setRGB(anyString());
		assertEquals(List.of("§aColour of Sunsora §aset to §f1,2,3"), StaffAdminService.setColour(faction, "1,2,3"));
		verify(faction).setRGB("1,2,3");
		verify(map, org.mockito.Mockito.times(2)).enqueue("nation", "10,20,30");

		assertEquals(List.of("§cHold the new banner in your main hand"), StaffAdminService.setBanner(faction, item(Material.STONE)));
		ItemStack banner = item(Material.BLUE_BANNER);
		assertEquals(List.of("§aBanner of Sunsora §achanged"), StaffAdminService.setBanner(faction, banner));
		verify(faction).setBanner(banner);

		assertEquals(List.of("§aSet culture of Sunsora §ato §fHigh Elven"), StaffAdminService.set(faction, "Culture", "High_Elven"));
		verify(faction).setCulture("High Elven");
		StaffAdminService.set(faction, "religion", "Sun");
		verify(faction).setReligion("Sun");
		StaffAdminService.set(faction, "rulingsystem", "Monarchy");
		verify(faction).setGovernment("Monarchy");
		StaffAdminService.set(faction, "rulertitle", "Queen");
		verify(faction).setRulerTitle("Queen");
		assertEquals(List.of("§cUnknown field motto§c. Use one of: culture, religion, rulingsystem, rulertitle"),
				StaffAdminService.set(faction, "motto", "x"));
	}

	@Test
	void kickRemovesMembersButNotLeaders() {
		members.when(() -> Members.find(faction.getMembers(), "x")).thenReturn(null);
		assertEquals(List.of("§cNo member of Sunsora §cmatches x"), StaffAdminService.kick(faction, "x"));
		members.when(() -> Members.find(faction.getMembers(), "verena")).thenReturn("Verena");
		assertEquals(List.of("§cVerena §cleads Sunsora§c. Replace them with §e/faction forceleader §cfirst."),
				StaffAdminService.kick(faction, "verena"));
		members.when(() -> Members.find(faction.getMembers(), "carl")).thenReturn("Carl");
		assertEquals(List.of("§cCarl §cleads the guild Smiths§c. Replace them with §e/guild forceleader §cor delete the guild first."),
				StaffAdminService.kick(faction, "carl"));
		verify(faction, never()).forceRemoveMember(anyString());
		members.when(() -> Members.find(faction.getMembers(), "bob")).thenReturn("Bob");
		Player bob = mock(Player.class);
		members.when(() -> Members.online("Bob")).thenReturn(Optional.of(bob));
		assertEquals(List.of("§aRemoved Bob §afrom Sunsora"), StaffAdminService.kick(faction, "bob"));
		verify(faction).forceRemoveMember("Bob");
		verify(faction).updatePrestige();
		verify(bob).sendMessage("§cStaff removed you from Sunsora");
	}

	@Test
	void guildInfoShowsMembersSeatAndBank() {
		when(smiths.getRGB()).thenReturn("4,5,6");
		List<String> lines = StaffAdminService.guildInfo(smiths);
		assertEquals("§6=== §fSmiths §7(smiths) §6===", lines.get(0));
		assertEquals("§7Faction: §fSunsora §7(sunsora)", lines.get(1));
		assertEquals("§7Leader: §fCarl §7(Carl)§f", lines.get(2));
		assertEquals("§7Members (2): §fCarl, Bob", lines.get(3));
		assertEquals("§7Seat: §fnone §7Colour: §f4,5,6", lines.get(4));
		assertEquals("§7Bank: §fnone", lines.get(5));
		when(smiths.hasCapital()).thenReturn(true);
		when(smiths.getCapital()).thenReturn(12);
		Bank bank = mock(Bank.class);
		when(bank.getWealth()).thenReturn(3.0);
		when(smiths.getBank()).thenReturn(bank);
		lines = StaffAdminService.guildInfo(smiths);
		assertEquals("§7Seat: §fprovince 12 §7Colour: §f4,5,6", lines.get(4));
		assertEquals("§7Bank: §f3.00d", lines.get(5));
		Settlement forge = settlement("forge", "Forge", 12);
		when(settlements.getByProvince(12)).thenReturn(forge);
		assertEquals("§7Seat: §fprovince 12, Forge §7(forge)§f §7Colour: §f4,5,6", StaffAdminService.guildInfo(smiths).get(4));
		when(base.getName()).thenReturn("Sunsora");
		when(base.getMembers()).thenReturn(List.of());
		assertEquals("§7Faction: §fSunsora §7(sunsora) §7- its main guild", StaffAdminService.guildInfo(base).get(1));
	}

	@Test
	void guildEditsRefuseTheMainGuild() {
		String refusal = "§csunsora §cis the main guild of Sunsora§c; use §e/faction admin %s sunsora §cinstead";
		assertNull(StaffAdminService.baseGuild(smiths, "rename"));
		assertEquals(List.of(String.format(refusal, "rename")), StaffAdminService.guildRename(base, "X"));
		assertEquals(List.of(String.format(refusal, "setcolour")), StaffAdminService.guildSetColour(base, "1,2,3"));
		assertEquals(List.of(String.format(refusal, "setbanner")), StaffAdminService.guildSetBanner(base, item(Material.RED_BANNER)));
		assertEquals(List.of(String.format(refusal, "kick")), StaffAdminService.guildKick(base, "Bob"));
	}

	@Test
	void guildRenameColourAndBannerChange() {
		assertEquals(List.of("§cInvalid guild name"), StaffAdminService.guildRename(smiths, "!"));
		when(smiths.getName()).thenReturn("Smiths", "Iron Smiths");
		assertEquals(List.of("§aRenamed guild §fSmiths §ato §fIron Smiths"), StaffAdminService.guildRename(smiths, "Iron_Smiths"));
		verify(smiths).setName(StaffAdminService.displayName("Iron_Smiths"));

		when(smiths.getName()).thenReturn("Smiths");
		assertEquals(List.of("§cEach RGB value must be between 0 and 255"), StaffAdminService.guildSetColour(smiths, "1,2,999"));
		assertEquals(List.of("§aColour of Smiths §aset to §f1,2,3"), StaffAdminService.guildSetColour(smiths, "1,2,3"));
		verify(smiths).setRGB("1,2,3");

		assertEquals(List.of("§cHold the new banner in your main hand"), StaffAdminService.guildSetBanner(smiths, null));
		ItemStack banner = item(Material.RED_BANNER);
		assertEquals(List.of("§aBanner of Smiths §achanged"), StaffAdminService.guildSetBanner(smiths, banner));
		verify(smiths).setBanner(banner);
	}

	@Test
	void guildKickSendsMembersBackToTheMainGuild() {
		members.when(() -> Members.find(smiths.getMembers(), "x")).thenReturn(null);
		assertEquals(List.of("§cNo member of Smiths §cmatches x"), StaffAdminService.guildKick(smiths, "x"));
		members.when(() -> Members.find(smiths.getMembers(), "carl")).thenReturn("Carl");
		assertEquals(List.of("§cCarl §cleads Smiths§c. Replace them with §e/guild forceleader §cfirst."),
				StaffAdminService.guildKick(smiths, "carl"));
		verify(smiths, never()).kick(anyString());
		members.when(() -> Members.find(smiths.getMembers(), "bob")).thenReturn("Bob");
		assertEquals(List.of("§aMoved Bob §afrom Smiths §ato the main guild of Sunsora"), StaffAdminService.guildKick(smiths, "bob"));
		verify(smiths).kick("Bob");
		verify(faction).addMember("Bob");
		Player bob = mock(Player.class);
		members.when(() -> Members.online("Bob")).thenReturn(Optional.of(bob));
		StaffAdminService.guildKick(smiths, "bob");
		verify(bob).sendMessage("§cStaff removed you from Smiths§c. You are back in the main guild of Sunsora");
	}
}
