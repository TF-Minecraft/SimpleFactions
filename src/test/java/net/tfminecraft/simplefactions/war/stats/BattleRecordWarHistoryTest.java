package net.tfminecraft.simplefactions.war.stats;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Instant;

import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.WarManager;
import net.tfminecraft.simplefactions.mercenary.contract.MercenaryContract;
import net.tfminecraft.simplefactions.mercenary.contract.MercenaryEngagements;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.testsupport.FactionDomainFixture;
import net.tfminecraft.simplefactions.war.battle.engine.core.Battle;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleFactory;
import net.tfminecraft.simplefactions.war.battle.enums.BattleType;
import net.tfminecraft.simplefactions.war.battle.template.BattleTemplate;
import net.tfminecraft.simplefactions.war.core.War;

/** What a battle record tells the war pages about who each fighter fought for. */
class BattleRecordWarHistoryTest {
	private FactionDomainFixture fixture;
	private Faction attacker;
	private Faction defender;

	@BeforeEach
	void setup() {
		fixture = new FactionDomainFixture();
		when(Bukkit.createBossBar(anyString(), any(BarColor.class), any(BarStyle.class)))
				.thenAnswer(call -> mock(BossBar.class));
		attacker = fixture.saved("aureate", "Alice");
		attacker.setFoundedAt(1730000000L);
		defender = fixture.saved("malestroit", "Bob");
	}

	@AfterEach
	void close() {
		fixture.close();
	}

	@Test
	void aFighterKeepsTheirFactionAndAHiredOneNamesTheirHirer() {
		Player alice = fixture.player("Alice");
		Player sellsword = fixture.player("Sellsword");
		Battle battle = BattleFactory.createBlank(BattleType.FIELD, "w9-field-7");
		battle.setWarId(9);
		battle.setProvinceId(7);
		War war = new War(9, attacker, defender);
		MercenaryContract contract = mock(MercenaryContract.class);
		when(contract.getHirer()).thenReturn(defender);
		BattleStats stats = new BattleStats();

		try (MockedStatic<FactionManager> factions = mockStatic(FactionManager.class, CALLS_REAL_METHODS);
				MockedStatic<WarManager> wars = mockStatic(WarManager.class, CALLS_REAL_METHODS);
				MockedStatic<MercenaryEngagements> engagements = mockStatic(MercenaryEngagements.class)) {
			factions.when(() -> FactionManager.getByMember(alice)).thenReturn(attacker);
			factions.when(() -> FactionManager.getByMember(sellsword)).thenReturn(null);
			wars.when(() -> WarManager.getById(9)).thenReturn(war);
			FighterStats own = stats.fighter("alice-key", alice, battle.getSideById(BattleTemplate.ATTACKER_SIDE));
			FighterStats hired = stats.fighter("sell-key", sellsword, battle.getSideById(BattleTemplate.DEFENDER_SIDE));
			engagements.when(() -> MercenaryEngagements.forPlayer(war, "sell-key"))
					.thenReturn(new MercenaryEngagements.Engagement(null, contract));
			engagements.when(() -> MercenaryEngagements.forPlayer(war, "alice-key")).thenReturn(null);

			BattleRecord record = BattleRecord.of(battle, stats, null, "TIME", Instant.parse("2026-11-19T21:00:00Z"));

			assertEquals(7, record.provinceId);
			assertEquals("aureate", own.factionId);
			assertEquals(1730000000L, own.factionFoundedAt);
			assertNull(own.hirerFactionId);
			assertNull(hired.factionId);
			assertNull(hired.factionFoundedAt);
			assertEquals("malestroit", hired.hirerFactionId);
		}
	}

	@Test
	void aFactionWithoutAFoundingStampAndAHirerlessContractLeaveThemNull() {
		Player bob = fixture.player("Bob");
		defender.setFoundedAt(0);
		Battle battle = BattleFactory.createBlank(BattleType.FIELD, "w9-field-8");
		battle.setWarId(9);
		War war = new War(9, attacker, defender);
		BattleStats stats = new BattleStats();

		try (MockedStatic<FactionManager> factions = mockStatic(FactionManager.class, CALLS_REAL_METHODS);
				MockedStatic<WarManager> wars = mockStatic(WarManager.class, CALLS_REAL_METHODS);
				MockedStatic<MercenaryEngagements> engagements = mockStatic(MercenaryEngagements.class)) {
			factions.when(() -> FactionManager.getByMember(bob)).thenReturn(defender);
			wars.when(() -> WarManager.getById(9)).thenReturn(war);
			FighterStats fighter = stats.fighter("bob-key", bob, battle.getSideById(BattleTemplate.DEFENDER_SIDE));
			engagements.when(() -> MercenaryEngagements.forPlayer(war, "bob-key"))
					.thenReturn(new MercenaryEngagements.Engagement(null, null));

			BattleRecord record = BattleRecord.of(battle, stats, null, "TIME", Instant.parse("2026-11-19T21:00:00Z"));

			assertNull(record.provinceId);
			assertEquals("malestroit", fighter.factionId);
			assertNull(fighter.factionFoundedAt);
			assertNull(fighter.hirerFactionId);
		}
	}
}
