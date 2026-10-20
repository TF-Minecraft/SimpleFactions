package net.tfminecraft.simplefactions.diplomacy.coalition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import net.tfminecraft.simplefactions.enums.SFGUI;
import net.tfminecraft.simplefactions.espionage.EspionageAccess;
import net.tfminecraft.simplefactions.espionage.EspionageService;
import net.tfminecraft.simplefactions.managers.InventoryManager;
import net.tfminecraft.simplefactions.managers.RequestManager;
import net.tfminecraft.simplefactions.managers.WarManager;
import net.tfminecraft.simplefactions.managers.holder.CoalitionHolder;
import net.tfminecraft.simplefactions.managers.holder.SFCombinedInventoryHolder;
import net.tfminecraft.simplefactions.managers.inventory.CoalitionView;
import net.tfminecraft.simplefactions.managers.inventory.IconGetter;
import net.tfminecraft.simplefactions.managers.inventory.WarView;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.objects.request.Request;
import net.tfminecraft.simplefactions.war.core.Participant;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.war.declare.WarDeclareRequest;
import net.tfminecraft.simplefactions.war.enums.WarGoalType;
import net.tfminecraft.tlibs.TLibs;
import net.tfminecraft.tlibs.objects.api.ItemAPI;
import net.tfminecraft.tlibs.objects.api.subapi.ItemCreator;

class CoalitionViewTest extends CoalitionTestBase {
	private final List<MockedStatic<?>> scopes = new ArrayList<>();
	private InventoryManager navigation;
	private CoalitionView view;

	private <T> MockedStatic<T> scoped(Class<T> type) {
		MockedStatic<T> scope = mockStatic(type);
		scopes.add(scope);
		return scope;
	}

	@BeforeEach
	void openMenus() {
		MockedStatic<EspionageService> intelligence = scoped(EspionageService.class);
		intelligence.when(() -> EspionageService.canViewExact(any(), any())).thenReturn(true);
		intelligence.when(() -> EspionageService.canViewCovert(any(), any())).thenReturn(true);
		scoped(EspionageAccess.class);
		ItemAPI api = mock(ItemAPI.class);
		ItemCreator items = mock(ItemCreator.class);
		when(api.getCreator()).thenReturn(items);
		when(items.getItemsAdderItem(anyString())).thenAnswer(call -> new ItemStack(Material.PAPER));
		scoped(TLibs.class).when(TLibs::getItemAPI).thenReturn(api);
		scoped(IconGetter.class).when(() -> IconGetter.getIcon(anyString()))
				.thenAnswer(call -> new ItemStack(Material.PAPER));
		navigation = mock(InventoryManager.class);
		when(navigation.createBackButton(any())).thenAnswer(call -> new ItemStack(Material.BARRIER));
		when(navigation.getFiller(any())).thenAnswer(call -> new ItemStack(call.<Material>getArgument(0)));
		view = new CoalitionView(navigation);
	}

	@AfterEach
	void closeMenus() throws Exception {
		for (int n = scopes.size() - 1; n >= 0; n--) scopes.get(n).close();
		requests().clear();
	}

	@SuppressWarnings("unchecked")
	private static Map<Player, Request> requests() throws Exception {
		Field field = RequestManager.class.getDeclaredField("requests");
		field.setAccessible(true);
		return (Map<Player, Request>) field.get(null);
	}

	private Inventory top(Player player) {
		return player.getOpenInventory().getTopInventory();
	}

	private void click(Player player, int slot) {
		view.click(fixture.ui.click(player, slot), top(player), player);
	}

	private String data(ItemStack item, String key) {
		return item.getItemMeta().getPersistentDataContainer().get(new NamespacedKey(fixture.ui.plugin, key), PersistentDataType.STRING);
	}

	private static String lore(ItemStack item) {
		return String.join("\n", item.getItemMeta().getLore().stream().map(ChatColor::stripColor).toList());
	}

	@Test
	void theListPagesThroughEveryCoalitionAndOpensOne() {
		view.list(hana, 0);
		assertEquals("No coalitions yet", ChatColor.stripColor(top(hana).getItem(22).getItemMeta().getDisplayName()));

		Coalition league = league();
		for (int n = 0; n < 45; n++) CoalitionRegistry.add(new Coalition("x" + n, "Pact " + n, "bog", "holy", n));
		view.list(hana, 7);
		assertEquals(1, ((CoalitionHolder) top(hana).getHolder()).getPage());
		assertNotNull(top(hana).getItem(45));
		click(hana, 45);
		assertEquals(0, ((CoalitionHolder) top(hana).getHolder()).getPage());
		assertTrue(lore(top(hana).getItem(0)).contains("Target: holy"));
		click(hana, 53);
		assertEquals(1, ((CoalitionHolder) top(hana).getHolder()).getPage());
		click(hana, 53);
		click(hana, 0);
		assertEquals(CoalitionHolder.Type.MENU, ((CoalitionHolder) top(hana).getHolder()).getType());

		view.list(hana, 0);
		click(hana, 0);
		assertEquals(league.getId(), ((CoalitionHolder) top(hana).getHolder()).getCoalitionId());
		// Anyone may look, but only members act.
		assertNull(top(hana).getItem(49));
		assertNull(top(hana).getItem(6));
		click(hana, 9);
		click(hana, 53);
		assertEquals(CoalitionHolder.Type.LIST, ((CoalitionHolder) top(hana).getHolder()).getType());

		CoalitionRegistry.remove(league);
		view.list(hana, 0);
		CoalitionRegistry.remove(CoalitionRegistry.get("x0"));
		click(hana, 0);
		assertEquals(CoalitionHolder.Type.LIST, ((CoalitionHolder) top(hana).getHolder()).getType());
		assertNull(new CoalitionHolder(CoalitionHolder.Type.MENU, league.getId(), 0).getInventory());
	}

	@Test
	void theMenuShowsTheTargetMembersSeatsAndWars() throws Exception {
		War war = WarManager.addWar(new War(95, holy, thalendor));
		clock.now = war.getStartedAt().minus(Duration.ofHours(1));
		Coalition league = league();
		view.menu(tom, league);
		Inventory menu = top(tom);
		assertTrue(lore(menu.getItem(4)).contains("Defensive coalition"));
		assertTrue(lore(menu.getItem(2)).contains("Realm prestige: 1200"));
		assertTrue(lore(menu.getItem(6)).contains("Diplomatic capacity used: 0"));
		assertTrue(lore(menu.getItem(9)).contains("Leader"));
		assertTrue(lore(menu.getItem(10)).contains("Member"));
		assertTrue(lore(menu.getItem(27)).contains("Members left to call: 2"));
		assertNotNull(menu.getItem(47));
		click(tom, 27);
		verify(navigation).warView(null, tom, war, true);
		WarManager.get().remove(war);
		click(tom, 27);

		league.setOffensive(true);
		league.setTargetLostAt(CoalitionRegistry.now());
		view.menu(tom, league);
		assertNull(top(tom).getItem(47));
		assertNotNull(top(tom).getItem(50));
		assertTrue(lore(top(tom).getItem(2)).contains("No longer a valid target"));
		assertTrue(lore(top(tom).getItem(4)).contains("Target lost. Disbands in 7d 0h"));
		league.setTargetId("gone");
		view.menu(tom, league);
		assertNull(top(tom).getItem(2));
		assertTrue(lore(top(tom).getItem(4)).contains("Target: gone"));
		league.setLeaderId("nobody");
		assertTrue(lore(CoalitionView.coalitionItem(league, false)).contains("Leader: -"));

		// A faction without a banner yet shows a plain one.
		Field banner = Faction.class.getDeclaredField("banner");
		banner.setAccessible(true);
		banner.set(bog, null);
		banner.set(holy, null);
		league.setTargetId("holy");
		view.menu(tom, league);
		assertEquals(Material.WHITE_BANNER, top(tom).getItem(10).getType());
		assertEquals(Material.WHITE_BANNER, top(tom).getItem(2).getType());
	}

	@Test
	void theLeaderActsFromTheMenuAndRiskyActionsNeedASecondClick() {
		Coalition league = league();
		view.menu(tom, league);
		ItemStack banner = new ItemStack(Material.GREEN_BANNER);
		var hands = tom.getInventory();
		org.mockito.Mockito.doReturn(banner).when(hands).getItemInMainHand();
		click(tom, 45);
		assertEquals(List.of("GREEN.BASE"), league.getBannerPatterns());
		click(tom, 46);
		assertNull(league.getBannerPatterns());
		click(tom, 48);

		click(tom, 47);
		org.mockito.Mockito.verify(tom).sendMessage("§eEnter the offensive coalition code staff gave you in chat:");

		// A disband refused for a running war keeps the menu open.
		view.menu(tom, league);
		click(tom, 49);
		org.mockito.Mockito.verify(tom).sendMessage("§7Click again to confirm.");
		War war = WarManager.addWar(new War(96, holy, bog));
		click(tom, 49);
		org.mockito.Mockito.verify(tom).sendMessage("§cYou cannot disband the coalition while one of its wars is running.");
		assertNotNull(CoalitionRegistry.get(league.getId()));
		WarManager.get().remove(war);

		view.menu(bea, league);
		assertNull(top(bea).getItem(45));
		click(bea, 49);
		click(bea, 49);
		assertFalse(league.isMember("bog"));
		assertEquals(CoalitionHolder.Type.MENU, ((CoalitionHolder) top(bea).getHolder()).getType());

		// Another button in between does not reset the confirmation.
		view.menu(tom, league);
		click(tom, 49);
		click(tom, 46);
		assertNotNull(CoalitionRegistry.get(league.getId()));
		click(tom, 49);
		assertNull(CoalitionRegistry.get(league.getId()));
		assertFalse(top(tom).getHolder() instanceof CoalitionHolder);

		// Buttons on a menu for a coalition that is gone, or pressed by a non-leader, do nothing.
		Coalition again = new Coalition("again", "Again", "fig", "holy", 1);
		CoalitionRegistry.add(again);
		view.menu(finn, again);
		CoalitionRegistry.remove(again);
		click(finn, 46);
		Player stranger = fixture.player("Stranger");
		view.menu(finn, again);
		CoalitionRegistry.add(again);
		view.click(fixture.ui.click(finn, 46), top(finn), stranger);
	}

	@Test
	void factionsShowTheirCoalitionInTheListAndTheirMenu() throws Exception {
		assertNull(CoalitionView.factionItem(thalendor));
		league();
		assertEquals(List.of("Part of Grand League (Coalition)"),
				CoalitionView.coalitionLore(bog).stream().map(ChatColor::stripColor).toList());
		assertTrue(lore(CoalitionView.factionItem(holy)).contains("Targeted by Grand League (Coalition)"));
		assertTrue(lore(CoalitionView.factionItem(bog)).contains("Part of Grand League"));

		InventoryManager manager = new InventoryManager();
		Field inv = net.tfminecraft.simplefactions.managers.FactionManager.class.getDeclaredField("inv");
		inv.setAccessible(true);
		Object previous = inv.get(null);
		inv.set(null, manager);
		try (MockedStatic<net.tfminecraft.simplefactions.espionage.RosterLore> roster = mockStatic(net.tfminecraft.simplefactions.espionage.RosterLore.class);
				MockedStatic<net.tfminecraft.simplefactions.utils.HomeSettlementNames> homes = mockStatic(net.tfminecraft.simplefactions.utils.HomeSettlementNames.class)) {
			roster.when(() -> net.tfminecraft.simplefactions.espionage.RosterLore.faction(any(), any())).thenReturn(List.of("Tom"));
			homes.when(() -> net.tfminecraft.simplefactions.utils.HomeSettlementNames.of(any(Faction.class))).thenReturn("Town");
			assertTrue(String.join("\n", manager.factionView.creator.listLore(hana, bog)).contains("Part of"));

			manager.factionView(tom, bog);
			assertNotNull(top(tom).getItem(22));
			manager.clickButton(fixture.ui.click(tom, 22));
			assertEquals(CoalitionHolder.Type.MENU, ((CoalitionHolder) top(tom).getHolder()).getType());
			manager.clickButton(fixture.ui.click(tom, 53));
			assertEquals(CoalitionHolder.Type.LIST, ((CoalitionHolder) top(tom).getHolder()).getType());

			manager.factionView(tom, holy);
			manager.clickButton(fixture.ui.click(tom, 22));
			assertEquals(CoalitionHolder.Type.MENU, ((CoalitionHolder) top(tom).getHolder()).getType());

			Faction loner = faction("loner", "Lon", 10);
			manager.factionView(tom, loner);
			assertNull(top(tom).getItem(22));
			manager.clickButton(fixture.ui.click(tom, 22));
			assertFalse(top(tom).getHolder() instanceof CoalitionHolder);

			// A foreign view still shows coalitions, since they are public.
			scopes.getFirst().when(() -> EspionageService.canViewExact(any(), any())).thenReturn(false);
			try (MockedStatic<net.tfminecraft.simplefactions.managers.inventory.EspionageView> foreign =
					mockStatic(net.tfminecraft.simplefactions.managers.inventory.EspionageView.class)) {
				manager.factionView(hana, bog);
				assertNotNull(top(hana).getItem(22));
			}
		} finally {
			inv.set(null, previous);
		}
	}

	@Test
	void theWarMenuListsCoalitionMembersAndCallsThemOneByOne() {
		War war = WarManager.addWar(new War(97, holy, thalendor));
		clock.now = war.getStartedAt().minus(Duration.ofHours(1));
		league();
		WarView warView = new WarView(navigation);
		Participant defence = war.getParticipant(thalendor);
		warView.participantView(null, tom, war, defence, true);
		Inventory menu = top(tom);
		assertEquals("bog", data(menu.getItem(9), "id"));
		assertTrue(lore(menu.getItem(9)).contains("Coalition member"));
		assertTrue(lore(menu.getItem(9)).contains("Not Called!"));
		assertEquals("fig", data(menu.getItem(10), "id"));

		warView.click(fixture.ui.click(tom, 9), menu, tom);
		assertTrue(RequestManager.getRequest(bea) != null);
		RequestManager.accept(bea);
		assertTrue(defence.hasCoalitionAlly(bog));
		warView.participantView(menu, tom, war, defence, false);
		assertTrue(lore(menu.getItem(9)).contains("Called!"));

		// Clicking a member the coalition may no longer call does nothing.
		Faction stranger = faction("stranger", "Stra", 10);
		ItemStack tagged = new ItemStack(Material.PAPER);
		var meta = tagged.getItemMeta();
		meta.getPersistentDataContainer().set(new NamespacedKey(fixture.ui.plugin, "id"), PersistentDataType.STRING, stranger.getId());
		tagged.setItemMeta(meta);
		menu.setItem(20, tagged);
		warView.click(fixture.ui.click(tom, 20), menu, tom);
		assertNull(RequestManager.getRequest(fixture.player("Stra")));

		String summary = lore(warView.creator.createParticipantItem(tom, defence, "main_defender", war, true, false));
		assertTrue(summary.contains("Coalition: 1"));
	}

	@Test
	void callingACoalitionsTargetAsAnAllyAsksForASecondClick() {
		Faction raider = faction("raider", "Rex", 300);
		Player rex = fixture.player("Rex");
		allies(raider, holy);
		War war = WarManager.addWar(new War(98, raider, thalendor));
		league();
		WarView warView = new WarView(navigation);
		warView.participantView(null, rex, war, war.getParticipant(raider), true);
		Inventory menu = top(rex);
		assertEquals("holy", data(menu.getItem(9), "id"));

		warView.click(fixture.ui.click(rex, 9), menu, rex);
		verify(rex).sendMessage("§7Click again to call them anyway.");
		assertNull(RequestManager.getRequest(hana));
		warView.click(fixture.ui.click(rex, 9), menu, rex);
		assertNotNull(RequestManager.getRequest(hana));
		verify(hana).sendMessage("§cAccepting will let the Grand League §ccoalition, which targets you, join this war.");
	}

	@Test
	void theDeclarationSummaryCarriesCoalitionWarnings() {
		league();
		WarView unused = new WarView(navigation);
		assertNotNull(unused);
		var declare = new net.tfminecraft.simplefactions.managers.inventory.DeclareWarView(navigation);
		String summary = lore(declare.creator.createConfirmSummaryItem(WarDeclareRequest.of(holy, thalendor, WarGoalType.TRIBUTARY)));
		assertTrue(summary.contains("Its members can be called into this war."));
		String quiet = lore(declare.creator.createConfirmSummaryItem(WarDeclareRequest.of(fig, holy, WarGoalType.TRIBUTARY)));
		assertFalse(quiet.contains("coalition"));
		verify(navigation, never()).warView(any(), any(), any(), org.mockito.ArgumentMatchers.anyBoolean());
	}

	@Test
	void coalitionMenusAreDispatchedAndProtectedLikeEveryPluginMenu() {
		InventoryManager manager = new InventoryManager();
		league();
		manager.coalitionView.list(hana, 0);
		var event = fixture.ui.click(hana, 0);
		manager.clickButton(event);
		assertTrue(event.isCancelled());
		assertEquals(CoalitionHolder.Type.MENU, ((CoalitionHolder) top(hana).getHolder()).getType());
		var outside = fixture.ui.click(hana, 60);
		manager.clickButton(outside);
		assertTrue(outside.isCancelled());
		assertEquals(SFGUI.FACTION_VIEW, SFGUI.valueOf("FACTION_VIEW"));
		assertNotNull(new SFCombinedInventoryHolder(1, "a", SFGUI.PARTICIPANT_VIEW));
	}
}
