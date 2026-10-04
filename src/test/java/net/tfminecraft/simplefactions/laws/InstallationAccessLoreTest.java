package net.tfminecraft.simplefactions.laws;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import net.tfminecraft.simplefactions.enums.FactionModifiers;
import net.tfminecraft.simplefactions.enums.Region;
import net.tfminecraft.simplefactions.enums.Scope;
import net.tfminecraft.simplefactions.objects.FactionModifier;
import net.tfminecraft.simplefactions.utils.LoreWriter;

class InstallationAccessLoreTest {

	@Test
	void lawLoreShowsInstallationAccessAsPercentAndSignsReach() throws Exception {
		YamlConfiguration yaml = new YamlConfiguration();
		yaml.loadFromString("""
				our_territory:
				  - installation_access(0.50)
				  - trade_power(15.0)
				foreign_territory:
				  - installation_access(0.10)
				  - installation_access(-0.25)
				""");
		LawEffect effect = new LawEffect(Scope.DOMESTIC_GUILDS, yaml);
		List<String> lore = new ArrayList<>();
		LoreWriter.writeEffect(Scope.DOMESTIC_GUILDS, effect, lore);
		String plain = lore.stream().map(ChatColor::stripColor).collect(Collectors.joining("\n"));

		assertTrue(plain.contains("Installation Access"));
		assertFalse(plain.contains("Infrastructure Access"));
		assertTrue(plain.contains("(50%)"));
		assertFalse(plain.contains("(+50%)"));
		assertTrue(plain.contains("(+10%)"));
		assertTrue(plain.contains("(-25%)"));
		assertTrue(plain.contains("(15%)"));
		assertFalse(plain.contains("(1500%)"));
	}

	@Test
	void zeroReachIsUnsignedAndGrantsStayFractionsUntilDisplay() {
		String grant = ChatColor.stripColor(new FactionModifier(FactionModifiers.INSTALLATION_ACCESS, 0.35)
				.getString(null, Region.OUR_TERRITORY));
		String reach = ChatColor.stripColor(new FactionModifier(FactionModifiers.INSTALLATION_ACCESS, 0.0)
				.getString(null, Region.FOREIGN_TERRITORY));
		String trade = ChatColor.stripColor(new FactionModifier(FactionModifiers.TRADE_POWER, 20.0).getString());

		assertTrue(grant.contains("Installation Access"));
		assertTrue(grant.contains("(35%)"));
		assertTrue(reach.contains("(0%)"));
		assertFalse(reach.contains("(+0%)"));
		assertTrue(trade.contains("(20%)"));
	}
}
