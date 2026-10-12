package net.tfminecraft.simplefactions.vehicles.maintenance;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import net.tfminecraft.denareconomy.api.Denars;
import net.tfminecraft.simplefactions.identity.Members;
import net.tfminecraft.tlibs.character.CharacterRef;
import org.junit.jupiter.api.Test;

class CharacterVehicleBankTest {
    @Test void readsCapturedCharacterBalancesAndNeverUsesItsOwnerAsAnAccount() {
        var character = new CharacterRef(UUID.randomUUID(), UUID.randomUUID());
        var other = new CharacterRef(character.ownerId(), UUID.randomUUID());
        try (var members = mockStatic(Members.class, CALLS_REAL_METHODS); var denars = mockStatic(Denars.class, CALLS_REAL_METHODS)) {
            members.when(() -> Members.ref(character.characterId().toString())).thenReturn(Optional.of(character));
            members.when(() -> Members.ref(other.characterId().toString())).thenReturn(Optional.of(other));
            members.when(() -> Members.ofAccountName("Account")).thenReturn(other.characterId().toString());
            denars.when(() -> Denars.balance(Denars.bank(character))).thenReturn(1234L);
            denars.when(() -> Denars.balance(Denars.pouch(character))).thenReturn(567L);
            denars.when(() -> Denars.balance(Denars.bank(other))).thenReturn(9900L);
            var bank = DenarEconomyPlayerBank.INSTANCE;
            assertEquals(12.34, bank.getBankBalance(character.characterId()));
            assertEquals(5.67, bank.getPouchBalance(character.characterId()));
            assertEquals(99, bank.getBankBalance(other.characterId()));
            assertEquals(character.characterId(), bank.resolve(character.characterId().toString()));
            assertEquals(other.characterId(), bank.resolve("Account"));
            assertEquals(0, bank.getBankBalance(character.ownerId()));
            assertEquals(0, bank.getPouchBalance(null));
            assertNull(bank.resolve("missing"));
        }
    }
}
