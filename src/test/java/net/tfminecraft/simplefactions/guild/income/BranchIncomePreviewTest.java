package net.tfminecraft.simplefactions.guild.income;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.enums.GuildModifier;
import net.tfminecraft.simplefactions.enums.Terrain;
import net.tfminecraft.simplefactions.government.proposal.TaxTarget;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.guild.GuildModifierOverride;
import net.tfminecraft.simplefactions.guild.branch.Branch;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.ProvinceManager;
import net.tfminecraft.simplefactions.map.provinces.Province;
import net.tfminecraft.simplefactions.objects.Faction;

class BranchIncomePreviewTest {
	private final List<Faction> savedFactions = new ArrayList<>();
	private boolean savedProvincesEnabled;
	private Double savedPlainsCarry;
	private Province capital;
	private Province neighbour;
	private ProvinceManager live;
	private Guild guild;
	private Faction faction;
	private Branch branch;

	@BeforeEach
	void setUp() {
		savedFactions.addAll(FactionManager.factions);
		FactionManager.factions.clear();
		savedProvincesEnabled = Cache.provincesEnabled;
		Cache.provincesEnabled = true;
		savedPlainsCarry = Cache.tradeCarry.get(Terrain.PLAINS);
		Cache.tradeCarry.put(Terrain.PLAINS, 0.8);

		capital = new Province(91001, Terrain.PLAINS.name(), 40, 0, 0);
		neighbour = new Province(91002, Terrain.PLAINS.name(), 40, 16, 0);
		capital.addNeighbour(neighbour.getId());
		neighbour.addNeighbour(capital.getId());
		capital.setProsperity(12.5);

		live = new ProvinceManager();
		live.start(Map.of(capital.getId(), capital, neighbour.getId(), neighbour));

		faction = mock(Faction.class);
		when(faction.getTaxRate(TaxTarget.GUILDS, "fields", true)).thenReturn(0.0);

		guild = mock(Guild.class);
		when(guild.getId()).thenReturn("fields");
		when(guild.hasCapital()).thenReturn(true);
		when(guild.getCapital()).thenReturn(capital.getId());
		when(guild.getFaction()).thenReturn(faction);
		when(guild.getModifier(any())).thenAnswer(invocation -> switch ((GuildModifier) invocation.getArgument(0)) {
			case TRADE_POWER -> 8.0;
			case TRADE_CARRY -> 1.2;
			case PRODUCTION -> 6.0;
			case TRADE_UPKEEP -> 0.05;
			default -> 0.0;
		});

		YamlConfiguration yaml = new YamlConfiguration();
		yaml.set("name", "Fields");
		yaml.set("group", 1);
		yaml.set("modifiers", List.of("PRODUCTION 0 2"));
		branch = new Branch(new Branch("fields", yaml), 2);
	}

	@AfterEach
	void tearDown() {
		GuildModifierOverride.clear();
		Cache.provincesEnabled = savedProvincesEnabled;
		if (savedPlainsCarry == null) {
			Cache.tradeCarry.remove(Terrain.PLAINS);
		} else {
			Cache.tradeCarry.put(Terrain.PLAINS, savedPlainsCarry);
		}
		FactionManager.factions.clear();
		FactionManager.factions.addAll(savedFactions);
	}

	@Test
	void upgradeAndDowngradeKeepLiveStateAndMoveIncome() {
		double upgrade = live.previewUpgradeIncomeExact(guild, branch);
		double downgrade = live.previewDowngradeIncomeExact(guild, branch);

		assertTrue(upgrade > 0, "upgrade delta was " + upgrade);
		assertTrue(downgrade < 0, "downgrade delta was " + downgrade);
		assertEquals(2, branch.getLevel());
		assertEquals(12.5, capital.getProsperity());
		assertTrue(capital.getAllData().isEmpty());
		assertTrue(neighbour.getAllData().isEmpty());
		verify(guild, never()).getTradeBreakdown();
	}

	@Test
	void guildTaxMovesIncomeToTheTreasuryWithoutChangingTheRealmTotal() {
		BranchIncomePreview.Prepared prepared = BranchIncomePreview.prepare(live);
		Map<GuildModifier, Double> current = BranchIncomePreview.modifiers(guild);
		Map<GuildModifier, Double> raised = BranchIncomePreview.adjust(current, branch, branch.getLevel(), 1);
		BranchIncomePreview.Estimate untaxed = BranchIncomePreview.estimate(prepared, guild, current, raised);
		when(faction.getTaxRate(TaxTarget.GUILDS, "fields", true)).thenReturn(50.0);
		BranchIncomePreview.Estimate taxed = BranchIncomePreview.estimate(prepared, guild, current, raised);

		assertTrue(untaxed.own() > 0);
		assertEquals(untaxed.own(), untaxed.realm());
		assertTrue(taxed.own() < untaxed.own());
		assertEquals(untaxed.realm(), taxed.realm(), 0.01);
		assertEquals(2, branch.getLevel());
	}

	@Test
	void theRealmGuildPaysNoGuildTaxOnItsOwnTrade() {
		when(faction.getTaxRate(TaxTarget.GUILDS, "fields", true)).thenReturn(0.0);
		double untaxed = live.previewUpgradeIncomeExact(guild, branch);
		when(faction.getTaxRate(TaxTarget.GUILDS, "fields", true)).thenReturn(50.0);
		when(guild.isBase()).thenReturn(true);

		assertEquals(0.0, BranchIncomePreview.taxFraction(guild));
		assertEquals(untaxed, live.previewUpgradeIncomeExact(guild, branch));
	}

	@Test
	void overrideDoesNotLeakAfterThePreview() {
		live.previewUpgradeIncomeExact(guild, branch);
		org.mockito.Mockito.clearInvocations(guild);

		GuildModifierOverride.resolve(guild, GuildModifier.PRODUCTION);

		verify(guild).getModifier(GuildModifier.PRODUCTION);
	}

	@Test
	void concurrentPreviewsStayOnTheirOwnModifiers() throws Exception {
		BranchIncomePreview.Prepared prepared = BranchIncomePreview.prepare(live);
		Map<GuildModifier, Double> current = BranchIncomePreview.modifiers(guild);
		Map<GuildModifier, Double> raised = BranchIncomePreview.adjust(current, branch, branch.getLevel(), 1);
		Map<GuildModifier, Double> lowered = BranchIncomePreview.adjust(current, branch, branch.getLevel(), -1);

		BranchIncomePreview.Estimate expectedRaise = BranchIncomePreview.estimate(prepared, guild, current, raised);
		BranchIncomePreview.Estimate expectedLower = BranchIncomePreview.estimate(prepared, guild, current, lowered);

		ExecutorService pool = Executors.newFixedThreadPool(2);
		try {
			CountDownLatch start = new CountDownLatch(1);
			Future<BranchIncomePreview.Estimate> raise = pool.submit(() -> {
				start.await();
				return BranchIncomePreview.estimate(prepared, guild, current, raised);
			});
			Future<BranchIncomePreview.Estimate> lower = pool.submit(() -> {
				start.await();
				return BranchIncomePreview.estimate(prepared, guild, current, lowered);
			});
			start.countDown();
			assertEquals(expectedRaise, raise.get(10, TimeUnit.SECONDS));
			assertEquals(expectedLower, lower.get(10, TimeUnit.SECONDS));
		} finally {
			pool.shutdownNow();
		}
		assertEquals(2, branch.getLevel());
		assertEquals(12.5, capital.getProsperity());
	}
}
