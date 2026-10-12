package net.tfminecraft.simplefactions.mercenary.company;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.ServicesManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

import net.tfminecraft.tlibs.character.CharacterIdentity;
import net.tfminecraft.tlibs.character.CharacterInfo;
import net.tfminecraft.tlibs.character.CharacterRef;
import net.tfminecraft.tlibs.character.CharacterSession;
import net.tfminecraft.tlibs.character.CharacterStatus;
import net.tfminecraft.tlibs.character.Characters;

class SharedMercenaryCharactersTest {
    private final UUID owner = UUID.randomUUID();
    private final UUID characterId = UUID.randomUUID();
    private final Map<String, Player> online = new HashMap<>();
    private final Map<UUID, Player> connections = new HashMap<>();
    private final Map<String, OfflinePlayer> cached = new HashMap<>();
    private final Map<UUID, CharacterInfo> infos = new HashMap<>();
    private final Map<UUID, List<CharacterInfo>> directory = new HashMap<>();
    private final CharacterIdentity provider = mock(CharacterIdentity.class);
    private CharacterIdentity registered;
    private CharacterSession session;
    private Player player;
    private MockedStatic<Bukkit> bukkit;

    @BeforeEach
    void setUp() {
        bukkit = mockStatic(Bukkit.class);
        bukkit.when(Bukkit::getServer).thenReturn(mock(Server.class));
        bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
        ServicesManager services = mock(ServicesManager.class);
        bukkit.when(Bukkit::getServicesManager).thenReturn(services);
        when(services.load(CharacterIdentity.class)).thenAnswer(call -> registered);
        bukkit.when(() -> Bukkit.getPlayerExact(anyString())).thenAnswer(call -> online.get(call.getArgument(0)));
        bukkit.when(() -> Bukkit.getPlayer(any(UUID.class))).thenAnswer(call -> connections.get(call.getArgument(0)));
        bukkit.when(() -> Bukkit.getOfflinePlayerIfCached(anyString())).thenAnswer(call -> cached.get(call.getArgument(0)));
        player = mock(Player.class);
        when(player.getName()).thenReturn("Sigrun");
        when(player.getUniqueId()).thenReturn(owner);
        when(player.isOnline()).thenReturn(true);
        online.put("Sigrun", player);
        connections.put(owner, player);
        when(provider.currentSession(player)).thenAnswer(call -> Optional.ofNullable(session));
        when(provider.isCurrent(any())).thenAnswer(call -> call.getArgument(0).equals(session));
        when(provider.info(any())).thenAnswer(call -> Optional.ofNullable(infos.get(call.getArgument(0))));
        when(provider.characters(any())).thenAnswer(call -> Optional.ofNullable(directory.get(call.getArgument(0))));
        registered = provider;
        MercenaryEligibility.useSharedIdentity();
    }

    @AfterEach
    void close() {
        MercenaryEligibility.reset();
        bukkit.close();
    }

    @Test
    void providerArrivalAndLossKeepOptionalGameplayOpenButPendingCharactersClosed() {
        registered = null;
        assertEquals(MercenaryEligibility.Status.UNKNOWN, MercenaryEligibility.check("Sigrun"));
        assertEquals(MercenaryEligibility.Status.UNKNOWN, MercenaryEligibility.livingMercenary("Sigrun"));
        assertTrue(MercenaryEligibility.canCreate("Sigrun"));
        assertTrue(MercenaryEligibility.canJoin("Sigrun"));

        registered = provider;
        assertFalse(MercenaryEligibility.canCreate("Sigrun"));
        assertFalse(MercenaryEligibility.canJoin("Sigrun"));
        assertEquals(MercenaryEligibility.Status.UNKNOWN, MercenaryEligibility.livingMercenary("Sigrun"));
        CharacterInfo mercenary = character(owner, characterId, CharacterStatus.ALIVE, "MERCENARY");
        activate(mercenary);
        directory.put(owner, List.of(mercenary));
        assertTrue(MercenaryEligibility.canCreate("Sigrun"));
        assertTrue(MercenaryEligibility.canJoin("Sigrun"));
        assertEquals(MercenaryEligibility.Status.ELIGIBLE, MercenaryEligibility.livingMercenary("Sigrun"));

        registered = null;
        assertEquals(MercenaryEligibility.Status.UNKNOWN, MercenaryEligibility.check("Sigrun"));
        assertEquals(MercenaryEligibility.Status.UNKNOWN, MercenaryEligibility.livingMercenary("Sigrun"));
        assertTrue(MercenaryEligibility.canCreate("Sigrun"));
        bukkit.when(Bukkit::getServer).thenReturn(null);
        assertEquals(MercenaryEligibility.Status.UNKNOWN, MercenaryEligibility.check("Sigrun"));
        assertEquals(MercenaryEligibility.Status.UNKNOWN, MercenaryEligibility.livingMercenary("Sigrun"));
    }

    @ParameterizedTest
    @EnumSource(CharacterStatus.class)
    void onlyAnOwnedAliveMercenaryCanFoundJoinOrCountAsLiving(CharacterStatus status) {
        CharacterInfo character = character(owner, characterId, status, "mercenary");
        activate(character);
        directory.put(owner, List.of(character));
        MercenaryEligibility.Status expected = status == CharacterStatus.ALIVE
                ? MercenaryEligibility.Status.ELIGIBLE : MercenaryEligibility.Status.INELIGIBLE;
        assertEquals(expected, MercenaryEligibility.check("Sigrun"));
        assertEquals(expected, MercenaryEligibility.livingMercenary("Sigrun"));
    }

    @Test
    void inactiveAliveMercenaryKeepsTheAccountEligibleWhileTheActiveMerchantCannotJoin() {
        CharacterInfo merchant = character(owner, characterId, CharacterStatus.ALIVE, "merchant");
        CharacterInfo mercenary = character(owner, UUID.randomUUID(), CharacterStatus.ALIVE, "mercenary");
        activate(merchant);
        directory.put(owner, List.of(merchant, mercenary));
        assertFalse(MercenaryEligibility.canJoin("Sigrun"));
        assertEquals(MercenaryEligibility.Status.ELIGIBLE, MercenaryEligibility.livingMercenary("Sigrun"));
        session = null;
        assertEquals(MercenaryEligibility.Status.ELIGIBLE, MercenaryEligibility.livingMercenary("Sigrun"));
    }

    @Test
    void unknownAndKnownEmptyAccountsStayDistinctAndResultsAreNeverCached() {
        assertEquals(MercenaryEligibility.Status.UNKNOWN, MercenaryEligibility.livingMercenary("Sigrun"));
        directory.put(owner, List.of());
        assertEquals(MercenaryEligibility.Status.INELIGIBLE, MercenaryEligibility.livingMercenary("Sigrun"));
        directory.put(owner, List.of(character(owner, characterId, CharacterStatus.ALIVE, "mercenary")));
        assertEquals(MercenaryEligibility.Status.ELIGIBLE, MercenaryEligibility.livingMercenary("Sigrun"));
        directory.put(owner, List.of(character(owner, characterId, CharacterStatus.ALIVE, "merchant")));
        assertEquals(MercenaryEligibility.Status.INELIGIBLE, MercenaryEligibility.livingMercenary("Sigrun"));
        directory.remove(owner);
        assertEquals(MercenaryEligibility.Status.UNKNOWN, MercenaryEligibility.livingMercenary("Sigrun"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"session-owner", "info-owner", "info-character", "unknown-info", "stale-session", "offline", "replacement-connection"})
    void invalidActiveIdentityCannotAuthorizeMercenaryWork(String invalid) {
        CharacterInfo mercenary = character(owner, characterId, CharacterStatus.ALIVE, "mercenary");
        activate(mercenary);
        switch (invalid) {
            case "session-owner" -> session = new CharacterSession(new CharacterRef(UUID.randomUUID(), characterId), 1);
            case "info-owner" -> infos.put(characterId, character(UUID.randomUUID(), characterId, CharacterStatus.ALIVE, "mercenary"));
            case "info-character" -> infos.put(characterId, character(owner, UUID.randomUUID(), CharacterStatus.ALIVE, "mercenary"));
            case "unknown-info" -> infos.clear();
            case "stale-session" -> doReturn(false).when(provider).isCurrent(any());
            case "offline" -> when(player.isOnline()).thenReturn(false);
            case "replacement-connection" -> connections.put(owner, mock(Player.class));
            default -> fail("Unknown invalid identity");
        }
        assertEquals(MercenaryEligibility.Status.INELIGIBLE, MercenaryEligibility.check("Sigrun"));
        assertFalse(MercenaryEligibility.canCreate("Sigrun"));
        assertFalse(MercenaryEligibility.canJoin("Sigrun"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"aba", "pending", "provider-loss", "reconnect"})
    void infoCallbackCannotAuthorizeAnActivationThatEndedDuringTheRead(String change) {
        CharacterInfo mercenary = character(owner, characterId, CharacterStatus.ALIVE, "mercenary");
        activate(mercenary);
        CharacterSession original = session;
        when(provider.info(characterId)).thenAnswer(call -> {
            switch (change) {
                case "aba" -> {
                    session = new CharacterSession(new CharacterRef(owner, UUID.randomUUID()), 2);
                    session = new CharacterSession(mercenary.ref(), 3);
                }
                case "pending" -> session = null;
                case "provider-loss" -> registered = null;
                case "reconnect" -> connections.put(owner, mock(Player.class));
                default -> fail("Unknown session change");
            }
            return Optional.of(mercenary);
        });
        assertEquals(MercenaryEligibility.Status.INELIGIBLE, MercenaryEligibility.check("Sigrun"));
        if (change.equals("aba")) {
            assertEquals(original.ref(), session.ref());
            assertFalse(Characters.isCurrent(original));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"null-entry", "foreign-owner", "missing-ref", "missing-character", "missing-status", "missing-traits", "null-trait"})
    void corruptOrForeignRecordsAreUnknownUnlessAValidLivingMercenaryExists(String corruption) {
        CharacterInfo broken = mock(CharacterInfo.class);
        when(broken.ref()).thenReturn(new CharacterRef(owner, characterId));
        when(broken.status()).thenReturn(CharacterStatus.ALIVE);
        when(broken.traits()).thenReturn(Set.of("mercenary"));
        switch (corruption) {
            case "null-entry" -> broken = null;
            case "foreign-owner" -> when(broken.ref()).thenReturn(new CharacterRef(UUID.randomUUID(), characterId));
            case "missing-ref" -> when(broken.ref()).thenReturn(null);
            case "missing-character" -> {
                CharacterRef ref = mock(CharacterRef.class);
                when(ref.ownerId()).thenReturn(owner);
                when(broken.ref()).thenReturn(ref);
            }
            case "missing-status" -> when(broken.status()).thenReturn(null);
            case "missing-traits" -> when(broken.traits()).thenReturn(null);
            case "null-trait" -> when(broken.traits()).thenReturn(new HashSet<>(Arrays.asList("mercenary", null)));
            default -> fail("Unknown damaged record");
        }
        directory.put(owner, Arrays.asList(broken));
        assertEquals(MercenaryEligibility.Status.UNKNOWN, MercenaryEligibility.livingMercenary("Sigrun"));
        directory.put(owner, Arrays.asList(broken, character(owner, UUID.randomUUID(), CharacterStatus.ALIVE, "mercenary")));
        assertEquals(MercenaryEligibility.Status.ELIGIBLE, MercenaryEligibility.livingMercenary("Sigrun"));
    }

    @Test
    void cachedOfflineAccountsUseTheDirectoryWithoutAnyMojangLookup() {
        UUID offlineOwner = UUID.randomUUID();
        OfflinePlayer account = mock(OfflinePlayer.class);
        when(account.getUniqueId()).thenReturn(offlineOwner);
        cached.put("Offline", account);
        assertEquals(MercenaryEligibility.Status.UNKNOWN, MercenaryEligibility.check("Offline"));
        assertEquals(MercenaryEligibility.Status.UNKNOWN, MercenaryEligibility.livingMercenary("Uncached"));
        assertEquals(MercenaryEligibility.Status.UNKNOWN, MercenaryEligibility.livingMercenary("Offline"));
        directory.put(offlineOwner, List.of());
        assertEquals(MercenaryEligibility.Status.INELIGIBLE, MercenaryEligibility.livingMercenary("Offline"));
        directory.put(offlineOwner, List.of(character(offlineOwner, UUID.randomUUID(), CharacterStatus.ALIVE, "mercenary")));
        assertEquals(MercenaryEligibility.Status.ELIGIBLE, MercenaryEligibility.livingMercenary("Offline"));
        cached.put("BrokenAccount", mock(OfflinePlayer.class));
        assertEquals(MercenaryEligibility.Status.UNKNOWN, MercenaryEligibility.livingMercenary("BrokenAccount"));
        bukkit.verify(() -> Bukkit.getOfflinePlayer(anyString()), never());
        bukkit.verify(() -> Bukkit.getOfflinePlayer(any(UUID.class)), never());
    }

    @Test
    void providerLossDuringTheDirectoryReadCannotTurnUnknownIntoKnownEmpty() {
        when(provider.characters(owner)).thenAnswer(call -> {
            registered = null;
            return Optional.of(List.of());
        });
        assertEquals(MercenaryEligibility.Status.UNKNOWN, MercenaryEligibility.livingMercenary("Sigrun"));
    }

    @Test
    void unknownAccountAndProviderLossCannotDismissTheMemberOrDisbandTheCompany() {
        CompanyFixture fixture = new CompanyFixture(0);
        MercenaryCompany company = new MercenaryCompany(fixture.guild, "Hired Blades", CompanyFixture.companyRegiment(), 0);
        fixture.guild.setCompany(company);
        company.getRegiment().setCurrentSlots(2);
        assertTrue(company.enlist("Sigrun"));
        OfflinePlayer leader = mock(OfflinePlayer.class);
        UUID leaderId = UUID.randomUUID();
        when(leader.getUniqueId()).thenReturn(leaderId);
        cached.put("Ivar", leader);
        directory.put(leaderId, List.of());

        assertFalse(company.enforceCharacterGate());
        assertSame(company, fixture.company());
        assertTrue(company.isEnlisted("Sigrun"));
        directory.put(owner, List.of());
        registered = null;
        assertFalse(company.enforceCharacterGate());
        assertSame(company, fixture.company());
        assertTrue(company.isEnlisted("Sigrun"));

        registered = provider;
        assertTrue(company.enforceCharacterGate(), "Only known empty accounts permit disbanding");
        assertNull(fixture.company());
    }

    private void activate(CharacterInfo character) {
        infos.put(character.ref().characterId(), character);
        session = new CharacterSession(character.ref(), 1);
    }

    private static CharacterInfo character(UUID ownerId, UUID id, CharacterStatus status, String... traits) {
        return new CharacterInfo(new CharacterRef(ownerId, id), "Canonical Mercenary", status, null, Set.of(traits), 0, null);
    }
}
