package net.tfminecraft.simplefactions.war.truce;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.war.civilwar.CivilWarSnapshot;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.war.enums.WarEndReason;

class TruceServiceTest {
	private static final Instant NOW = Instant.parse("2026-10-20T12:00:00Z");

	private final List<Faction> savedFactions = new ArrayList<>();
	private int savedDays;
	private Faction iron;
	private Faction river;
	private Faction ironSubject;
	private Faction riverBacker;

	@BeforeEach
	void setUp() {
		savedFactions.addAll(FactionManager.factions);
		FactionManager.factions.clear();
		savedDays = Cache.warTruceDays;
		Cache.warTruceDays = 10;
		iron = faction("iron");
		river = faction("river");
		ironSubject = faction("ironsubject");
		riverBacker = faction("riverbacker");
		TruceService.useClock(Clock.fixed(NOW, ZoneOffset.UTC));
	}

	@AfterEach
	void tearDown() {
		FactionManager.factions.clear();
		FactionManager.factions.addAll(savedFactions);
		Cache.warTruceDays = savedDays;
		TruceService.reset();
	}

	@ParameterizedTest
	@EnumSource(value = WarEndReason.class, names = {"WHITE_PEACE", "ATTACKER_VICTORY", "DEFENDER_VICTORY"})
	void endedWarsGiveEveryOpposingPairATruce(WarEndReason reason) {
		War war = new War(7, iron, river);
		war.getAttackers().getMainParticipants().get(0).getSubjects().add(ironSubject);
		war.getDefenders().getMainParticipants().get(0).addBacker(riverBacker);

		TruceService.onWarEnded(war, reason);

		Instant until = NOW.plus(Duration.ofDays(10));
		assertEquals(until, TruceService.getTruce(iron, river));
		assertEquals(until, TruceService.getTruce(iron, riverBacker));
		assertEquals(until, TruceService.getTruce(ironSubject, river));
		assertEquals(until, TruceService.getTruce(ironSubject, riverBacker));
		assertFalse(TruceService.hasTruce(iron, ironSubject));
		assertFalse(TruceService.hasTruce(river, riverBacker));
		assertEquals(4, TruceService.list(null).size());
		assertEquals(7, TruceService.listFor(iron).getFirst().warId());
	}

	@Test
	void staffEndingAWarLeavesNoTruce() {
		TruceService.onWarEnded(new War(7, iron, river), WarEndReason.ADMIN_END);
		assertFalse(TruceService.hasTruce(iron, river));
		assertTrue(TruceService.list(null).isEmpty());
	}

	@Test
	void civilWarsGiveTheHostACooldownInsteadOfPairTruces() {
		Faction host = faction("host");
		Faction rebels = faction("rebels");
		War war = new War(3, rebels, host);
		CivilWarSnapshot snapshot = new CivilWarSnapshot();
		snapshot.setHostFactionId("host");
		snapshot.setTempRebelFactionId("rebels");
		war.setCivilWarSnapshot(snapshot);

		TruceService.onWarEnded(war, WarEndReason.DEFENDER_VICTORY);

		assertFalse(TruceService.hasTruce(host, rebels));
		assertEquals(NOW.plus(Duration.ofDays(10)), TruceService.civilWarCooldownUntil(host));
		assertEquals(Duration.ofDays(10), TruceService.civilWarCooldownRemaining(host));
		assertNull(TruceService.civilWarCooldownUntil(rebels));
	}

	@Test
	void lookupIsSymmetricAndCaseInsensitive() {
		TruceService.record("Iron", "river", NOW.plusSeconds(60), 1);
		assertTrue(TruceService.hasTruce(iron, river));
		assertTrue(TruceService.hasTruce(river, iron));
		assertEquals(Duration.ofSeconds(60), TruceService.remaining(river, iron));
		assertEquals("river", TruceService.listFor(iron).getFirst().otherId("iron"));
		assertFalse(TruceService.record("iron", "IRON", NOW.plusSeconds(60), 1));
	}

	@Test
	void anExistingTruceKeepsTheLaterExpiry() {
		Instant later = NOW.plus(Duration.ofDays(5));
		TruceService.record("iron", "river", later, 1);
		assertFalse(TruceService.record("river", "iron", NOW.plus(Duration.ofDays(2)), 2));
		assertEquals(later, TruceService.getTruce(iron, river));
		assertTrue(TruceService.record("river", "iron", NOW.plus(Duration.ofDays(9)), 3));
		assertEquals(3, TruceService.listFor(river).getFirst().warId());
		assertEquals(1, TruceService.list(null).size());
	}

	@Test
	void trucesAndCooldownsExpireOnTheirOwn() {
		TruceService.record("iron", "river", NOW.plus(Duration.ofHours(1)), 1);
		TruceService.recordCivilWarCooldown("iron", NOW.plus(Duration.ofHours(1)), 2);
		TruceService.useClock(Clock.fixed(NOW.plus(Duration.ofHours(1)), ZoneOffset.UTC));
		assertFalse(TruceService.hasTruce(iron, river));
		assertEquals(Duration.ZERO, TruceService.remaining(iron, river));
		assertNull(TruceService.civilWarCooldownUntil(iron));
		assertTrue(TruceService.list(null).isEmpty());
	}

	@Test
	void clearRemovesOnlyThatPair() {
		TruceService.record("iron", "river", NOW.plus(Duration.ofDays(1)), 1);
		TruceService.record("iron", "riverbacker", NOW.plus(Duration.ofDays(1)), 1);
		assertTrue(TruceService.clear(river, iron));
		assertFalse(TruceService.clear(river, iron));
		assertFalse(TruceService.hasTruce(iron, river));
		assertTrue(TruceService.hasTruce(iron, riverBacker));
	}

	@Test
	void remainingTimeReadsInTheTwoLargestUnits() {
		assertEquals("6d 4h", TruceService.formatRemaining(Duration.ofDays(6).plusHours(4).plusMinutes(10)));
		assertEquals("3h 12m", TruceService.formatRemaining(Duration.ofHours(3).plusMinutes(12)));
		assertEquals("1m", TruceService.formatRemaining(Duration.ofSeconds(5)));
		assertEquals("0m", TruceService.formatRemaining(Duration.ZERO));
		assertEquals("1d 0h", TruceService.formatRemaining(Duration.ofDays(1)));
	}

	@Test
	void trucesSurviveARestartAndExpiredEntriesAreDroppedOnLoad(@TempDir Path folder) throws Exception {
		TruceService.bind(folder.toFile());
		TruceService.load();
		TruceService.onWarEnded(new War(4, iron, river), WarEndReason.WHITE_PEACE);
		TruceService.record("iron", "gone", NOW.plus(Duration.ofHours(2)), 5);
		TruceService.recordCivilWarCooldown("host", NOW.plus(Duration.ofDays(3)), 6);
		TruceService.save();
		File file = folder.resolve("truces.json").toFile();
		assertTrue(file.exists());

		TruceService.reset();
		TruceService.useClock(Clock.fixed(NOW.plus(Duration.ofHours(3)), ZoneOffset.UTC));
		TruceService.bind(folder.toFile());
		TruceService.load();

		assertEquals(NOW.plus(Duration.ofDays(10)), TruceService.getTruce(river, iron));
		assertEquals(4, TruceService.listFor(iron).getFirst().warId());
		assertEquals(1, TruceService.list(null).size());
		Faction host = faction("host");
		assertEquals(NOW.plus(Duration.ofDays(3)), TruceService.civilWarCooldownUntil(host));
		assertFalse(Files.readString(file.toPath(), StandardCharsets.UTF_8).contains("gone"));
	}

	@Test
	void anUnreadableFileIsNotOverwritten(@TempDir Path folder) throws Exception {
		Path file = folder.resolve("truces.json");
		Files.writeString(file, "{not json", StandardCharsets.UTF_8);
		TruceService.bind(folder.toFile());
		TruceService.load();
		TruceService.onWarEnded(new War(4, iron, river), WarEndReason.WHITE_PEACE);
		assertTrue(TruceService.hasTruce(iron, river));
		assertEquals("{not json", Files.readString(file, StandardCharsets.UTF_8));
	}

	@Test
	void unparsableTimesAreDroppedOnLoad(@TempDir Path folder) throws Exception {
		Files.writeString(folder.resolve("truces.json"),
				"{\"truces\":[{\"first\":\"iron\",\"second\":\"river\",\"until\":\"soon\"}],"
						+ "\"civilWarCooldowns\":{\"host\":{\"until\":\"later\",\"warId\":1}}}",
				StandardCharsets.UTF_8);
		TruceService.bind(folder.toFile());
		TruceService.load();
		assertFalse(TruceService.hasTruce(iron, river));
		assertTrue(TruceService.list(null).isEmpty());
		assertEquals(null, TruceService.civilWarCooldownUntil(faction("host")));
	}

	@Test
	void aFailedSaveKeepsTheTruceInMemory(@TempDir Path folder) throws Exception {
		File blocked = folder.resolve("blocked").toFile();
		Files.writeString(blocked.toPath(), "a file, not a folder", StandardCharsets.UTF_8);
		TruceService.bind(blocked);
		TruceService.onWarEnded(new War(4, iron, river), WarEndReason.WHITE_PEACE);
		assertTrue(TruceService.hasTruce(iron, river));
	}

	private Faction faction(String id) {
		Faction faction = mock(Faction.class);
		when(faction.getId()).thenReturn(id);
		when(faction.getName()).thenReturn(id);
		when(faction.getRelations()).thenReturn(new HashMap<>());
		when(faction.getMembers()).thenReturn(new ArrayList<>());
		FactionManager.factions.add(faction);
		return faction;
	}
}
