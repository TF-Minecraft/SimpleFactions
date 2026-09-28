package net.tfminecraft.simplefactions.laws;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.enums.FactionModifiers;
import net.tfminecraft.simplefactions.enums.Region;
import net.tfminecraft.simplefactions.enums.Scope;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.objects.FactionModifier;
import net.tfminecraft.simplefactions.objects.handler.LawHandler;

/** Favoured and repressed guilds get their own scope's base effect, not the vassal one. */
class FavourRepressBaseEffectTest {

	private Map<Scope, LawEffect> savedBaseEffects;

	@BeforeEach
	void setUp() {
		savedBaseEffects = Cache.baseEffects;
		Cache.baseEffects = new HashMap<>();
		Cache.baseEffects.put(Scope.FAVOURED_GUILDS, tradePower(Scope.FAVOURED_GUILDS, 20));
		Cache.baseEffects.put(Scope.REPRESSED_GUILDS, tradePower(Scope.REPRESSED_GUILDS, -15));
		Cache.baseEffects.put(Scope.FAVOURED_VASSALS, tradePower(Scope.FAVOURED_VASSALS, 15));
		Cache.baseEffects.put(Scope.REPRESSED_VASSALS, tradePower(Scope.REPRESSED_VASSALS, -10));
	}

	@AfterEach
	void restore() {
		Cache.baseEffects = savedBaseEffects;
	}

	@Test
	void favouredDomesticGuild_getsFavouredGuildsEffect() {
		assertEquals(20.0, tradePowerFor(guild(true, false), Scope.DOMESTIC_GUILDS));
	}

	@Test
	void repressedDomesticGuild_getsRepressedGuildsEffect() {
		assertEquals(-15.0, tradePowerFor(guild(false, true), Scope.DOMESTIC_GUILDS));
	}

	@Test
	void favouredVassal_getsFavouredVassalsEffect() {
		assertEquals(15.0, tradePowerFor(guild(true, false), Scope.VASSALS));
		assertEquals(15.0, tradePowerFor(guild(true, false), Scope.VASSAL_GUILDS));
	}

	@Test
	void repressedVassal_getsRepressedVassalsEffect() {
		assertEquals(-10.0, tradePowerFor(guild(false, true), Scope.VASSALS));
		assertEquals(-10.0, tradePowerFor(guild(false, true), Scope.VASSAL_GUILDS));
	}

	@Test
	void ordinaryGuild_getsNoBaseEffect() {
		assertEquals(0.0, tradePowerFor(guild(false, false), Scope.DOMESTIC_GUILDS));
	}

	private static double tradePowerFor(Guild guild, Scope scope) {
		try (MockedStatic<FactionManager> factions = mockStatic(FactionManager.class)) {
			factions.when(() -> FactionManager.getGuildByString("g")).thenReturn(guild);
			LawHandler handler = new LawHandler(mock(Faction.class), List.of());
			double total = 0.0;
			for (FactionModifier m : handler.getLawModifiers("g", scope, Region.OUR_TERRITORY)) {
				if (m.getType() == FactionModifiers.TRADE_POWER) total += m.getAmount();
			}
			return total;
		}
	}

	private static Guild guild(boolean favoured, boolean repressed) {
		Guild guild = mock(Guild.class);
		when(guild.isFavoured()).thenReturn(favoured);
		when(guild.isRepressed()).thenReturn(repressed);
		return guild;
	}

	private static LawEffect tradePower(Scope scope, double amount) {
		LawEffect effect = new LawEffect(scope, new YamlConfiguration());
		effect.addModifier(Region.OUR_TERRITORY, new FactionModifier(FactionModifiers.TRADE_POWER, amount));
		return effect;
	}
}
