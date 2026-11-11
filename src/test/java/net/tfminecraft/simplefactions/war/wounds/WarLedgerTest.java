package net.tfminecraft.simplefactions.war.wounds;

import static net.tfminecraft.simplefactions.war.wounds.TestForces.faction;
import static net.tfminecraft.simplefactions.war.wounds.TestForces.mercenary;
import static net.tfminecraft.simplefactions.war.wounds.TestForces.militia;
import static net.tfminecraft.simplefactions.war.wounds.TestForces.paid;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import net.tfminecraft.simplefactions.database.JsonUtil;

class WarLedgerTest {
	@Test
	void factionWoundsAreThePaidShareOfEveryBattleAndAdjustment() {
		WarLedger ledger = new WarLedger();
		ledger.addBattle("b1", "fought", "t1", 7,
				new WarLedger.SideRecord(30, List.of(faction("ATK", militia(6, 4)), mercenary("guild", 2))),
				new WarLedger.SideRecord(10, List.of(faction("def", paid("professional", 5)))));

		// 30 attacker deaths: 25 on the faction (15 law, 10 paid), 5 on the mercenaries.
		assertEquals(10, ledger.factionWoundLives("atk", "MILITIA"));
		assertEquals(10, ledger.factionWoundLives("def", "professional"));
		assertEquals(Map.of("atk", Map.of("militia", 10), "def", Map.of("professional", 10)), ledger.factionWoundLives());
		assertSame(ledger.factionWoundLives(), ledger.factionWoundLives(), "the totals are kept until something changes");

		ledger.addAdjustment(new WarLedger.Adjustment("def", "professional", -15, "staff", null, "t2"));
		ledger.addAdjustment(new WarLedger.Adjustment("atk", "garrison", 4, "staff", "bug", "t2"));
		assertEquals(0, ledger.factionWoundLives("def", "professional"));
		assertEquals(Map.of("atk", Map.of("militia", 10, "garrison", 4)), ledger.factionWoundLives());
		assertEquals(0, ledger.factionWoundLives(null, "x"));
		assertEquals(0, ledger.factionWoundLives("x", null));
		assertEquals(0, ledger.factionWoundLives("nobody", "x"));
	}

	@Test
	void editingDeathsChangesOnlyThatSideAndKeepsTheBattle() {
		WarLedger ledger = new WarLedger();
		ledger.addBattle(null, "autoresolve", "t1", null,
				new WarLedger.SideRecord(10, List.of(faction("atk", paid("p", 4)))),
				new WarLedger.SideRecord(5, List.of(faction("def", paid("p", 4)))));
		ledger.addBattle("b2", "fought", "t2", 3, new WarLedger.SideRecord(0, null), new WarLedger.SideRecord(-4, null));

		WarLedger.BattleEntry before = ledger.setDeaths(1, WarLedger.DEFENDER, 15);

		assertEquals(5, before.defender().deaths());
		assertEquals(15, ledger.battle(1).defender().deaths());
		assertEquals(10, ledger.battle(1).attacker().deaths());
		assertEquals(15, ledger.factionWoundLives("def", "p"));
		ledger.setDeaths(1, WarLedger.ATTACKER, 0);
		assertEquals(0, ledger.factionWoundLives("atk", "p"));
		assertNull(ledger.setDeaths(9, WarLedger.ATTACKER, 1));
		assertNull(ledger.battle(9));
		assertEquals(2, ledger.battle(2).number());
		assertEquals(0, ledger.battle(2).defender().deaths());
		assertTrue(ledger.battle(2).attacker().forces().isEmpty());
	}

	@Test
	void theLedgerSurvivesSavingAsJson() {
		WarLedger ledger = new WarLedger();
		ledger.addBattle("b1", "fought", "t1", 7,
				new WarLedger.SideRecord(30, List.of(faction("atk", militia(6, 4)), mercenary("guild", 2))),
				new WarLedger.SideRecord(0, List.of()));
		ledger.addAdjustment(new WarLedger.Adjustment("atk", "militia", 3, "staff", "why", "t2"));
		ledger.addNote(new WarLedger.Note("t2", "staff", "added"));

		String json = JsonUtil.GSON.toJson(ledger.toData());
		WarLedger loaded = WarLedger.fromData(JsonUtil.GSON.fromJson(json, WarLedger.Data.class));

		assertEquals(ledger.battles(), loaded.battles());
		assertEquals(ledger.adjustments(), loaded.adjustments());
		assertEquals(ledger.notes(), loaded.notes());
		assertEquals(13, loaded.factionWoundLives("atk", "militia"));
		assertFalse(loaded.isSettled());
		loaded.markSettled();
		assertTrue(WarLedger.fromData(loaded.toData()).isSettled());
		assertTrue(WarLedger.fromData(null).battles().isEmpty());
		assertTrue(WarLedger.fromData(new WarLedger.Data(null, null, null, false)).notes().isEmpty());
		WarLedger partial = WarLedger.fromData(new WarLedger.Data(
				List.of(new WarLedger.BattleEntry(1, null, "staff", "t", null, null, null)), null, null, false));
		assertTrue(partial.factionWoundLives().isEmpty(), "a battle saved without sides wounds nobody");
	}
}
