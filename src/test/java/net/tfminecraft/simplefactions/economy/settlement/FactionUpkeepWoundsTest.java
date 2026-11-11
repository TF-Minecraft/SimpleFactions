package net.tfminecraft.simplefactions.economy.settlement;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import net.tfminecraft.simplefactions.army.Regiment;
import net.tfminecraft.simplefactions.managers.WarManager;
import net.tfminecraft.simplefactions.testsupport.FactionDomainFixture;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.war.wounds.WarLedger;

class FactionUpkeepWoundsTest {
	/** Unpaid upkeep disbands only slots that cost something; wounded slots wait for the war's end. */
	@ParameterizedTest
	@ValueSource(booleans = { true, false })
	void unpaidUpkeepKeepsWoundedSlots(boolean bank) throws Exception {
		try (var fixture = new FactionDomainFixture()) {
			var faction = fixture.saved("realm", UUID.randomUUID().toString());
			var enemy = fixture.saved("enemy", UUID.randomUUID().toString());
			Regiment guard = new Regiment(fixture.regiment("guard", false, 1, 5));
			guard.setCurrentSlots(6);
			faction.getMilitary().getRegiments().add(guard);
			War war = new War(990088, faction, enemy);
			war.getLedger().addAdjustment(new WarLedger.Adjustment("realm", "guard", 10, "staff", null, "t"));
			WarManager.get().add(war);
			try {
				if (!bank) {
					SettlementGuilds.capital(faction).setBank(null);
				}
				var upkeep = FactionUpkeepPlan.calculate(faction, 0);
				// 1 law slot, 2 wounded paid slots kept; the 3 healthy paid slots cannot be paid for.
				assertEquals(Map.of("guard", 3), upkeep.regimentSlots());
				assertEquals(0, upkeep.charge());
			} finally {
				WarManager.get().remove(war);
			}
		}
	}
}
