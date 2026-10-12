package net.tfminecraft.simplefactions.espionage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;
import java.util.UUID;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.SimpleServicesManager;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.bukkit.entity.Player;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import net.tfminecraft.simplefactions.database.Database;
import net.tfminecraft.simplefactions.database.JsonUtil;
import net.tfminecraft.tlibs.character.CharacterDeathEvent;
import net.tfminecraft.tlibs.character.CharacterIdentity;
import net.tfminecraft.tlibs.character.CharacterInfo;
import net.tfminecraft.tlibs.character.CharacterRef;
import net.tfminecraft.tlibs.character.CharacterStatus;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.objects.Faction;

class OfficeVacancyTest {
    @AfterEach void defaults() { EspionageConfig.load(new YamlConfiguration()); }

    @Test void vacancyPersistsUntilFilledAndReturnsOnRemoval() {
        var faction = mock(Faction.class);
        var state = new EspionageState();
        when(faction.getEspionage()).thenReturn(state);
        for (long now : new long[]{0, 365L * 86_400_000}) {
            var penalties = EspionageService.stabilityModifiers(faction, now);
            assertEquals(1, penalties.size());
            assertEquals(-10, penalties.getFirst().getModifier());
            assertEquals("Vacant Spymaster", penalties.getFirst().getName());
        }
        var holder = new SpecialPositionAssignment(); holder.playerName = "Spy";
        when(faction.isMember("Spy")).thenReturn(true);
        state.appoint(holder, 80);
        assertTrue(EspionageService.stabilityModifiers(faction, 0).isEmpty());
        state.removeSpymaster();
        assertEquals(-10, EspionageService.stabilityModifiers(faction, Long.MAX_VALUE).getFirst().getModifier());
        var config = new YamlConfiguration();
        config.set("positions.spymaster.vacancy-stability-penalty", 0);
        EspionageConfig.load(config);
        assertTrue(EspionageService.stabilityModifiers(faction, 0).isEmpty());
        config.set("positions.spymaster.vacancy-stability-penalty", 15);
        EspionageConfig.load(config);
        assertEquals(-15, EspionageService.stabilityModifiers(faction, 0).getFirst().getModifier());
    }

    @Test void ineligibleHolderCountsAsVacantAndReplacementUnrestRemainsIndependent() {
        var faction = mock(Faction.class);
        var state = new EspionageState();
        when(faction.getEspionage()).thenReturn(state);
        var holder = new SpecialPositionAssignment(); holder.playerName = "FormerSpy";
        state.appoint(holder, 80);
        state.addUnrest(SpecialPosition.SPYMASTER, 10, 7, 1000);
        assertEquals(2, EspionageService.stabilityModifiers(faction, 1000).size());
        assertEquals(1, EspionageService.stabilityModifiers(faction, 1000 + 8L * 86_400_000).size());
        when(faction.isMember("FormerSpy")).thenReturn(true);
        assertTrue(EspionageService.stabilityModifiers(faction, 1000 + 8L * 86_400_000).isEmpty());
    }

    @Test void confirmedDeathMatchesOwnerAndCaseInsensitiveCharacterWithoutLosingAppointmentHistory() {
        try (var fixture = new DeathFixture()) {
            var holder = fixture.appoint(fixture.ref.ownerId(), fixture.ref.characterId().toString().toUpperCase(java.util.Locale.ROOT), 1000);
            UUID deathId = UUID.randomUUID();
            EspionageService.characterDied(new CharacterRef(fixture.ref.ownerId(), UUID.randomUUID()),
                    UUID.randomUUID(), Instant.ofEpochMilli(2000), "Other character");
            EspionageService.characterDied(new CharacterRef(UUID.randomUUID(), fixture.ref.characterId()),
                    UUID.randomUUID(), Instant.ofEpochMilli(2000), "Other owner");
            assertSame(holder, fixture.state.getSpymaster());
            assertTrue(fixture.databases.constructed().isEmpty());
            assertEquals(1000, fixture.state.lastAppointedAt(SpecialPosition.SPYMASTER));

            EspionageService.characterDied(fixture.ref, deathId, Instant.ofEpochMilli(2000), "Spy");
            assertNull(fixture.state.getSpymaster());
            assertEquals(1, fixture.state.appointmentCount(SpecialPosition.SPYMASTER));
            assertEquals(0, fixture.state.lastAppointedAt(SpecialPosition.SPYMASTER));
            assertTrue(fixture.state.handledDeath(deathId));
            verify(fixture.databases.constructed().getFirst()).saveFactionChecked(fixture.faction);
            verify(fixture.owner).sendMessage(contains("passing of Spy"));
        }
    }

    @Test void legacyAppointmentsWithUnknownOwnerOrCharacterAreRetained() {
        for (int legacy = 0; legacy < 3; legacy++) {
            try (var fixture = new DeathFixture()) {
                UUID owner = legacy == 0 ? null : fixture.ref.ownerId();
                String character = legacy == 1 ? null : legacy == 2 ? "old-unparseable-id" : fixture.ref.characterId().toString();
                var holder = fixture.appoint(owner, character, 1000);
                UUID deathId = UUID.randomUUID();
                EspionageService.characterDied(fixture.ref, deathId, Instant.ofEpochMilli(2000), "Spy");
                assertSame(holder, fixture.state.getSpymaster());
                assertFalse(fixture.state.handledDeath(deathId));
                assertTrue(fixture.databases.constructed().isEmpty());
                verify(fixture.owner, never()).sendMessage(anyString());
            }
        }
    }

    @Test void deathBeforeTheCurrentAppointmentCannotRevokeItsSuccessor() {
        try (var fixture = new DeathFixture()) {
            var holder = fixture.appoint(fixture.ref.ownerId(), fixture.ref.characterId().toString(), 3000);
            UUID deathId = UUID.randomUUID();
            EspionageService.characterDied(fixture.ref, deathId, Instant.ofEpochMilli(2000), "Spy");
            assertSame(holder, fixture.state.getSpymaster());
            assertEquals(3000, fixture.state.lastAppointedAt(SpecialPosition.SPYMASTER));
            assertFalse(fixture.state.handledDeath(deathId));
            assertTrue(fixture.databases.constructed().isEmpty());
        }
    }

    @Test void pendingFounderRequiresKnownCachedOwnerAndExactUuidAndRetriesFailedSave() {
        try (var fixture = new DeathFixture()) {
            String savedId = fixture.ref.characterId().toString().toUpperCase(java.util.Locale.ROOT);
            fixture.state.pendingFounder(SpecialPosition.SPYMASTER, savedId);
            long boundAt = fixture.state.pendingFounderBoundAt(SpecialPosition.SPYMASTER);
            assertTrue(boundAt > 0);
            var offices = fixture.state.snapshotOffices();
            fixture.state.removeSpymaster();
            fixture.state.restoreOffices(offices);
            assertEquals(savedId, fixture.state.pendingFounderCharacter(SpecialPosition.SPYMASTER));
            assertEquals(boundAt, fixture.state.pendingFounderBoundAt(SpecialPosition.SPYMASTER));
            UUID deathId = UUID.randomUUID();
            Instant at = Instant.ofEpochMilli(boundAt + 1);

            EspionageService.characterDied(fixture.ref, deathId, at, "Founder");
            fixture.cacheFounder(UUID.randomUUID());
            EspionageService.characterDied(fixture.ref, deathId, at, "Founder");
            fixture.cacheFounder(fixture.ref.ownerId());
            EspionageService.characterDied(new CharacterRef(fixture.ref.ownerId(), UUID.randomUUID()),
                    UUID.randomUUID(), at, "Other");
            assertTrue(fixture.state.hasPendingFounder());
            assertTrue(fixture.databases.constructed().isEmpty());
            fixture.saveSucceeds = false;
            EspionageService.characterDied(fixture.ref, deathId, at, "Founder");
            assertTrue(fixture.state.hasPendingFounder());
            assertEquals(savedId, fixture.state.pendingFounderCharacter(SpecialPosition.SPYMASTER));
            assertEquals(boundAt, fixture.state.pendingFounderBoundAt(SpecialPosition.SPYMASTER));
            assertFalse(fixture.state.handledDeath(deathId));
            verify(fixture.owner, never()).sendMessage(anyString());

            fixture.saveSucceeds = true;
            EspionageService.characterDied(fixture.ref, deathId, at, "Founder");
            assertFalse(fixture.state.hasPendingFounder());
            assertNull(fixture.state.pendingFounderCharacter(SpecialPosition.SPYMASTER));
            assertEquals(0, fixture.state.appointmentCount(SpecialPosition.SPYMASTER));
            assertTrue(fixture.state.handledDeath(deathId));
            assertEquals(2, fixture.databases.constructed().size());
            verify(fixture.owner).sendMessage(contains("passing of Founder"));
        }
    }

    @Test void pendingFounderWithoutUsableCharacterIdentityIsRetained() {
        for (String unknown : new String[] {null, "legacy-id"}) {
            try (var fixture = new DeathFixture()) {
                fixture.cacheFounder(fixture.ref.ownerId());
                fixture.state.pendingFounder(SpecialPosition.SPYMASTER, unknown);
                EspionageService.characterDied(fixture.ref, UUID.randomUUID(), Instant.ofEpochMilli(2000), "Founder");
                assertTrue(fixture.state.hasPendingFounder());
                assertTrue(fixture.databases.constructed().isEmpty());
                verify(fixture.owner, never()).sendMessage(anyString());
            }
        }
    }

    @Test void automaticFounderRejectsEarlierDeathAndAcceptsANewDeathAfterRevival() {
        try (var fixture = new DeathFixture()) {
            var founder = new SpecialPositionAssignment();
            founder.playerId = fixture.ref.ownerId();
            founder.characterId = fixture.ref.characterId().toString();
            fixture.state.assignFounder(SpecialPosition.SPYMASTER, founder, 80);
            assertEquals(0, founder.appointedAt, "Automatic founders remain immediately established");
            assertTrue(founder.characterBoundAt > 0);
            UUID oldDeath = UUID.randomUUID();
            EspionageService.characterDied(fixture.ref, oldDeath,
                    Instant.ofEpochMilli(founder.characterBoundAt - 1), "Founder");
            assertSame(founder, fixture.state.getSpymaster());
            assertFalse(fixture.state.handledDeath(oldDeath));
            assertTrue(fixture.databases.constructed().isEmpty());
            UUID liveDeath = UUID.randomUUID();
            EspionageService.characterDied(fixture.ref, liveDeath,
                    Instant.ofEpochMilli(founder.characterBoundAt + 1), "Founder");
            assertNull(fixture.state.getSpymaster());

            var revived = new SpecialPositionAssignment();
            revived.playerId = fixture.ref.ownerId();
            revived.characterId = fixture.ref.characterId().toString();
            fixture.state.assignFounder(SpecialPosition.SPYMASTER, revived, 80);
            EspionageService.characterDied(fixture.ref, liveDeath,
                    Instant.ofEpochMilli(founder.characterBoundAt + 1), "Founder");
            assertSame(revived, fixture.state.getSpymaster());
            EspionageService.characterDied(fixture.ref, UUID.randomUUID(),
                    Instant.ofEpochMilli(revived.characterBoundAt + 1), "Founder");
            assertNull(fixture.state.getSpymaster());
            assertEquals(0, fixture.state.appointmentCount(SpecialPosition.SPYMASTER));
            assertEquals(2, fixture.databases.constructed().size());
        }
    }

    @Test void pendingFounderBindingSurvivesGsonAndBlocksOldDeathAfterRebinding() {
        try (var fixture = new DeathFixture()) {
            fixture.cacheFounder(fixture.ref.ownerId());
            String id = fixture.ref.characterId().toString();
            fixture.state.pendingFounder(SpecialPosition.SPYMASTER, id);
            long boundAt = fixture.state.pendingFounderBoundAt(SpecialPosition.SPYMASTER);
            fixture.state.pendingFounder(SpecialPosition.SPYMASTER, id.toUpperCase(java.util.Locale.ROOT));
            assertEquals(boundAt, fixture.state.pendingFounderBoundAt(SpecialPosition.SPYMASTER));
            fixture.state = JsonUtil.GSON.fromJson(JsonUtil.GSON.toJson(fixture.state), EspionageState.class);
            assertEquals(boundAt, fixture.state.pendingFounderBoundAt(SpecialPosition.SPYMASTER));
            UUID oldDeath = UUID.randomUUID();
            EspionageService.characterDied(fixture.ref, oldDeath, Instant.ofEpochMilli(boundAt - 1), "Founder");
            assertTrue(fixture.state.hasPendingFounder());
            assertFalse(fixture.state.handledDeath(oldDeath));
            assertTrue(fixture.databases.constructed().isEmpty());
            UUID liveDeath = UUID.randomUUID();
            EspionageService.characterDied(fixture.ref, liveDeath, Instant.ofEpochMilli(boundAt + 1), "Founder");
            assertFalse(fixture.state.hasPendingFounder());
            assertEquals(0, fixture.state.pendingFounderBoundAt(SpecialPosition.SPYMASTER));

            fixture.state.pendingFounder(SpecialPosition.SPYMASTER, id);
            long revivedBoundAt = fixture.state.pendingFounderBoundAt(SpecialPosition.SPYMASTER);
            EspionageService.characterDied(fixture.ref, liveDeath, Instant.ofEpochMilli(boundAt + 1), "Founder");
            assertTrue(fixture.state.hasPendingFounder());
            UUID olderUnseenDeath = UUID.randomUUID();
            EspionageService.characterDied(fixture.ref, olderUnseenDeath,
                    Instant.ofEpochMilli(revivedBoundAt - 1), "Founder");
            assertTrue(fixture.state.hasPendingFounder());
            assertFalse(fixture.state.handledDeath(olderUnseenDeath));
            EspionageService.characterDied(fixture.ref, UUID.randomUUID(),
                    Instant.ofEpochMilli(revivedBoundAt + 1), "Founder");
            assertFalse(fixture.state.hasPendingFounder());
            assertEquals(0, fixture.state.appointmentCount(SpecialPosition.SPYMASTER));
            assertEquals(2, fixture.databases.constructed().size());
        }
    }

    @Test void deathLedgerSurvivesGsonReloadAndProtectsAnUndatedAppointmentFromReplay() {
        try (var fixture = new DeathFixture()) {
            fixture.state = JsonUtil.GSON.fromJson("{\"handledCharacterDeaths\":null}", EspionageState.class);
            fixture.appoint(fixture.ref.ownerId(), fixture.ref.characterId().toString(), 1000);
            UUID deathId = UUID.randomUUID();
            Instant at = Instant.ofEpochMilli(2000);
            EspionageService.characterDied(fixture.ref, deathId, at, "Spy");
            fixture.state = JsonUtil.GSON.fromJson(JsonUtil.GSON.toJson(fixture.state), EspionageState.class);
            assertTrue(fixture.state.handledDeath(deathId));
            assertEquals(1, fixture.state.appointmentCount(SpecialPosition.SPYMASTER));
            // An undated legacy appointment makes the death ID, rather than time, block replay.
            var successor = fixture.appoint(fixture.ref.ownerId(), fixture.ref.characterId().toString(), 0);
            EspionageService.characterDied(fixture.ref, deathId, at, "Spy");
            assertSame(successor, fixture.state.getSpymaster());
            assertEquals(2, fixture.state.appointmentCount(SpecialPosition.SPYMASTER));
            assertEquals(1, fixture.databases.constructed().size());
            verify(fixture.owner, times(1)).sendMessage(anyString());
        }
    }

    @Test void deathLedgerKeepsOnlyTheMostRecentDeathsAcrossAGsonReload() {
        var state = new EspionageState();
        var deaths = new java.util.ArrayList<UUID>();
        for (int i = 0; i <= EspionageState.HANDLED_DEATH_LIMIT; i++) {
            deaths.add(UUID.randomUUID());
            state.rememberDeath(deaths.getLast());
        }
        state.rememberDeath(deaths.getLast());
        state = JsonUtil.GSON.fromJson(JsonUtil.GSON.toJson(state), EspionageState.class);
        assertFalse(state.handledDeath(deaths.getFirst()));
        for (UUID kept : deaths.subList(1, deaths.size())) assertTrue(state.handledDeath(kept));
        state.rememberDeath(UUID.randomUUID());
        assertFalse(state.handledDeath(deaths.get(1)), "Order survives the reload");
        assertTrue(state.handledDeath(deaths.get(2)));
    }

    @Test void newDeathAfterRevivalAndReappointmentStillRevokesTheSameCharacter() {
        try (var fixture = new DeathFixture()) {
            fixture.appoint(fixture.ref.ownerId(), fixture.ref.characterId().toString(), 1000);
            UUID firstDeath = UUID.randomUUID();
            EspionageService.characterDied(fixture.ref, firstDeath, Instant.ofEpochMilli(2000), "Spy");
            var revived = fixture.appoint(fixture.ref.ownerId(), fixture.ref.characterId().toString(), 3000);
            EspionageService.characterDied(fixture.ref, firstDeath, Instant.ofEpochMilli(2000), "Spy");
            assertSame(revived, fixture.state.getSpymaster());
            UUID secondDeath = UUID.randomUUID();
            EspionageService.characterDied(fixture.ref, secondDeath, Instant.ofEpochMilli(4000), "Spy");
            assertNull(fixture.state.getSpymaster());
            assertTrue(fixture.state.handledDeath(firstDeath));
            assertTrue(fixture.state.handledDeath(secondDeath));
            assertEquals(2, fixture.state.appointmentCount(SpecialPosition.SPYMASTER));
            assertEquals(0, fixture.state.lastAppointedAt(SpecialPosition.SPYMASTER));
            assertEquals(2, fixture.databases.constructed().size());
        }
    }

    @Test void failedDeathSaveRestoresOfficeHistoryUnrestAndLedgerBeforeRetry() {
        try (var fixture = new DeathFixture()) {
            var holder = fixture.appoint(fixture.ref.ownerId(), fixture.ref.characterId().toString(), 1000);
            fixture.state.addUnrest(SpecialPosition.SPYMASTER, 10, 7, 1000);
            String before = JsonUtil.GSON.toJson(fixture.state);
            UUID deathId = UUID.randomUUID();
            fixture.saveSucceeds = false;
            EspionageService.characterDied(fixture.ref, deathId, Instant.ofEpochMilli(2000), "Spy");
            assertSame(holder, fixture.state.getSpymaster());
            assertEquals(before, JsonUtil.GSON.toJson(fixture.state));
            assertFalse(fixture.state.handledDeath(deathId));
            verify(fixture.owner, never()).sendMessage(anyString());

            fixture.saveSucceeds = true;
            EspionageService.characterDied(fixture.ref, deathId, Instant.ofEpochMilli(2000), "Spy");
            assertNull(fixture.state.getSpymaster());
            assertTrue(fixture.state.handledDeath(deathId));
            assertEquals(1, fixture.state.appointmentCount(SpecialPosition.SPYMASTER));
            assertEquals(1, fixture.state.unrestModifiers(2000).size());
            assertEquals(2, fixture.databases.constructed().size());
            EspionageService.characterDied(fixture.ref, deathId, Instant.ofEpochMilli(2000), "Spy");
            assertEquals(2, fixture.databases.constructed().size());
            verify(fixture.owner).sendMessage(contains("passing of Spy"));
        }
    }

    @Test void committedSharedDeathListenerSettlesImmediatelyWithCanonicalName() {
        try (var fixture = new DeathFixture()) {
            fixture.appoint(fixture.ref.ownerId(), fixture.ref.characterId().toString(), 1000);
            UUID deathId = UUID.randomUUID();
            CharacterInfo info = new CharacterInfo(fixture.ref, "Canonical Spy", CharacterStatus.DEAD,
                    null, Set.of(), 0, Instant.EPOCH);
            when(fixture.provider.info(fixture.ref.characterId())).thenReturn(Optional.of(info));
            new OfficeCharacterDeathListener().onCharacterDeath(new CharacterDeathEvent(
                    fixture.ref, deathId, Instant.ofEpochMilli(2000), null, null, "OTHER"));
            assertNull(fixture.state.getSpymaster());
            assertTrue(fixture.state.handledDeath(deathId));
            verify(fixture.owner).sendMessage(contains("passing of Canonical Spy"));
            fixture.bukkit.verify(Bukkit::getScheduler, never());
            verify(fixture.provider, never()).currentSession(any());
        }
    }

    @Test void committedDeathStillSettlesWhenDisplaySnapshotIsMissingOrBelongsElsewhere() {
        for (boolean foreignInfo : new boolean[] {false, true}) {
            try (var fixture = new DeathFixture()) {
                fixture.appoint(fixture.ref.ownerId(), fixture.ref.characterId().toString(), 1000);
                if (foreignInfo) {
                    CharacterInfo wrong = new CharacterInfo(new CharacterRef(UUID.randomUUID(), fixture.ref.characterId()),
                            "Someone else", CharacterStatus.DEAD, null, Set.of(), 0, Instant.EPOCH);
                    when(fixture.provider.info(fixture.ref.characterId())).thenReturn(Optional.of(wrong));
                }
                new OfficeCharacterDeathListener().onCharacterDeath(new CharacterDeathEvent(fixture.ref,
                        UUID.randomUUID(), Instant.ofEpochMilli(2000), null, null, "OTHER"));
                assertNull(fixture.state.getSpymaster());
                verify(fixture.owner).sendMessage(contains("passing of your character"));
            }
        }
    }

    private static final class DeathFixture implements AutoCloseable {
        final MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
        final MockedStatic<FactionManager> factions = mockStatic(FactionManager.class);
        final Faction faction = mock(Faction.class);
        final Player owner = mock(Player.class);
        final CharacterRef ref = new CharacterRef(UUID.randomUUID(),
                UUID.fromString("aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee"));
        final CharacterIdentity provider = mock(CharacterIdentity.class);
        final SimpleServicesManager services = new SimpleServicesManager();
        final Plugin plugin = mock(Plugin.class);
        final MockedConstruction<Database> databases;
        EspionageState state = new EspionageState();
        boolean saveSucceeds = true;
        private OfflinePlayer cachedFounder;

        DeathFixture() {
            Server server = mock(Server.class);
            PluginManager plugins = mock(PluginManager.class);
            when(server.getPluginManager()).thenReturn(plugins);
            when(server.getServicesManager()).thenReturn(services);
            bukkit.when(Bukkit::getServer).thenReturn(server);
            bukkit.when(Bukkit::getServicesManager).thenReturn(services);
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            bukkit.when(() -> Bukkit.getPlayer(ref.ownerId())).thenReturn(owner);
            bukkit.when(() -> Bukkit.getOfflinePlayerIfCached("Founder")).thenAnswer(call -> cachedFounder);
            when(owner.getUniqueId()).thenReturn(ref.ownerId());
            when(owner.getName()).thenReturn("Founder");
            when(faction.getLeader()).thenReturn("Founder");
            when(faction.getEspionage()).thenAnswer(call -> state);
            factions.when(FactionManager::getCopy).thenReturn(List.of(faction));
            databases = mockConstruction(Database.class, (database, context) ->
                    when(database.saveFactionChecked(faction)).thenAnswer(call -> saveSucceeds));
            services.register(CharacterIdentity.class, provider, plugin, ServicePriority.Normal);
        }

        SpecialPositionAssignment appoint(UUID ownerId, String characterId, long at) {
            var holder = new SpecialPositionAssignment();
            holder.playerId = ownerId;
            holder.playerName = "Spy";
            holder.characterId = characterId;
            state.appoint(holder, 80, at);
            return holder;
        }

        void cacheFounder(UUID ownerId) {
            cachedFounder = mock(OfflinePlayer.class);
            when(cachedFounder.getUniqueId()).thenReturn(ownerId);
        }

        @Override public void close() {
            try {
                services.unregister(CharacterIdentity.class, provider);
            } finally {
                databases.close();
                factions.close();
                bukkit.close();
            }
        }
    }
}
