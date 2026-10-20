package net.tfminecraft.simplefactions.diplomacy.coalition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import net.tfminecraft.simplefactions.inactivity.InactivityService;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.objects.Faction;

class CoalitionStorageAndCostTest extends CoalitionTestBase {
	@TempDir
	Path folder;

	@Test
	void everyCoalitionDetailSurvivesASaveAndLoad() {
		CoalitionRegistry.bind(folder.toFile());
		Coalition league = league();
		league.setOffensive(true);
		league.setBannerPatterns(List.of("RED.BASE"));
		league.invite("late", 99L);
		league.setTargetLostAt(5L);
		league.setTargetLossPausedMillis(7L);
		league.setSingleMemberSince(9L);
		league.setRgb("9,8,7");
		CoalitionRegistry.setCooldown("Gone", CoalitionRegistry.now() + 1000);
		CoalitionRegistry.save();

		CoalitionRegistry.reset();
		CoalitionRegistry.useClock(clock);
		CoalitionRegistry.bind(folder.toFile());
		CoalitionRegistry.load();

		Coalition loaded = CoalitionRegistry.get(league.getId());
		assertEquals("Grand League", loaded.getName());
		assertEquals(List.of("thalendor", "bog", "fig"), loaded.getMemberIds());
		assertEquals(league.joinedAt("fig"), loaded.joinedAt("fig"));
		assertTrue(loaded.isLeader("thalendor"));
		assertTrue(loaded.isTarget("holy"));
		assertTrue(loaded.isOffensive());
		assertEquals(List.of("RED.BASE"), loaded.getBannerPatterns());
		assertEquals(99L, loaded.getInvites().get("late"));
		assertEquals(5L, loaded.getTargetLostAt());
		assertEquals(7L, loaded.getTargetLossPausedMillis());
		assertEquals(9L, loaded.getSingleMemberSince());
		assertEquals("9,8,7", loaded.getRgb());
		assertEquals(league.getCreatedAt(), loaded.getCreatedAt());
		assertNotNull(CoalitionRegistry.cooldownUntil("gone"));
		assertNull(loaded.joinedAt("holy"));
		assertNull(loaded.joinedAt(null));
		assertFalse(loaded.removeMember("holy"));
		assertFalse(loaded.removeInvite("holy"));
	}

	@Test
	void brokenEntriesAreSkippedAndAnUnreadableFileIsNeverOverwritten() throws Exception {
		File file = folder.resolve("coalitions.json").toFile();
		Files.writeString(file.toPath(), """
				{"coalitions":[null,{"id":"x"},
				 {"id":"c1","name":"Kept","leader":"a","target":"b","members":{"a":1,"z":null},"invites":{"q":null}},
				 {"id":"c2","name":"Bare","leader":"a","target":"b","members":null,"invites":null}],
				 "cooldowns":{"a":5}}
				""");
		CoalitionRegistry.bind(folder.toFile());
		CoalitionRegistry.load();
		assertEquals(List.of("a"), CoalitionRegistry.get("c1").getMemberIds());
		assertTrue(CoalitionRegistry.get("c1").getInvites().isEmpty());
		assertEquals(2, CoalitionRegistry.all().size());

		Files.writeString(file.toPath(), "{\"coalitions\":null,\"cooldowns\":null}");
		CoalitionRegistry.load();
		assertTrue(CoalitionRegistry.all().isEmpty());

		Files.writeString(file.toPath(), "null");
		CoalitionRegistry.load();
		Files.writeString(file.toPath(), "{broken");
		CoalitionRegistry.load();
		CoalitionRegistry.add(new Coalition("c3", "New", "a", "b", 1));
		CoalitionRegistry.save();
		assertEquals("{broken", Files.readString(file.toPath()));

		CoalitionRegistry.bind(folder.resolve("missing").toFile());
		CoalitionRegistry.load();
		CoalitionRegistry.save();
		assertTrue(Files.exists(folder.resolve("missing/coalitions.json")));

		// Writing over a directory fails, which is logged and survived.
		Files.createDirectories(folder.resolve("blocked/coalitions.json"));
		CoalitionRegistry.bind(folder.resolve("blocked").toFile());
		CoalitionRegistry.save();
		CoalitionRegistry.bind(null);
		CoalitionRegistry.load();
		CoalitionRegistry.save();
	}

	@Test
	void cooldownsRunOutOnTheirOwn() {
		assertNull(CoalitionRegistry.cooldownUntil(null));
		assertFalse(CoalitionRegistry.clearCooldown(null));
		CoalitionRegistry.setCooldown("Bog", CoalitionRegistry.now() + 10);
		assertNotNull(CoalitionRegistry.cooldownUntil("bog"));
		clock.advance(java.time.Duration.ofSeconds(1));
		assertNull(CoalitionRegistry.cooldownUntil("bog"));
		assertFalse(CoalitionRegistry.clearCooldown("bog"));
		assertFalse(CoalitionRegistry.remove(null));
		assertNull(CoalitionRegistry.get(null));
		CoalitionRegistry.useClock(null);
		assertTrue(CoalitionRegistry.now() > 0);
	}

	@Test
	void settingsComeFromDiplomacyYmlWithSafeDefaults() throws Exception {
		YamlConfiguration yaml = new YamlConfiguration();
		yaml.set("coalition.enabled", false);
		yaml.set("coalition.cost.base", 3.0);
		yaml.set("coalition.cost.exponent", 2.0);
		yaml.set("coalition.cost.leader-multiplier", 1.5);
		yaml.set("coalition.gate-ratio", 0.8);
		yaml.set("coalition.target-loss-days", 3);
		yaml.set("coalition.single-member-days", 2);
		yaml.set("coalition.leave-cooldown-days", 4);
		yaml.set("coalition.invite-expiry-days", 0);
		yaml.set("coalition.name-min-length", 5);
		yaml.set("coalition.name-max-length", 2);
		File file = folder.resolve("diplomacy.yml").toFile();
		yaml.save(file);

		CoalitionSettings.load(file);

		assertFalse(CoalitionSettings.enabled);
		assertEquals(3.0, CoalitionSettings.costBase);
		assertEquals(2.0, CoalitionSettings.costExponent);
		assertEquals(1.5, CoalitionSettings.leaderMultiplier);
		assertEquals(0.8, CoalitionSettings.gateRatio);
		assertEquals(3, CoalitionSettings.targetLossDays);
		assertEquals(2, CoalitionSettings.singleMemberDays);
		assertEquals(4, CoalitionSettings.leaveCooldownDays);
		assertEquals(1, CoalitionSettings.inviteExpiryDays);
		assertEquals(5, CoalitionSettings.nameMinLength);
		assertEquals(5, CoalitionSettings.nameMaxLength);

		CoalitionSettings.load(folder.resolve("absent.yml").toFile());
		assertTrue(CoalitionSettings.enabled);
		assertEquals(5.0, CoalitionSettings.costBase);
		assertEquals(86_400_000L, CoalitionSettings.days(1));
	}

	@Test
	void seatsCostTheBaseUpToTheMedianAndClimbSteeplyAbove() {
		assertEquals(0.0, CoalitionCost.costFor(bog));
		league();
		CoalitionSettings.costBase = 5;
		CoalitionSettings.costExponent = 1.5;
		// Active prestige: bog 124, sanctuary 200, fig 428, holy 1000, thalendor 1044 -> median 428.
		assertEquals(428.0, CoalitionCost.medianPrestige());
		assertEquals(5.0, CoalitionCost.cost(bog, false));
		assertEquals(5.0, CoalitionCost.cost(fig, false));
		assertEquals(5 * Math.pow(1044.0 / 428.0, 1.5), CoalitionCost.cost(thalendor, false), 1e-9);
		// Holy Order counts Sanctuary as part of its realm.
		assertEquals(1200.0, CoalitionCost.realmPrestige(holy));

		CoalitionSettings.leaderMultiplier = 2;
		assertEquals(10.0, CoalitionCost.cost(bog, true));
		assertEquals(10.0, CoalitionCost.costFor(bog) + CoalitionCost.costFor(fig));
		assertEquals(CoalitionCost.cost(thalendor, true), CoalitionCost.costFor(thalendor));
		assertEquals(CoalitionCost.costFor(fig), fig.getDiplomacyHandler().getUsedDiplomaticCapacity(), 1e-9);

		faction("sixth", "Six", 300);
		assertEquals((300 + 428) / 2.0, CoalitionCost.medianPrestige());
	}

	@Test
	void inactiveFactionsAndEmptyPrestigeDoNotCount() {
		for (Faction faction : FactionManager.factions) faction.setPrestige(null);
		assertEquals(0.0, CoalitionCost.medianPrestige());
		assertEquals(1.0, CoalitionCost.ratio(thalendor));
		assertEquals(0.0, CoalitionCost.realmPrestige(null));

		bog.setPrestige(100.0);
		fig.setPrestige(300.0);
		try (MockedStatic<InactivityService> inactivity = mockStatic(InactivityService.class)) {
			inactivity.when(() -> InactivityService.isFullyInactive(any())).thenAnswer(
					call -> call.getArgument(0) == bog.getMembers() || bog.getMembers().equals(call.getArgument(0)));
			assertEquals(300.0, CoalitionCost.medianPrestige());
		}
	}

	@Test
	void aRealmThatLoopsBackIsCountedOnce() {
		Faction loop = faction("loop", "Loo", 50);
		fixture.subject(sanctuary, loop);
		fixture.subject(loop, holy);
		assertEquals(1250.0, CoalitionCost.realmPrestige(holy));
	}
}
