package net.tfminecraft.simplefactions.economy.settlement;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import net.tfminecraft.simplefactions.database.Database;
import net.tfminecraft.simplefactions.database.JsonUtil;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.identity.Members;
import net.tfminecraft.simplefactions.loaders.RankLoader;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.player.PlayerEconomyManager;
import net.tfminecraft.simplefactions.prestige.MemberPlaytime;
import net.tfminecraft.simplefactions.prestige.RecentPlaytime;
import net.tfminecraft.simplefactions.testsupport.FactionDomainFixture;
import net.tfminecraft.simplefactions.testsupport.NameKeyedMembers;
import net.tfminecraft.simplefactions.testsupport.TestTreasuries;
import net.tfminecraft.tlibs.character.CharacterInfo;
import net.tfminecraft.tlibs.character.CharacterRef;
import net.tfminecraft.tlibs.character.CharacterSession;
import net.tfminecraft.tlibs.character.CharacterStatus;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The settlement's guild loop runs one prestige pass per guild. It now looks every member's
 * character and playtime up once for all of those passes. Ranks, prestige and wealth must come out
 * exactly as when each guild's pass looked them up again, which applying one guild per plan gives.
 */
class RealmSettlementPrestigePassTest {
    private static final String[] REALMS = {"amber", "birch", "cedar", "dune"};
    @TempDir Path folder;
    private final Characters characters = new Characters();

    /** Characters by key, a few per account, with set lifetimes so members split unevenly. */
    private static final class Characters implements Members.Resolver {
        final Map<String, CharacterInfo> infos = new LinkedHashMap<>();
        int infoCalls;
        int playtimeCalls;

        String add(String account, String name, CharacterStatus status, long seconds) {
            UUID owner = UUID.nameUUIDFromBytes(("account:" + account).getBytes(StandardCharsets.UTF_8));
            CharacterRef ref = new CharacterRef(owner, UUID.nameUUIDFromBytes(("character:" + name).getBytes(StandardCharsets.UTF_8)));
            String key = Members.Resolver.super.key(ref.characterId());
            infos.put(key, new CharacterInfo(ref, name, status, null, Set.of(), seconds, Instant.EPOCH));
            return key;
        }

        Integer playtime(String key) {
            playtimeCalls++;
            CharacterInfo info = infos.get(key);
            return info == null ? null : (int) info.playtimeSeconds();
        }

        @Override public Optional<CharacterSession> session(Player player) { return Optional.empty(); }
        @Override public boolean isCurrent(CharacterSession session) { return false; }
        @Override public String key(Player player) { return null; }
        @Override public Optional<Player> online(String key) { return Optional.empty(); }

        @Override public Optional<CharacterInfo> info(String key) {
            infoCalls++;
            return Optional.ofNullable(infos.get(key));
        }

        @Override public Optional<List<CharacterInfo>> characters(UUID ownerId) {
            return Optional.of(infos.values().stream().filter(info -> info.ref().ownerId().equals(ownerId)).toList());
        }
    }

    @BeforeEach void identity() {
        RecentPlaytime.reset();
        Members.setResolver(characters);
    }

    @AfterEach void restore() {
        RecentPlaytime.reset();
        NameKeyedMembers.install();
    }

    /** Four realms of different wealth, each with a trading guild, alts, a dead member and a five-step rank ladder. */
    private List<Guild> world(FactionDomainFixture fixture) {
        RankLoader.ranks.clear();
        fixture.rank("common", 1, 0);
        double[] minimums = {40, 90, 180, 360};
        for (int level = 2; level <= 5; level++) fixture.rank("rank" + level, level, minimums[level - 2]).setPercentage(55.0);
        List<Guild> guilds = new ArrayList<>();
        for (int i = 0; i < REALMS.length; i++) {
            String realm = REALMS[i];
            String leader = characters.add(realm + "-leader", realm + " leader", CharacterStatus.ALIVE, 40_000L * (i + 1));
            Faction faction = fixture.saved(realm, leader);
            Guild capital = faction.getOrCreateMainGuild();
            capital.addMember(leader);
            for (int m = 0; m < 2 + i; m++) {
                capital.addMember(characters.add(realm + "-citizen" + m, realm + " citizen " + m, CharacterStatus.ALIVE, 9_000L * (m + 1)));
            }
            String trader = characters.add(realm + "-trader", realm + " trader", CharacterStatus.ALIVE, 120_000L);
            String alt = characters.add(realm + "-trader", realm + " trader alt", CharacterStatus.ALIVE, 30_000L);
            String dead = characters.add(realm + "-fallen", realm + " fallen", CharacterStatus.DEAD, 50_000L);
            Guild guild = fixture.guild(faction, realm + "_guild", trader);
            guild.addMember(alt);
            guild.addMember(dead);
            TestTreasuries.set(capital.getBank(), 2_500.0 * (i + 1) * (i + 1));
            TestTreasuries.set(guild.getBank(), 700.0 * (REALMS.length - i));
            guilds.add(capital);
            guilds.add(guild);
        }
        MemberPlaytime.setProbe(characters::playtime);
        return guilds;
    }

    private static SettlementJournal.Plan plan(long day) {
        return new RealmSettlementCalculation(day, 100_000_000L, FactionManager.getAllGuilds(), List.of()).calculate((b, l, c) -> {});
    }

    private RealmSettlementEffects effects() throws IOException {
        Database database = mock(Database.class);
        when(database.saveFactionDurably(any(), any())).thenReturn(true);
        return new RealmSettlementEffects(folder, database, new PlayerEconomyManager(), (id, paid) -> {}, changes -> {});
    }

    /** Rank, prestige, prestige lines and wealth of every realm and guild, as exact text. */
    private static Map<String, String> prestige() {
        Map<String, String> all = new TreeMap<>();
        for (Faction faction : FactionManager.factions) {
            StringBuilder lines = new StringBuilder();
            faction.getPrestigeModifiers().forEach(m -> lines.append(m.getType()).append('=').append(m.getAmount()).append(';'));
            all.put("realm " + faction.getId(), faction.getRank().getId() + " " + faction.getPrestige() + " " + faction.getWealth() + " " + lines);
            for (Guild guild : faction.getGuildHandler().getGuilds()) all.put("guild " + guild.getId(), String.valueOf(guild.getWealth()));
        }
        return all;
    }

    private record Outcome(Map<String, String> prestige, int ranksClimbed, int playtimeAsks) {}

    /** The settlement as it now runs: one plan, every guild's pass inside one set of lookups. */
    private Outcome together() throws IOException {
        try (var fixture = new FactionDomainFixture(); var treasuries = new TestTreasuries()) {
            List<Guild> guilds = world(fixture);
            var plan = plan(7);
            unranked();
            characters.playtimeCalls = 0;
            effects().apply(plan);
            assertEquals(guilds.size(), JsonUtil.GSON.fromJson(plan.changes(), RealmSettlementChanges.class).guilds.size());
            return new Outcome(prestige(), climbed(), characters.playtimeCalls);
        }
    }

    /** As before: each guild's prestige pass looks every member up again, here one guild per plan. */
    private Outcome eachGuildAlone() throws IOException {
        try (var fixture = new FactionDomainFixture(); var treasuries = new TestTreasuries()) {
            world(fixture);
            var plan = plan(7);
            unranked();
            RealmSettlementChanges whole = JsonUtil.GSON.fromJson(plan.changes(), RealmSettlementChanges.class);
            var effects = effects();
            characters.playtimeCalls = 0;
            for (int i = 0; i < whole.guilds.size(); i++) {
                RealmSettlementChanges one = new RealmSettlementChanges();
                // Upkeep is applied before the guild loop and reparations after it, as in one plan.
                if (i == 0) one.upkeep.addAll(whole.upkeep);
                one.guilds.add(whole.guilds.get(i));
                if (i == whole.guilds.size() - 1) one.reparations.putAll(whole.reparations);
                effects.apply(new SettlementJournal.Plan(7, plan.legs(), JsonUtil.GSON.toJsonTree(one).getAsJsonObject()));
            }
            return new Outcome(prestige(), climbed(), characters.playtimeCalls);
        }
    }

    /** Every realm back at the lowest rank, as after a cold start, so the guild passes have ranks to climb. */
    private static void unranked() {
        FactionManager.factions.forEach(faction -> faction.setRank(RankLoader.getLowest()));
    }

    private static int climbed() {
        return FactionManager.factions.stream().mapToInt(f -> f.getRank().getLevel() - 1).max().orElse(0);
    }

    @Test void onePassOfLookupsGivesEveryRealmTheRankAndPrestigeOfAFreshLookupPerGuild() throws IOException {
        Outcome before = eachGuildAlone();
        Outcome now = together();
        assertEquals(before.prestige(), now.prestige());
        assertTrue(now.ranksClimbed() >= 2,
                "A realm climbs one rank per pass, so the fixture must need several of the per-guild passes");
        assertTrue(now.playtimeAsks() * REALMS.length * 2 == before.playtimeAsks(),
                "Each member's playtime is asked once instead of once per guild: " + now.playtimeAsks() + " vs " + before.playtimeAsks());
    }

    @Test void aMissingBranchFailsThroughTheSharedLookupsAndLeavesThemClosed() throws IOException {
        try (var fixture = new FactionDomainFixture(); var treasuries = new TestTreasuries()) {
            world(fixture);
            var plan = plan(8);
            var changes = JsonUtil.GSON.fromJson(plan.changes(), RealmSettlementChanges.class);
            changes.guilds.getLast().branches.put(99, 1);
            var broken = new SettlementJournal.Plan(8, plan.legs(), JsonUtil.GSON.toJsonTree(changes).getAsJsonObject());
            var failure = assertThrows(IOException.class, () -> effects().apply(broken));
            assertEquals("Settlement branch missing: 99", failure.getMessage());
            // Outside a pass every ask reaches the probe again.
            characters.playtimeCalls = 0;
            String someone = characters.infos.keySet().iterator().next();
            MemberPlaytime.secondsFor(someone);
            MemberPlaytime.secondsFor(someone);
            assertEquals(2, characters.playtimeCalls);
        }
    }
}
