package net.tfminecraft.simplefactions.guild.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.tfminecraft.simplefactions.diplomacy.RelationType;
import net.tfminecraft.simplefactions.loaders.RelationLoader;

class InstallationTradeCopyTest {

	private List<RelationType> previousTypes;

	@BeforeEach
	void setUp() {
		previousTypes = new ArrayList<>(RelationLoader.types);
		RelationLoader.types.clear();
	}

	@AfterEach
	void tearDown() {
		RelationLoader.types.clear();
		RelationLoader.types.addAll(previousTypes);
	}

	@Test
	void equalAgreementIsFullUseBothWays() {
		assertEquals("Installations: full use both ways",
				InstallationTradeCopy.agreementLine(1.0, 1.0, false));
	}

	@Test
	void unequalTreatyIsWordedFromTheViewer() {
		assertEquals("Installations: we use theirs fully, they use ours at half",
				InstallationTradeCopy.agreementLine(1.0, 0.5, false));
		assertEquals("Installations: we use theirs at half, they use ours fully",
				InstallationTradeCopy.agreementLine(0.5, 1.0, false));
	}

	@Test
	void embargoIsClosedBothWays() {
		assertEquals("Installations: closed both ways",
				InstallationTradeCopy.agreementLine(0, 0, true));
		assertEquals("Installations: closed both ways",
				InstallationTradeCopy.agreementLine(1.0, 1.0, true));
	}

	@Test
	void otherValuesUsePercentsAndZeroBothWaysIsOmitted() {
		assertEquals("Installations: we use theirs at 35%, they use ours at 10%",
				InstallationTradeCopy.agreementLine(0.35, 0.10, false));
		assertEquals("Installations: we use theirs at 12.5%, they use ours at 0%",
				InstallationTradeCopy.agreementLine(0.125, 0, false));
		assertNull(InstallationTradeCopy.agreementLine(0, 0, false));
	}

	@Test
	void relationTypeUsesItsLinkOrTheTypeThatLinksToIt() {
		register("""
				unequal_treaty_leader:
				  name: Leader
				  mutual: true
				  link: unequal_treaty_subject
				  installation-access: 1.0
				unequal_treaty_subject:
				  name: Subject
				  installation-access: 0.5
				trade_agreement:
				  name: Trade
				  mutual: true
				  installation-access: 1.0
				embargo:
				  name: Embargo
				  blocks-installations: true
				partial:
				  name: Partial
				  mutual: true
				  link: partial_other
				  installation-access: 0.2
				partial_other:
				  name: Partial Other
				  installation-access: 0.8
				quiet:
				  name: Quiet
				  mutual: true
				""");

		assertEquals("Installations: we use theirs fully, they use ours at half",
				InstallationTradeCopy.agreementLine(RelationLoader.getType("unequal_treaty_leader")));
		assertEquals("Installations: we use theirs at half, they use ours fully",
				InstallationTradeCopy.agreementLine(RelationLoader.getType("unequal_treaty_subject")));
		assertEquals("Installations: full use both ways",
				InstallationTradeCopy.agreementLine(RelationLoader.getType("trade_agreement")));
		assertEquals("Installations: closed both ways",
				InstallationTradeCopy.agreementLine(RelationLoader.getType("embargo")));
		assertEquals("Installations: we use theirs at 20%, they use ours at 80%",
				InstallationTradeCopy.agreementLine(RelationLoader.getType("partial")));
		assertNull(InstallationTradeCopy.agreementLine(RelationLoader.getType("quiet")));
		assertNull(InstallationTradeCopy.agreementLine((RelationType) null));
	}

	@Test
	void unresolvedLinkOmitsTheInstallationLine() {
		register("""
				broken:
				  name: Broken
				  mutual: true
				  link: missing_agreement
				  installation-access: 1.0
				pointed:
				  name: Pointed
				  link: also_missing
				  installation-access: 0.25
				back:
				  name: Back
				  link: pointed
				  installation-access: 0.75
				""");

		assertNull(InstallationTradeCopy.agreementLine(RelationLoader.getType("broken")));
		assertEquals("Installations: we use theirs at 25%, they use ours at 75%",
				InstallationTradeCopy.agreementLine(RelationLoader.getType("pointed")));
	}

	private static void register(String types) {
		YamlConfiguration yaml = new YamlConfiguration();
		try {
			yaml.loadFromString("types:\n" + indent(types));
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
		for (String key : yaml.getConfigurationSection("types").getKeys(false)) {
			RelationLoader.types.add(new RelationType(key, yaml.getConfigurationSection("types." + key)));
		}
	}

	private static String indent(String body) {
		StringBuilder out = new StringBuilder();
		for (String line : body.stripIndent().split("\n", -1)) {
			out.append("  ").append(line).append('\n');
		}
		return out.toString();
	}
}
