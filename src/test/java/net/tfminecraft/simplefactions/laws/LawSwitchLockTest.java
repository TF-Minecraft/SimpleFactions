package net.tfminecraft.simplefactions.laws;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.objects.handler.LawHandler;

class LawSwitchLockTest {

	private static final long DAY = 86_400_000L;
	private static final long NOW = 1_000L * DAY;

	private final double savedDays = Cache.lawSwitchLockDays;

	@AfterEach
	void restore() {
		Cache.lawSwitchLockDays = savedDays;
	}

	@Test
	void neverSwitched_notLocked() {
		Cache.lawSwitchLockDays = 3;
		assertEquals(0, lawGroup().lockRemaining(NOW));
	}

	@Test
	void switching_startsLockForConfiguredDays() {
		Cache.lawSwitchLockDays = 3;
		LawGroup group = lawGroup();
		group.switchTo(group.getLaw("high"), NOW);

		assertEquals(3 * DAY, group.lockRemaining(NOW));
		assertEquals(DAY, group.lockRemaining(NOW + 2 * DAY));
		assertEquals(0, group.lockRemaining(NOW + 3 * DAY));
	}

	@Test
	void reapplyingSameLaw_doesNotRestartLock() {
		Cache.lawSwitchLockDays = 3;
		LawGroup group = lawGroup();
		group.switchTo(group.getLaw("high"), NOW);
		group.switchTo(group.getLaw("high"), NOW + 2 * DAY);

		assertEquals(NOW, group.getChangedAt());
	}

	@Test
	void setCurrent_doesNotStartLock() {
		Cache.lawSwitchLockDays = 3;
		LawGroup group = lawGroup();
		group.setCurrent(group.getLaw("high"));

		assertEquals(0, group.lockRemaining(NOW));
	}

	@Test
	void zeroDays_disablesLock() {
		Cache.lawSwitchLockDays = 0;
		LawGroup group = lawGroup();
		group.switchTo(group.getLaw("high"), NOW);

		assertEquals(0, group.lockRemaining(NOW));
	}

	@Test
	void lockReason_blocksOtherLawsInLockedGroup() {
		Cache.lawSwitchLockDays = 3;
		LawGroup group = lawGroup();
		group.switchTo(group.getLaw("high"), NOW);
		Faction faction = factionWith(group);

		String reason = CanHaveLaw.lockReason(faction, group.getLaw("low"), NOW + DAY);
		assertNotNull(reason);
		assertTrue(reason.contains("2d 0h"), reason);
		assertNull(CanHaveLaw.lockReason(faction, group.getLaw("low"), NOW + 3 * DAY));
	}

	@Test
	void lockReason_exemptsCurrentLaw() {
		Cache.lawSwitchLockDays = 3;
		LawGroup group = lawGroup();
		group.switchTo(group.getLaw("high"), NOW);

		assertNull(CanHaveLaw.lockReason(factionWith(group), group.getLaw("high"), NOW + DAY));
	}

	@Test
	void blockReason_currentLawStillAvailableWhileLocked() {
		Cache.lawSwitchLockDays = 3;
		LawGroup group = lawGroup();
		group.switchTo(group.getLaw("high"), System.currentTimeMillis());
		Faction faction = factionWith(group);

		assertNull(CanHaveLaw.blockReason(faction, group.getLaw("high")));
		assertNotNull(CanHaveLaw.blockReason(faction, group.getLaw("low")));
	}

	@Test
	void formatRemaining_roundsUpToMinutes() {
		assertEquals("2d 5h", CanHaveLaw.formatRemaining(2 * DAY + 5 * 3_600_000L + 1));
		assertEquals("3h 1m", CanHaveLaw.formatRemaining(3 * 3_600_000L + 1));
		assertEquals("1m", CanHaveLaw.formatRemaining(1));
	}

	private static Faction factionWith(LawGroup group) {
		Faction faction = mock(Faction.class);
		LawHandler handler = mock(LawHandler.class);
		when(faction.getLawHandler()).thenReturn(handler);
		when(handler.getGroup("taxes")).thenReturn(group);
		when(handler.getCurrentLaws()).thenReturn(List.of(group.getCurrent()));
		return faction;
	}

	private static LawGroup lawGroup() {
		YamlConfiguration config = new YamlConfiguration();
		config.set("name", "Taxes");
		for (String id : List.of("low", "high")) {
			config.createSection("laws." + id);
			config.set("laws." + id + ".name", id);
		}
		LawGroup group = new LawGroup("taxes", config);
		group.setCurrent(group.getLaw("low"));
		return group;
	}
}
