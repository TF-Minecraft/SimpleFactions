package net.tfminecraft.simplefactions.guild.income;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.tfminecraft.simplefactions.diplomacy.DiplomacyHandler;
import net.tfminecraft.simplefactions.diplomacy.RelationType;
import net.tfminecraft.simplefactions.government.Government;
import net.tfminecraft.simplefactions.government.proposal.TaxTarget;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.laws.Law;
import net.tfminecraft.simplefactions.laws.LawGroup;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.ProvinceManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.objects.handler.TaxHandler;

class EconomicPreviewTest {
	private final List<Faction> savedFactions = new ArrayList<>();

	@BeforeEach
	void isolateFactions() {
		savedFactions.addAll(FactionManager.factions);
		FactionManager.factions.clear();
	}

	@AfterEach
	void restore() {
		IncomePreviewContext.clear();
		FactionManager.factions.clear();
		FactionManager.factions.addAll(savedFactions);
	}

	@Test
	void lawPreviewDoesNotChangeTheLiveLaw() {
		LawGroup group = lawGroup("old");
		Law current = group.getLaws().get("old");
		group.setCurrent(current);
		Law proposed = new Law("taxes", "proposed", named("Proposed"));
		Faction faction = faction();
		ProvinceManager live = new ProvinceManager();
		live.start(Map.of());

		EconomicPreview.law(live, faction, group, proposed);

		assertSame(current, group.getCurrent());
	}

	@Test
	void lawBracketIsOnlyVisibleWhileThePreviewIsOpen() {
		Faction faction = faction();
		TaxHandler tax = new TaxHandler(faction, 10, 20, 30, 40, 5);
		Law proposed = new Law("taxes", "cap", guildTaxCap("0-10"));
		LawGroup group = lawGroup("old");

		IncomePreviewContext.open(IncomePreviewContext.law(faction, group, proposed));
		assertEquals(10.0, tax.getTaxRate(TaxTarget.GUILDS, "guild", false));
		IncomePreviewContext.clear();

		assertEquals(20.0, tax.getGuildTax());
		assertEquals(20.0, tax.getTaxRate(TaxTarget.GUILDS, "guild", false));
	}

	@Test
	void taxPreviewDoesNotWriteTheRate() {
		Faction faction = faction();
		TaxHandler tax = new TaxHandler(faction, 10, 20, 30, 40, 5);
		when(faction.getTaxHandler()).thenReturn(tax);

		Map<Guild, Double> deltas = tax.getTaxChangeEffects(TaxTarget.GUILDS, null, 35);

		assertTrue(deltas.isEmpty());
		assertEquals(20.0, tax.getGuildTax());
		assertEquals(35.0, rateWhileOpen(tax, faction));
	}

	@Test
	void tradePreviewDoesNotWriteRelations() {
		Faction origin = mock(Faction.class);
		Faction target = mock(Faction.class);
		when(origin.getId()).thenReturn("origin");
		when(target.getId()).thenReturn("target");
		DiplomacyHandler originHandler = new DiplomacyHandler(origin);
		DiplomacyHandler targetHandler = new DiplomacyHandler(target);
		when(origin.getDiplomacyHandler()).thenReturn(originHandler);
		when(target.getDiplomacyHandler()).thenReturn(targetHandler);
		RelationType agreement = mock(RelationType.class);
		when(agreement.hasLink()).thenReturn(false);

		IncomePreviewContext.open(IncomePreviewContext.trade(origin, target, agreement));
		assertSame(agreement, originHandler.getTradeRelation("target"));
		assertTrue(originHandler.hasTradeRelation("target"));
		IncomePreviewContext.clear();

		assertNull(originHandler.getTradeRelation("target"));
		assertTrue(originHandler.getTradeRelations().isEmpty());
	}

	@Test
	void favourPreviewDoesNotToggleTheGuild() {
		Faction host = faction();
		Guild guild = mock(Guild.class);
		when(guild.isFavoured()).thenReturn(false);
		when(guild.isRepressed()).thenReturn(false);
		when(guild.getFaction()).thenReturn(host);
		ProvinceManager live = new ProvinceManager();
		live.start(Map.of());

		EconomicPreview.favour(live, guild, true);

		verify(guild, never()).setFavoured(true);
		verify(guild, never()).setRepressed(true);
	}

	private static double rateWhileOpen(TaxHandler tax, Faction faction) {
		IncomePreviewContext.open(IncomePreviewContext.tax(faction, TaxTarget.GUILDS, null, 35));
		try {
			return tax.getTaxRate(TaxTarget.GUILDS, "guild", false);
		} finally {
			IncomePreviewContext.clear();
		}
	}

	private static Faction faction() {
		Faction faction = mock(Faction.class);
		Government government = mock(Government.class);
		when(faction.getGovernment()).thenReturn(government);
		when(government.getTaxEfficiency()).thenReturn(1.0);
		return faction;
	}

	private static LawGroup lawGroup(String lawId) {
		YamlConfiguration config = new YamlConfiguration();
		config.set("name", "Taxes");
		config.createSection("laws." + lawId);
		config.set("laws." + lawId + ".name", lawId);
		return new LawGroup("taxes", config);
	}

	private static YamlConfiguration named(String name) {
		YamlConfiguration config = new YamlConfiguration();
		config.set("name", name);
		return config;
	}

	private static YamlConfiguration guildTaxCap(String range) {
		YamlConfiguration config = named("Cap");
		config.set("effects.FACTION.brackets.GUILD_TAX", range);
		return config;
	}
}
