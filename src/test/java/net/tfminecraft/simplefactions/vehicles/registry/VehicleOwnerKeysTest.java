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
import net.tfminecraft.simplefactions.vehicles.owners.VehicleOwnerMigration;
import net.tfminecraft.tlibs.character.CharacterIdentity;
import net.tfminecraft.tlibs.character.CharacterInfo;
import net.tfminecraft.tlibs.character.CharacterRef;
import net.tfminecraft.tlibs.character.CharacterSession;
import net.tfminecraft.tlibs.character.CharacterStatus;

/** The live lookups the vehicle owner migration seeds legacy owner names with. */
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

    private final VehicleOwnerMigration.LiveIdentity identity = new VehicleOwnerMigration.LiveIdentity();

    @Test
    void aLegacyOwnerNameCountsOnlyWhileItIsStillThatAccountsName() {
        OfflinePlayer current = mock(OfflinePlayer.class);
        when(current.getName()).thenReturn("owner");
        bukkit.when(() -> Bukkit.getOfflinePlayer(account)).thenReturn(current);
        assertEquals(account, identity.roundTrip("Owner"));

        when(current.getName()).thenReturn("Renamed");
        assertNull(identity.roundTrip("Owner"), "The name now belongs to someone else, or to nobody");
        when(current.getName()).thenReturn(null);
        assertNull(identity.roundTrip("Owner"));
        assertNull(identity.roundTrip("Unknown"), "An account DenarEconomy never saw");
    }

    @Test
    void charactersResolveToTheirAccountReviewedHeirAndPlainName() {
        assertEquals(Optional.of(account), identity.accountOf(characterId));
        assertEquals(Optional.empty(), identity.accountOf(UUID.randomUUID()));
        assertEquals(Optional.empty(), identity.heir(account));
        heirs.put(account, new CharacterRef(account, characterId));
        assertEquals(Optional.of(new CharacterRef(account, characterId)), identity.heir(account));
        assertEquals("Owner's Character", identity.nameOf(characterId));
        assertNull(identity.nameOf(UUID.randomUUID()));
        UUID coloured = UUID.randomUUID();
        when(provider.info(coloured)).thenReturn(Optional.of(new CharacterInfo(new CharacterRef(account, coloured),
                "\u00a7a  ", CharacterStatus.ALIVE, null, Set.of(), 0, null)));
        assertNull(identity.nameOf(coloured), "A name that is only colour codes is no name");

        Player online = mock(Player.class);
        when(online.getUniqueId()).thenReturn(account);
        when(provider.currentSession(online)).thenReturn(Optional.of(new CharacterSession(new CharacterRef(account, characterId), 1)));
        when(provider.isCurrent(any())).thenReturn(true);
        assertEquals(characterId, VehicleOwnershipQueries.characterId(online));
        Player stranger = mock(Player.class);
        when(stranger.getUniqueId()).thenReturn(UUID.randomUUID());
        assertNull(VehicleOwnershipQueries.characterId(stranger), "No character means no payer");
    }

    private static CharacterInfo info(UUID owner, UUID id) {
        return new CharacterInfo(new CharacterRef(owner, id), "Owner's Character", CharacterStatus.ALIVE,
                null, Set.of(), 0, null);
    }

    @Test
    void anExpiredUserCacheFallsBackToTheNameDenarEconomyRememberedLast(@org.junit.jupiter.api.io.TempDir java.nio.file.Path folder)
            throws Exception {
        OfflinePlayer forgotten = mock(OfflinePlayer.class);
        bukkit.when(() -> Bukkit.getOfflinePlayer(account)).thenReturn(forgotten);
        java.nio.file.Path index = folder.resolve("player-names.json");
        UUID other = UUID.randomUUID();
        java.nio.file.Files.writeString(index, "{\"oldname\":\"" + account + "\",\"owner\":\"" + account
                + "\",\"someone\":\"" + other + "\",\"broken\":\"not-a-uuid\",\"nothing\":null,"
                + "\"object\":{}}");
        accounts.when(() -> OfflineModifier.playerId("OldName")).thenReturn(account);

        var remembered = new VehicleOwnerMigration.LiveIdentity(index);
        assertEquals(account, remembered.roundTrip("Owner"), "The account's newest remembered name");
        assertNull(remembered.roundTrip("OldName"), "A name the account has since replaced");
        assertEquals("someone", remembered.latestRemembered().get(other));

        java.nio.file.Files.writeString(index, "{\"owner\":\"" + account + "\",\"renamed\":\"" + account + "\"}");
        assertNull(new VehicleOwnerMigration.LiveIdentity(index).roundTrip("Owner"), "The owner renamed since");
        java.nio.file.Files.writeString(index, "[\"not an object\"]");
        assertNull(new VehicleOwnerMigration.LiveIdentity(index).roundTrip("Owner"));
        assertNull(new VehicleOwnerMigration.LiveIdentity(folder.resolve("absent.json")).roundTrip("Owner"));

        when(forgotten.getName()).thenReturn("Owner");
        assertEquals(account, new VehicleOwnerMigration.LiveIdentity(folder.resolve("absent.json")).roundTrip("Owner"),
                "A name the server still knows needs no index");
    }
}
