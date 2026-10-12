package net.tfminecraft.simplefactions.vehicles.registry;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.util.HashMap;
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
import org.mockito.MockedStatic;

import net.tfminecraft.denareconomy.accounts.OfflineModifier;
import net.tfminecraft.simplefactions.identity.Members;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.vehicles.berth.InstallationVehicleOwnerSync;
import net.tfminecraft.tlibs.character.CharacterIdentity;
import net.tfminecraft.tlibs.character.CharacterInfo;
import net.tfminecraft.tlibs.character.CharacterRef;
import net.tfminecraft.tlibs.character.CharacterSession;
import net.tfminecraft.tlibs.character.CharacterStatus;
import net.tfminecraft.vehicleframework.data.OwnerData;

/** VehicleFramework still names accounts; these bridge its owner tags and member keys. */
class VehicleOwnerKeysTest {
    private final UUID account = UUID.randomUUID();
    private final UUID characterId = UUID.randomUUID();
    private final Map<UUID, CharacterRef> heirs = new HashMap<>();
    private final CharacterIdentity provider = mock(CharacterIdentity.class);
    private CharacterIdentity registered = provider;
    private MockedStatic<Bukkit> bukkit;
    private MockedStatic<OfflineModifier> accounts;

    @BeforeEach
    void setUp() {
        bukkit = mockStatic(Bukkit.class);
        accounts = mockStatic(OfflineModifier.class);
        bukkit.when(Bukkit::getServer).thenReturn(mock(Server.class));
        bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
        ServicesManager services = mock(ServicesManager.class);
        bukkit.when(Bukkit::getServicesManager).thenReturn(services);
        when(services.load(CharacterIdentity.class)).thenAnswer(call -> registered);
        when(provider.legacyHeir(any())).thenAnswer(call -> Optional.ofNullable(heirs.get(call.getArgument(0))));
        when(provider.info(characterId)).thenReturn(Optional.of(info(account, characterId)));
        accounts.when(() -> OfflineModifier.playerId("Owner")).thenReturn(account);
        Members.useSharedIdentity();
    }

    @AfterEach
    void close() {
        accounts.close();
        bukkit.close();
    }

    @Test
    void ownerKeyIsThePlayedCharacterElseTheLegacyHeirOfTheTaggedAccount() {
        assertNull(VehicleOwnershipQueries.ownerKey(null));
        assertNull(VehicleOwnershipQueries.ownerKey(" "));
        assertNull(VehicleOwnershipQueries.ownerKey("Owner"), "No reviewed heir yet");
        heirs.put(account, new CharacterRef(UUID.randomUUID(), characterId));
        assertNull(VehicleOwnershipQueries.ownerKey("Owner"), "A foreign heir never names the owner");
        heirs.put(account, new CharacterRef(account, characterId));
        assertEquals(Members.key(characterId), VehicleOwnershipQueries.ownerKey("Owner"));
        assertNull(VehicleOwnershipQueries.ownerKey("Unknown"), "An account DenarEconomy never saw");

        Player online = mock(Player.class);
        when(online.getUniqueId()).thenReturn(account);
        when(provider.currentSession(online)).thenReturn(Optional.of(new CharacterSession(new CharacterRef(account, characterId), 1)));
        when(provider.isCurrent(any())).thenReturn(true);
        bukkit.when(() -> Bukkit.getPlayerExact("Owner")).thenReturn(online);
        assertEquals(Members.key(characterId), VehicleOwnershipQueries.ownerKey("Owner"));
        assertEquals(characterId, VehicleOwnershipQueries.characterId(online));
        Player stranger = mock(Player.class);
        UUID strangerAccount = UUID.randomUUID();
        when(stranger.getUniqueId()).thenReturn(strangerAccount);
        assertNull(VehicleOwnershipQueries.characterId(stranger), "No character means no payer");
    }

    @Test
    void withoutAServerOrProviderNoCharacterIsKnown() {
        heirs.put(account, new CharacterRef(account, characterId));
        registered = null;
        assertNull(VehicleOwnershipQueries.ownerKey("Owner"));
        registered = provider;
        bukkit.when(Bukkit::isPrimaryThread).thenReturn(false);
        assertNull(VehicleOwnershipQueries.ownerKey("Owner"));
        bukkit.when(Bukkit::getServer).thenReturn(null);
        assertNull(VehicleOwnershipQueries.ownerKey("Owner"));
    }

    @Test
    void leaderOwnedFactionVehiclesNameTheLeadersAccountAndWaitWhileItIsUnknown() {
        OfflinePlayer owner = mock(OfflinePlayer.class);
        when(owner.getName()).thenReturn("Owner");
        bukkit.when(() -> Bukkit.getOfflinePlayer(account)).thenReturn(owner);
        assertNull(VehicleOwnershipQueries.accountNameOf(null));
        assertNull(VehicleOwnershipQueries.accountNameOf(""));
        assertEquals("Legacy", VehicleOwnershipQueries.accountNameOf("Legacy"));
        assertEquals("Owner", VehicleOwnershipQueries.accountNameOf(Members.key(characterId)));

        Faction faction = mock(Faction.class);
        when(faction.getLeader()).thenReturn(Members.key(characterId));
        assertEquals("player_Owner", InstallationVehicleOwnerSync.expectedOwner(faction));

        registered = null;
        assertNull(InstallationVehicleOwnerSync.expectedOwner(faction));
        OwnerData data = new OwnerData();
        data.setOwner("player_Previous");
        InstallationVehicleOwnerSync sync = new InstallationVehicleOwnerSync(new PlayerVehicleRegistry());
        sync.applyLeaderOwner(data, faction);
        assertEquals("player_Previous", data.getOwner(), "An unreadable leader leaves the owner alone");
        PlayerVehicleRegistry registry = new PlayerVehicleRegistry();
        registry.register(new PlayerVehicleRecord(characterId, "pooled", "cart", OwnershipMode.POOL, null, "home"));
        var vehicle = mock(net.tfminecraft.vehicleframework.vehicles.ActiveVehicle.class);
        when(vehicle.getUUID()).thenReturn("pooled");
        when(vehicle.getOwnerData()).thenReturn(data);
        try (var factions = mockStatic(net.tfminecraft.simplefactions.managers.FactionManager.class)) {
            factions.when(() -> net.tfminecraft.simplefactions.managers.FactionManager.getByString("home")).thenReturn(faction);
            new InstallationVehicleOwnerSync(registry).syncIfBerthed(vehicle);
        }
        assertEquals("player_Previous", data.getOwner());
        bukkit.verify(() -> Bukkit.getOfflinePlayer(anyString()), never());
    }

    private static CharacterInfo info(UUID owner, UUID id) {
        return new CharacterInfo(new CharacterRef(owner, id), "Owner's Character", CharacterStatus.ALIVE,
                null, Set.of(), 0, null);
    }
}
