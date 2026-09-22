package me.Plugins.SimpleFactions.Map.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import com.google.gson.JsonObject;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import me.Plugins.SimpleFactions.Cache;
import me.Plugins.SimpleFactions.Guild.Guild;
import me.Plugins.SimpleFactions.Guild.GuildType;
import me.Plugins.SimpleFactions.Guild.income.Ledger;
import me.Plugins.SimpleFactions.Guild.income.TradeBreakdown;
import me.Plugins.SimpleFactions.Guild.loans.LoanHandler;
import me.Plugins.SimpleFactions.Loaders.RankLoader;
import me.Plugins.SimpleFactions.Managers.FactionManager;
import me.Plugins.SimpleFactions.Managers.RelationManager;
import me.Plugins.SimpleFactions.Managers.TitleManager;
import me.Plugins.SimpleFactions.Managers.WarManager;
import me.Plugins.SimpleFactions.Objects.Bank;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.Objects.Handler.GuildHandler;
import me.Plugins.SimpleFactions.Objects.Modifier;
import me.Plugins.SimpleFactions.Objects.PrestigeRank;
import me.Plugins.SimpleFactions.Tiers.Tier;
import me.Plugins.SimpleFactions.installation.handler.InstallationHandler;
import me.Plugins.SimpleFactions.settlement.handler.SettlementHandler;

class ChronicleSnapshotTest {

	private static void stubGuildExport(Guild guild, String id, String name) {
		Ledger ledger = mock(Ledger.class);
		when(ledger.getNetIncome()).thenReturn(0.0);
		when(ledger.getInflationDelta()).thenReturn(0.0);
		TradeBreakdown trade = mock(TradeBreakdown.class);
		when(trade.getTradePower()).thenReturn(0.0);
		LoanHandler loans = mock(LoanHandler.class);
		when(loans.getCreditScore()).thenReturn(50);
		GuildType type = mock(GuildType.class);
		when(type.getName()).thenReturn("Guild");
		Bank bank = mock(Bank.class);
		when(bank.getWealth()).thenReturn(0.0);

		when(guild.getId()).thenReturn(id);
		when(guild.getOwnName()).thenReturn(name);
		when(guild.getType()).thenReturn(type);
		when(guild.getLedger()).thenReturn(ledger);
		when(guild.getTradeBreakdown()).thenReturn(trade);
		when(guild.getLoanHandler()).thenReturn(loans);
		when(guild.getBank()).thenReturn(bank);
		when(guild.getTotalExpansionSpent()).thenReturn(0.0);
		when(guild.getSize()).thenReturn(0);
	}

	private static Faction faction() {
		Guild main = mock(Guild.class);
		stubGuildExport(main, "rhodesia", "Rhodesia");
		when(main.isBase()).thenReturn(true);
		when(main.getWealthModifiers()).thenReturn(List.of(
				new Modifier("Bank", 4000.0, false),
				new Modifier("Nodes", 8000.0, true)));

		Guild sub = mock(Guild.class);
		stubGuildExport(sub, "The_Betriebsrat", "The Betriebsrat");
		when(sub.isBase()).thenReturn(false);
		when(sub.getName()).thenReturn(
				"§x§a§3§a§1§8§4The Betriebsrat #a39ba8(§x§b§d§a§4§6§4Guild#a39ba8)");
		when(sub.getWealth()).thenReturn(224.71);

		GuildHandler guilds = mock(GuildHandler.class);
		when(guilds.getGuilds()).thenReturn(List.of(main, sub));

		SettlementHandler settlements = mock(SettlementHandler.class);
		when(settlements.getAll()).thenReturn(List.of());

		InstallationHandler installations = mock(InstallationHandler.class);
		when(installations.getAll()).thenReturn(List.of());

		Bank bank = mock(Bank.class);
		when(bank.getWealth()).thenReturn(4000.0);

		PrestigeRank rank = mock(PrestigeRank.class);
		when(rank.getId()).thenReturn("powerful_faction");
		when(rank.getLevel()).thenReturn(3);

		Tier tier = mock(Tier.class);
		when(tier.getId()).thenReturn("kingdom");
		when(tier.getIndex()).thenReturn(2);

		Faction faction = mock(Faction.class);
		when(faction.getId()).thenReturn("rhodesia");
		when(faction.getName()).thenReturn("Rhodesia");
		when(faction.getRGB()).thenReturn("128,64,32");
		when(faction.getFoundedAt()).thenReturn(1725184500L);
		when(faction.getGuildHandler()).thenReturn(guilds);
		when(faction.getOrCreateMainGuild()).thenReturn(main);
		when(faction.getSettlementHandler()).thenReturn(settlements);
		when(faction.getInstallationHandler()).thenReturn(installations);
		when(faction.getBank()).thenReturn(bank);
		when(faction.getWealth()).thenReturn(12000.0);
		when(faction.getVassalWealth()).thenReturn(2200.0);
		when(faction.getPrestige()).thenReturn(2450.0);
		when(faction.getRank()).thenReturn(rank);
		when(faction.getTier()).thenReturn(tier);
		when(faction.getTitles()).thenReturn(List.of());
		when(faction.getProvinces()).thenReturn(List.of(1, 2, 3));
		when(faction.getMembers()).thenReturn(List.of("ann", "bob"));
		when(faction.getCompleteMemberList()).thenReturn(List.of("ann", "bob", "cid"));
		when(faction.getWealthModifiers()).thenReturn(List.of(
				new Modifier("Bank", 4000.0, false),
				new Modifier("Nodes", 8000.0, true),
				new Modifier(
						"§x§a§3§a§1§8§4The Betriebsrat #a39ba8(§x§b§d§a§4§6§4Guild#a39ba8)",
						224.71,
						false)));
		when(faction.getPrestigeModifiers()).thenReturn(List.of(
				new Modifier("Members", 300.0, false),
				new Modifier("Provinces", 2150.0, false)));
		return faction;
	}

	private interface Body {
		void run(JsonObject root, Faction faction);
	}

	/** Everything the snapshot reads is static manager surface, so it all gets stubbed. */
	private static void withSnapshot(Body body) {
		Faction faction = faction();
		List<Faction> factions = List.of(faction);
		String previousMapRef = Cache.mapRef;
		String previousChapterId = Cache.chapterId;
		String previousChapterName = Cache.chapterName;
		Cache.mapRef = "main";
		Cache.chapterId = "vardera";
		Cache.chapterName = "Vardera";

		try (MockedStatic<FactionManager> factionManager = mockStatic(FactionManager.class);
				MockedStatic<RelationManager> relations = mockStatic(RelationManager.class);
				MockedStatic<TitleManager> titles = mockStatic(TitleManager.class);
				MockedStatic<RankLoader> ranks = mockStatic(RankLoader.class);
				MockedStatic<WarManager> wars = mockStatic(WarManager.class)) {

			factionManager.when(FactionManager::getGlobalWealth).thenReturn(12000.0);
			factionManager.when(FactionManager::getPouchWealth).thenReturn(450.0);
			factionManager.when(FactionManager::getBankWealth).thenReturn(890.0);
			factionManager.when(FactionManager::getGlobalLiquidWealth).thenReturn(4000.0);
			factionManager.when(FactionManager::getGuildLiquidWealth).thenReturn(0.0);
			factionManager.when(FactionManager::getGlobalNodeWealth).thenReturn(8000.0);
			factionManager.when(FactionManager::getGlobalGuildExpansions).thenReturn(0.0);
			factionManager.when(FactionManager::getTotalGuildIncome).thenReturn(84.0);
			factionManager.when(FactionManager::getCopy).thenReturn(new java.util.ArrayList<>(factions));
			factionManager.when(() -> FactionManager.getRankUpAmount(org.mockito.ArgumentMatchers.any()))
					.thenReturn(3000.0);

			relations.when(() -> RelationManager.getOverlord(faction)).thenReturn(null);
			relations.when(() -> RelationManager.getSubjects(faction)).thenReturn(List.of());
			titles.when(() -> TitleManager.getRealmSize(faction)).thenReturn(5);
			ranks.when(() -> RankLoader.getByLevel(4)).thenReturn(mock(PrestigeRank.class));
			wars.when(WarManager::getActive).thenReturn(List.of());

			body.run(ChronicleSnapshot.build(factions, 143, 43200, Instant.parse("2026-09-01T10:35:00Z")), faction);
		} finally {
			Cache.mapRef = previousMapRef;
			Cache.chapterId = previousChapterId;
			Cache.chapterName = previousChapterName;
		}
	}

	@Test
	void snapshot_hasEnvelopeKeys() {
		withSnapshot((root, faction) -> {
			assertEquals(ChronicleSnapshot.SCHEMA_VERSION, root.get("schema_version").getAsInt());
			assertEquals("main", root.get("map_id").getAsString());
			assertEquals("vardera", root.get("chapter_id").getAsString());
			assertEquals("Vardera", root.get("chapter_name").getAsString());
			assertEquals("2026-09-01T10:35:00Z", root.get("captured_at").getAsString());
			assertEquals(143, root.get("server_day").getAsInt());
			assertEquals(43200, root.get("day_progress_seconds").getAsInt());
			assertTrue(root.get("complete").getAsBoolean());
			assertTrue(root.has("global"));
			assertTrue(root.get("factions").isJsonArray());
			assertTrue(root.get("guilds").isJsonArray());
		});
	}

	@Test
	void snapshot_eventsPresentAndEmpty() {
		withSnapshot((root, faction) ->
				assertEquals(0, root.getAsJsonArray("events").size()));
	}

	/** Faction wealth excludes personal money, so these must stay separate series. */
	@Test
	void snapshot_globalsNotPreSummed() {
		withSnapshot((root, faction) -> {
			JsonObject global = root.getAsJsonObject("global");
			assertEquals(12000.0, global.get("faction_wealth").getAsDouble(), 1e-9);
			assertEquals(450.0, global.get("pouch_wealth").getAsDouble(), 1e-9);
			assertEquals(890.0, global.get("player_bank_wealth").getAsDouble(), 1e-9);
			assertEquals(1, global.get("faction_count").getAsInt());
			assertEquals(3, global.get("claimed_provinces").getAsInt());
			assertEquals(2, global.get("population").getAsInt());
		});
	}

	@Test
	void snapshot_breakdownsAreKeyedMaps() {
		withSnapshot((root, faction) -> {
			JsonObject row = root.getAsJsonArray("factions").get(0).getAsJsonObject();
			JsonObject wealth = row.getAsJsonObject("wealth_breakdown");
			assertEquals(4000.0, wealth.get("Bank").getAsDouble(), 1e-9);
			assertEquals(8000.0, wealth.get("Nodes").getAsDouble(), 1e-9);
			assertEquals(224.71, wealth.get("The_Betriebsrat").getAsDouble(), 1e-9);
			assertTrue(!wealth.has(
					"§x§a§3§a§1§8§4The Betriebsrat #a39ba8(§x§b§d§a§4§6§4Guild#a39ba8)"));

			JsonObject prestige = row.getAsJsonObject("prestige_breakdown");
			assertEquals(300.0, prestige.get("Members").getAsDouble(), 1e-9);
			assertEquals(2150.0, prestige.get("Provinces").getAsDouble(), 1e-9);
		});
	}

	@Test
	void snapshot_rankThresholds() {
		withSnapshot((root, faction) -> {
			JsonObject row = root.getAsJsonArray("factions").get(0).getAsJsonObject();
			assertEquals("powerful_faction", row.get("rank").getAsString());
			assertEquals(3, row.get("rank_level").getAsInt());
			assertEquals(3000.0, row.get("rank_up_at").getAsDouble(), 1e-9);
			assertEquals(3000.0 * 0.95, row.get("rank_down_at").getAsDouble(), 1e-9);
		});
	}

	@Test
	void snapshot_carriesIdentityAndCounts() {
		withSnapshot((root, faction) -> {
			JsonObject row = root.getAsJsonArray("factions").get(0).getAsJsonObject();
			assertEquals("rhodesia", row.get("id").getAsString());
			// Paired with the id so the backend can tell a recycled name apart.
			assertEquals(1725184500L, row.get("founded_at").getAsLong());
			assertEquals("kingdom", row.get("tier").getAsString());
			assertEquals(3, row.get("provinces").getAsInt());
			assertEquals(5, row.get("realm_size").getAsInt());
			assertEquals(2, row.get("members").getAsInt());
			assertEquals(3, row.get("members_with_vassals").getAsInt());
			assertEquals(4000.0, row.get("bank").getAsDouble(), 1e-9);
			assertEquals(2200.0, row.get("vassal_wealth").getAsDouble(), 1e-9);
		});
	}
}
