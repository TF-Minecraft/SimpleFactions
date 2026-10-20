package net.tfminecraft.simplefactions.diplomacy.coalition;

import java.nio.file.Files;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import net.tfminecraft.simplefactions.diplomacy.Relation;
import net.tfminecraft.simplefactions.diplomacy.RelationType;
import net.tfminecraft.simplefactions.loaders.RelationLoader;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.testsupport.FactionDomainFixture;
import net.tfminecraft.simplefactions.testsupport.PersistenceFilesFixture;
import net.tfminecraft.simplefactions.war.truce.TruceService;

/**
 * The example from the design: Thalendor, the Bog and FIG against the Holy Order, which
 * rules Sanctuary. Every faction is real; only the Paper boundary is simulated.
 */
abstract class CoalitionTestBase {
	/** A clock tests move by hand. */
	static final class MovingClock extends Clock {
		Instant now = Instant.parse("2026-10-20T12:00:00Z");

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return now;
		}

		void advance(Duration duration) {
			now = now.plus(duration);
		}
	}

	PersistenceFilesFixture files;
	FactionDomainFixture fixture;
	MovingClock clock;
	RelationType ally;
	Faction holy;
	Faction sanctuary;
	Faction thalendor;
	Faction bog;
	Faction fig;
	Player hana;
	Player tom;
	Player bea;
	Player finn;

	@BeforeEach
	void setUpCoalitions() throws Exception {
		// Wars save themselves, so they write into a throwaway data folder.
		files = new PersistenceFilesFixture();
		Files.createDirectories(files.root.resolve("Data"));
		fixture = new FactionDomainFixture();
		CoalitionRegistry.reset();
		CoalitionSettings.reset();
		CoalitionSettings.costBase = 0;
		CoalitionService.resetTimer();
		CoalitionWars.reset();
		clock = new MovingClock();
		CoalitionRegistry.useClock(clock);
		ally = fixture.relationType("ally", Map.of("link", "ally"));
		holy = faction("holy", "Hana", 1000);
		sanctuary = faction("sanctuary", "Sid", 200);
		fixture.subject(holy, sanctuary);
		thalendor = faction("thalendor", "Tom", 1044);
		bog = faction("bog", "Bea", 124);
		fig = faction("fig", "Finn", 428);
		hana = fixture.player("Hana");
		tom = fixture.player("Tom");
		bea = fixture.player("Bea");
		finn = fixture.player("Finn");
	}

	@AfterEach
	void tearDownCoalitions() throws Exception {
		CoalitionRegistry.reset();
		CoalitionSettings.reset();
		CoalitionService.resetTimer();
		CoalitionWars.reset();
		TruceService.reset();
		try {
			fixture.close();
		} finally {
			files.close();
		}
	}

	Faction faction(String id, String leader, double prestige) {
		Faction faction = fixture.saved(id, leader);
		faction.addMember(leader);
		faction.setPrestige(prestige);
		return faction;
	}

	void allies(Faction a, Faction b) {
		a.setRelation(b, new Relation(ally, RelationLoader.getDefaultAttitude()));
		b.setRelation(a, new Relation(ally, RelationLoader.getDefaultAttitude()));
	}

	/** Thalendor leads, with the Bog and FIG in, against the Holy Order. */
	Coalition league() {
		CoalitionService.Result created = CoalitionService.create(thalendor, "Grand League", holy);
		if (!created.ok()) throw new AssertionError(created.message());
		Coalition league = CoalitionService.of(thalendor);
		join(league, bog);
		join(league, fig);
		return league;
	}

	void join(Coalition coalition, Faction faction) {
		CoalitionService.invite(CoalitionService.leader(coalition), faction);
		CoalitionService.Result joined = CoalitionService.join(faction, coalition);
		if (!joined.ok()) throw new AssertionError(joined.message());
	}
}
