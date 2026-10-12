package net.tfminecraft.simplefactions.espionage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.net.URLClassLoader;
import java.util.*;
import net.Indyuce.mmocore.api.player.attribute.PlayerAttributes;
import net.tfminecraft.rpcharacters.objects.attributes.AttributeModifier;
import net.tfminecraft.tlibs.character.*;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.ServicesManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import net.tfminecraft.simplefactions.objects.Faction;

class OfficeOptionalIntegrationsTest {
    @Test void roleplayAttributesRemainAvailableWithoutMmocore() {
        try (var f = new IdentityFixture()) {
            var session = f.activate(UUID.randomUUID());
            try (var rpc = new RoleplayFixture(f)) {
            when(rpc.character.getId()).thenReturn(session.ref().characterId().toString().toUpperCase(Locale.ROOT));
            assertEquals(session.ref().characterId().toString(), OfficeCharacters.activeCharacterId(f.player));
            var values = OfficeCharacters.attributes(f.player);
            assertEquals(EspionageConfig.DEFAULT_WEIGHTS.keySet(), values.keySet());
            assertTrue(values.values().stream().allMatch(value -> value == 12));
            verify(f.plugins).isPluginEnabled("MMOCore");
            }
        }
    }

    @Test void absentRoleplayLeavesFounderPendingAndDoesNotRevokeLoadedHolder() {
        var faction = mock(Faction.class);
        var state = new EspionageState();
        when(faction.getEspionage()).thenReturn(state);
        when(faction.getLeader()).thenReturn("Founder");
        when(faction.isLeader("Founder")).thenReturn(true);
        when(faction.getMembers()).thenReturn(List.of("Founder"));
        try (var f = new IdentityFixture("Founder")) {
            f.registered = false;
            EspionageService.initializeFounder(faction);
            assertTrue(state.hasPendingFounder());
            assertNull(state.getSpymaster());
            assertEquals(0, state.appointmentCount(SpecialPosition.SPYMASTER));
            var holder = new SpecialPositionAssignment();
            holder.playerId = f.owner; holder.characterId = "founder"; holder.playerName = "Founder";
            state.assignFounder(SpecialPosition.SPYMASTER, holder, 80);
            assertSame(holder, EspionageService.spymaster(faction));
            assertTrue(EspionageService.stabilityModifiers(faction, 0).isEmpty());
        }
    }

    @Test void providerArrivalPendingAndSwitchBackRequireFreshReadyToken() {
        try (var f = new IdentityFixture()) {
            assertNull(OfficeCharacters.currentSession(null));
            assertFalse(OfficeCharacters.isCurrent(f.player, null));
            f.registered = false;
            assertNull(OfficeCharacters.currentSession(f.player));
            f.registered = true;
            assertNull(OfficeCharacters.currentSession(f.player));
            var original = f.activate(UUID.randomUUID());
            assertEquals(original, OfficeCharacters.currentSession(f.player));
            assertFalse(OfficeCharacters.isCurrent(null, original));
            f.activate(UUID.randomUUID());
            assertFalse(OfficeCharacters.isCurrent(f.player, original));
            var returned = f.activate(original.ref().characterId());
            assertNotEquals(original, returned);
            assertFalse(OfficeCharacters.isCurrent(f.player, original));
            assertEquals(returned, OfficeCharacters.currentSession(f.player));
            f.registered = false;
            assertNull(OfficeCharacters.currentSession(f.player));
            assertThrows(OfficeCharacters.StaleCharacterSession.class, () -> OfficeCharacters.attributes(f.player, returned));
        }
    }

    @ParameterizedTest
    @ValueSource(strings={"token-owner", "unknown", "info-owner", "info-character", "dead", "missing", "stale", "offline", "reconnect"})
    void untrustedOrDisconnectedSessionNeverBecomesActive(String invalid) {
        try (var f = new IdentityFixture()) {
            var session = f.activate(UUID.randomUUID());
            UUID id = session.ref().characterId();
            switch (invalid) {
                case "token-owner" -> f.token = new CharacterSession(new CharacterRef(UUID.randomUUID(), id), 9);
                case "unknown" -> f.infos.clear();
                case "info-owner" -> f.infos.put(id, info(new CharacterRef(UUID.randomUUID(), id), CharacterStatus.ALIVE));
                case "info-character" -> f.infos.put(id, info(new CharacterRef(f.owner, UUID.randomUUID()), CharacterStatus.ALIVE));
                case "dead" -> f.infos.put(id, info(session.ref(), CharacterStatus.DEAD));
                case "missing" -> f.infos.put(id, info(session.ref(), CharacterStatus.MISSING));
                case "stale" -> when(f.provider.isCurrent(session)).thenReturn(false);
                case "offline" -> when(f.player.isOnline()).thenReturn(false);
                case "reconnect" -> f.connection = mock(Player.class);
                default -> throw new AssertionError(invalid);
            }
            assertNull(OfficeCharacters.currentSession(f.player));
            assertNull(OfficeCharacters.activeCharacterId(f.player));
            assertThrows(OfficeCharacters.StaleCharacterSession.class, () -> OfficeCharacters.attributes(f.player));
        }
    }

    @ParameterizedTest
    @ValueSource(strings={"data", "character", "owner", "id", "malformed", "shorthand", "plugin"})
    void roleplayModelMustBelongToTheCapturedCharacter(String missing) {
        try (var f = new IdentityFixture()) {
            var session = f.activate(UUID.fromString("00000001-0001-0001-0001-000000000001"));
            try (var rpc = new RoleplayFixture(f)) {
                switch (missing) {
                    case "data" -> rpc.players.when(() -> net.tfminecraft.rpcharacters.managers.PlayerManager.get(f.player)).thenReturn(null);
                    case "character" -> when(rpc.data.getActiveCharacter()).thenReturn(null);
                    case "owner" -> when(rpc.character.getOwner()).thenReturn(mock(Player.class));
                    case "id" -> when(rpc.character.getId()).thenReturn(UUID.randomUUID().toString());
                    case "malformed" -> when(rpc.character.getId()).thenReturn("legacy-character");
                    case "shorthand" -> when(rpc.character.getId()).thenReturn("1-1-1-1-1");
                    case "plugin" -> when(f.plugins.isPluginEnabled("RPCharacters")).thenReturn(false);
                    default -> throw new AssertionError(missing);
                }
                assertThrows(OfficeCharacters.StaleCharacterSession.class, () -> OfficeCharacters.attributes(f.player, session));
                verify(rpc.attributes, never()).getAmount(any(AttributeModifier.class));
            }
        }
    }

    @Test void roleplayAttributeCallbackCannotReturnValuesAfterSwitchBack() {
        try (var f = new IdentityFixture()) {
            var session = f.activate(UUID.randomUUID());
            try (var rpc = new RoleplayFixture(f)) {
                when(rpc.attributes.getAmount(any(AttributeModifier.class))).thenAnswer(call -> {
                    f.activate(UUID.randomUUID());
                    f.activate(session.ref().characterId());
                    return 12;
                });
                assertThrows(OfficeCharacters.StaleCharacterSession.class, () -> OfficeCharacters.attributes(f.player, session));
                verify(f.plugins, never()).isPluginEnabled("MMOCore");
                assertFalse(OfficeCharacters.isCurrent(f.player, session));
            }
        }
    }

    @Test void mmocoreOverlayIsRejectedIfItsAttributeCallbackEndsTheSession() {
        try (var f = new IdentityFixture()) {
            var session = f.activate(UUID.randomUUID());
            try (var rpc = new RoleplayFixture(f);
                 var mmocore = mockStatic(net.Indyuce.mmocore.api.player.PlayerData.class)) {
                when(f.plugins.isPluginEnabled("MMOCore")).thenReturn(true);
                var data = mock(net.Indyuce.mmocore.api.player.PlayerData.class);
                var attributes = mock(PlayerAttributes.class);
                var attribute = mock(PlayerAttributes.AttributeInstance.class);
                when(data.getAttributes()).thenReturn(attributes);
                when(attributes.getInstance(anyString())).thenReturn(attribute);
                when(attribute.getBase()).thenAnswer(call -> { f.token = null; return 15; });
                mmocore.when(() -> net.Indyuce.mmocore.api.player.PlayerData.get(f.player)).thenReturn(data);
                assertThrows(OfficeCharacters.StaleCharacterSession.class, () -> OfficeCharacters.attributes(f.player, session));
                verify(attribute, never()).getTotal();
            }
        }
    }

    @Test void onlyExactOwnerAndCanonicalDeadIdentityRevokesAnOffice() {
        try (var f = new IdentityFixture()) {
            UUID id = UUID.fromString("abcdefab-cdef-abcd-efab-cdefabcdefab");
            var ref = new CharacterRef(f.owner, id);
            assertFalse(OfficeCharacters.isDead(null, id.toString()));
            assertFalse(OfficeCharacters.isDead(f.owner, null));
            assertFalse(OfficeCharacters.isDead(f.owner, "bad"));
            assertFalse(OfficeCharacters.isDead(f.owner, "1-1-1-1-1"));
            assertFalse(OfficeCharacters.isDead(f.owner, id.toString()));
            for (var status : CharacterStatus.values()) {
                f.infos.put(id, info(ref, status));
                assertEquals(status == CharacterStatus.DEAD, OfficeCharacters.isDead(f.owner, id.toString().toUpperCase(Locale.ROOT)));
            }
            f.infos.put(id, info(new CharacterRef(UUID.randomUUID(), id), CharacterStatus.DEAD));
            assertFalse(OfficeCharacters.isDead(f.owner, id.toString()));
            f.infos.put(id, info(new CharacterRef(f.owner, UUID.randomUUID()), CharacterStatus.DEAD));
            assertFalse(OfficeCharacters.isDead(f.owner, id.toString()));
            f.infos.put(id, info(ref, CharacterStatus.DEAD));
            f.registered = false;
            assertFalse(OfficeCharacters.isDead(f.owner, id.toString()));
        }
    }

    static CharacterInfo info(CharacterRef ref, CharacterStatus status) {
        return new CharacterInfo(ref, "Canonical Secret", status, null, Set.of(), 0, null);
    }

    /** Uses the real Characters facade while controlling registry and live session boundaries. */
    static final class IdentityFixture implements AutoCloseable {
        final MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
        final PluginManager plugins = mock(PluginManager.class);
        final ServicesManager services = mock(ServicesManager.class);
        final CharacterIdentity provider = mock(CharacterIdentity.class);
        final Player player = mock(Player.class);
        final UUID owner = UUID.randomUUID();
        final Map<UUID, CharacterInfo> infos = new HashMap<>();
        final Map<UUID, CharacterInfo> lastPlayed = new HashMap<>();
        final String account;
        Player connection = player;
        CharacterSession token;
        boolean registered = true;
        String alias = "Masked Stranger";
        long generation;

        IdentityFixture() { this("Account"); }
        IdentityFixture(String account) {
            this.account = account;
            when(player.getName()).thenReturn(account);
            when(player.getUniqueId()).thenReturn(owner);
            when(player.isOnline()).thenReturn(true);
            when(services.load(CharacterIdentity.class)).thenAnswer(call -> registered ? provider : null);
            when(provider.currentSession(any())).thenAnswer(call -> Optional.ofNullable(token));
            when(provider.isCurrent(any())).thenAnswer(call -> token != null && token.equals(call.getArgument(0)));
            when(provider.info(any())).thenAnswer(call -> Optional.ofNullable(infos.get(call.getArgument(0))));
            when(provider.lastPlayed(any())).thenAnswer(call -> Optional.ofNullable(lastPlayed.get(call.getArgument(0))));
            when(provider.displayName(any())).thenAnswer(call -> alias);
            Server server = mock(Server.class);
            bukkit.when(Bukkit::getServer).thenReturn(server);
            bukkit.when(Bukkit::getPluginManager).thenReturn(plugins);
            bukkit.when(Bukkit::getServicesManager).thenReturn(services);
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            bukkit.when(() -> Bukkit.getPlayer(owner)).thenAnswer(call -> connection);
            bukkit.when(() -> Bukkit.getPlayerExact(account)).thenAnswer(call -> connection);
        }
        CharacterSession activate(UUID characterId) {
            token = new CharacterSession(new CharacterRef(owner, characterId), ++generation);
            infos.put(characterId, info(token.ref(), CharacterStatus.ALIVE));
            return token;
        }
        @Override public void close() { bukkit.close(); }
    }

    private static final class RoleplayFixture implements AutoCloseable {
        final MockedStatic<net.tfminecraft.rpcharacters.managers.PlayerManager> players = mockStatic(net.tfminecraft.rpcharacters.managers.PlayerManager.class);
        final net.tfminecraft.rpcharacters.objects.PlayerData data = mock(net.tfminecraft.rpcharacters.objects.PlayerData.class);
        final net.tfminecraft.rpcharacters.objects.RPCharacter character = mock(net.tfminecraft.rpcharacters.objects.RPCharacter.class);
        final net.tfminecraft.rpcharacters.objects.attributes.AttributeData attributes = mock(net.tfminecraft.rpcharacters.objects.attributes.AttributeData.class);
        RoleplayFixture(IdentityFixture f) {
            when(f.plugins.isPluginEnabled("RPCharacters")).thenReturn(true);
            when(data.getActiveCharacter()).thenReturn(character);
            when(character.getId()).thenReturn(f.token.ref().characterId().toString());
            when(character.getOwner()).thenReturn(f.player);
            when(character.getAttributeData()).thenReturn(attributes);
            when(attributes.getAmount(any(AttributeModifier.class))).thenReturn(12);
            players.when(() -> net.tfminecraft.rpcharacters.managers.PlayerManager.get(f.player)).thenReturn(data);
        }
        @Override public void close() { players.close(); }
    }

    @Test void officeServiceLoadsAndReflectsWithoutOptionalPluginClasses() throws Exception {
        var classes = EspionageService.class.getProtectionDomain().getCodeSource().getLocation();
        try (var loader = new URLClassLoader(new java.net.URL[]{classes}, getClass().getClassLoader()) {
            @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.startsWith("net.tfminecraft.rpcharacters.") || name.startsWith("net.Indyuce.mmocore."))
                    throw new ClassNotFoundException("Optional plugin absent: " + name);
                if (name.startsWith("net.tfminecraft.simplefactions.espionage.")) {
                    Class<?> loaded = findLoadedClass(name);
                    if (loaded == null) loaded = findClass(name);
                    if (resolve) resolveClass(loaded);
                    return loaded;
                }
                return super.loadClass(name, resolve);
            }
        }) {
            var service = Class.forName(EspionageService.class.getName(), true, loader);
            assertDoesNotThrow(service::getDeclaredMethods);
            var bridge = Class.forName(OfficeCharacters.class.getName(), true, loader);
            var active = bridge.getDeclaredMethod("activeCharacterId", Player.class);
            active.setAccessible(true);
            assertNull(active.invoke(null, new Object[]{null}));
        }
    }
}
